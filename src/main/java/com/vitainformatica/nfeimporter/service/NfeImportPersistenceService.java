package com.vitainformatica.nfeimporter.service;

import java.time.Instant;

import com.vitainformatica.nfeimporter.config.NfeConfig;
import com.vitainformatica.nfeimporter.domain.ControleDistribuicao;
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

    @Transactional
    public ControleDistribuicao obterControle() {
        return ControleDistribuicao.obterOuCriar(config.cnpj(), config.ambiente());
    }

    /** Persiste os documentos de uma pagina de resposta e atualiza o cursor de NSU. Retorna quantas notas eram novas. */
    @Transactional
    public int persistirPagina(Long controleId, DistDfeResponse resposta) {
        ControleDistribuicao controle = ControleDistribuicao.findById(controleId);

        int novas = 0;
        if (resposta.sucessoComDocumentos()) {
            for (DistDfeDocumento documento : resposta.documentos()) {
                if (persistirDocumentoSeNovo(documento)) {
                    novas++;
                }
            }
        }

        controle.ultimoNsuProcessado = resposta.ultNSU();
        controle.ultimaConsultaEm = Instant.now();
        controle.ultimoCstat = resposta.cStat();
        controle.ultimoXmotivo = resposta.xMotivo();

        return novas;
    }

    private boolean persistirDocumentoSeNovo(DistDfeDocumento documento) {
        if (NotaFiscal.existePorNsu(documento.nsu())) {
            // Idempotencia: NSU ja importado em execucao anterior (ex: reprocessamento apos falha parcial).
            return false;
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
        return true;
    }
}
