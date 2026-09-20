package com.vitainformatica.nfeimporter.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.vitainformatica.nfeimporter.config.NfeConfig;
import com.vitainformatica.nfeimporter.domain.ControleDistribuicao;
import com.vitainformatica.nfeimporter.domain.ManifestacaoDestinatario;
import com.vitainformatica.nfeimporter.domain.NotaFiscal;
import com.vitainformatica.nfeimporter.domain.TipoDocumento;
import com.vitainformatica.nfeimporter.nfe.DadosDocumentoFiscal;
import com.vitainformatica.nfeimporter.nfe.NfeXmlExtractor;
import com.vitainformatica.nfeimporter.sefaz.DistDfeDocumento;
import com.vitainformatica.nfeimporter.sefaz.DistDfeResponse;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Operacoes de banco de dados do fluxo de importacao, isoladas em transacoes curtas por pagina
 * (uma pagina = uma chamada ao webservice) para que o progresso ja obtido nao se perca caso uma
 * pagina posterior falhe no meio de uma importacao grande.
 */
@ApplicationScoped
public class NfeImportPersistenceService {

    @Inject
    NfeConfig config;

    /**
     * @param notasNovas             total de documentos novos persistidos nesta pagina (resumos, NF-e
     *                                completas e eventos)
     * @param idsElegiveisMercadinho ids das notas novas que sao NF-e completas (unico tipo que carrega
     *                                os itens/valores necessarios para o mercadinho) - candidatas a
     *                                encaminhamento automatico
     * @param idsManifestacaoPendente ids (em manifestacao_destinatario) de chaves com resumo novo
     *                                ainda sem manifestacao registrada - candidatas a "Ciencia da
     *                                Operacao" automatica
     */
    public record ResultadoPagina(int notasNovas, List<Long> idsElegiveisMercadinho, List<Long> idsManifestacaoPendente) {
    }

    @Transactional
    public ControleDistribuicao obterControle() {
        return ControleDistribuicao.obterOuCriar(config.cnpj(), config.ambiente());
    }

    /** Persiste os documentos de uma pagina de resposta e atualiza o cursor de NSU. */
    @Transactional
    public ResultadoPagina persistirPagina(Long controleId, DistDfeResponse resposta) {
        ControleDistribuicao controle = ControleDistribuicao.findById(controleId);

        int novas = 0;
        List<Long> idsElegiveisMercadinho = new ArrayList<>();
        List<Long> idsManifestacaoPendente = new ArrayList<>();
        if (resposta.sucessoComDocumentos()) {
            for (DistDfeDocumento documento : resposta.documentos()) {
                ResultadoDocumento resultado = persistirDocumentoSeNovo(documento);
                if (resultado.persistido()) {
                    novas++;
                    if (resultado.elegivelMercadinho()) {
                        idsElegiveisMercadinho.add(resultado.idNotaFiscal());
                    }
                    if (resultado.idManifestacaoPendente() != null) {
                        idsManifestacaoPendente.add(resultado.idManifestacaoPendente());
                    }
                }
            }
        }

        controle.ultimoNsuProcessado = resposta.ultNSU();
        controle.ultimaConsultaEm = Instant.now();
        controle.ultimoCstat = resposta.cStat();
        controle.ultimoXmotivo = resposta.xMotivo();

        return new ResultadoPagina(novas, idsElegiveisMercadinho, idsManifestacaoPendente);
    }

    private record ResultadoDocumento(boolean persistido, Long idNotaFiscal, boolean elegivelMercadinho, Long idManifestacaoPendente) {
        static final ResultadoDocumento JA_EXISTIA = new ResultadoDocumento(false, null, false, null);
    }

    private ResultadoDocumento persistirDocumentoSeNovo(DistDfeDocumento documento) {
        if (NotaFiscal.existePorNsu(documento.nsu())) {
            // Idempotencia: NSU ja importado em execucao anterior (ex: reprocessamento apos falha parcial).
            return ResultadoDocumento.JA_EXISTIA;
        }

        TipoDocumento tipo = TipoDocumento.porSchema(documento.schema());
        DadosDocumentoFiscal dados;
        try {
            dados = NfeXmlExtractor.extrair(documento.xml(), tipo);
        } catch (Exception e) {
            Log.errorf(e, "Falha ao interpretar XML do NSU %s (schema %s); documento sera salvo com dados vazios",
                    documento.nsu(), documento.schema());
            dados = new DadosDocumentoFiscal(null, null, null, null, null, null,
                    "Erro ao interpretar XML: " + e.getMessage());
        }

        NotaFiscal nota = new NotaFiscal();
        nota.nsu = documento.nsu();
        nota.schemaOrigem = documento.schema();
        nota.tipoDocumento = tipo;
        nota.chaveAcesso = dados.chaveAcesso();
        nota.cnpjEmitente = dados.cnpjEmitente();
        nota.nomeEmitente = dados.nomeEmitente();
        nota.cnpjDestinatario = config.cnpj();
        nota.dataEmissao = dados.dataEmissao();
        nota.valorTotal = dados.valorTotal();
        nota.numeroProtocolo = dados.numeroProtocolo();
        nota.situacao = dados.situacao();
        nota.ambiente = config.ambiente();
        nota.xml = documento.xml();
        nota.dataImportacao = Instant.now();
        nota.persist();

        Long idManifestacaoPendente = null;
        if (tipo == TipoDocumento.RESUMO_NFE && nota.chaveAcesso != null && !nota.chaveAcesso.isBlank()) {
            // Resumo novo: registra (ou recupera) o controle de manifestacao dessa chave. Se ja foi
            // enviada antes (ex: chave repetida em outro NSU), nao ha nada a fazer de novo aqui.
            ManifestacaoDestinatario manifestacao = ManifestacaoDestinatario.obterOuCriar(nota.chaveAcesso, config.cnpj());
            if (!manifestacao.enviada) {
                idManifestacaoPendente = manifestacao.id;
            }
        }

        // Resumos (resNFe) e eventos nao carregam itens/valores da nota - nao ha o que encaminhar
        // ao mercadinho ainda. Apenas a NF-e completa (procNFe) tem o XML que o mercadinho espera.
        return new ResultadoDocumento(true, nota.id, tipo == TipoDocumento.NFE_COMPLETA, idManifestacaoPendente);
    }
}
