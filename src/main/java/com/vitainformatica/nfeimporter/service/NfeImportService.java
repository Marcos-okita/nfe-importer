package com.vitainformatica.nfeimporter.service;

import java.time.Duration;
import java.time.Instant;

import com.vitainformatica.nfeimporter.config.MercadinhoConfig;
import com.vitainformatica.nfeimporter.config.NfeConfig;
import com.vitainformatica.nfeimporter.domain.ControleDistribuicao;
import com.vitainformatica.nfeimporter.sefaz.DistDfeResponse;
import com.vitainformatica.nfeimporter.sefaz.NfeDistribuicaoClient;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Orquestra a importacao continua de documentos fiscais via Distribuicao de DFe: aplica o
 * autothrottle recomendado pela SEFAZ, pagina pelos NSUs pendentes e delega a persistencia
 * (em transacoes curtas, por pagina) ao {@link NfeImportPersistenceService}.
 * <p>
 * Se {@code mercadinho.enviar-apos-importar} estiver habilitado (padrao), cada NF-e completa nova
 * e encaminhada ao mercadinho logo apos a pagina ser persistida - fora da transacao de banco, para
 * nao segurar uma conexao aberta durante a chamada HTTP. Falha no envio nunca interrompe a
 * importacao em si.
 */
@ApplicationScoped
public class NfeImportService {

    @Inject
    NfeDistribuicaoClient client;

    @Inject
    NfeImportPersistenceService persistenceService;

    @Inject
    MercadinhoEnvioService mercadinhoEnvioService;

    @Inject
    NfeConfig config;

    @Inject
    MercadinhoConfig mercadinhoConfig;

    /**
     * @param forcar ignora o intervalo minimo entre consultas (uso administrativo/depuracao apenas -
     *               chamadas excessivas podem levar a SEFAZ a bloquear temporariamente o CNPJ, cStat 656).
     */
    public ImportResultado importar(boolean forcar) {
        ControleDistribuicao controle = persistenceService.obterControle();

        if (!forcar && controle.ultimaConsultaEm != null) {
            long minimoMinutos = config.import_().intervaloMinimoMinutos();
            long minutosDesdeUltima = Duration.between(controle.ultimaConsultaEm, Instant.now()).toMinutes();
            if (minutosDesdeUltima < minimoMinutos) {
                return ImportResultado.pulado(minimoMinutos - minutosDesdeUltima);
            }
        }

        int totalNovas = 0;
        int paginas = 0;
        int maxPaginas = config.import_().maxPaginasPorExecucao();
        String nsuAtual = controle.ultimoNsuProcessado;
        DistDfeResponse ultimaResposta = null;

        while (paginas < maxPaginas) {
            paginas++;
            Log.debugf("Consultando distribuicao de DFe a partir do NSU %s (pagina %d)", nsuAtual, paginas);

            DistDfeResponse resposta = client.consultarDistribuicao(nsuAtual);
            ultimaResposta = resposta;

            if (resposta.consumoIndevido()) {
                Log.warnf("SEFAZ sinalizou consumo indevido (cStat 656): %s. Importacao interrompida; "
                        + "aguarde antes de tentar novamente.", resposta.xMotivo());
                persistenceService.persistirPagina(controle.id, resposta);
                break;
            }

            if (!resposta.semNovosDocumentos() && !resposta.sucessoComDocumentos()) {
                Log.warnf("SEFAZ retornou cStat=%d xMotivo=%s ao consultar distribuicao de DFe",
                        resposta.cStat(), resposta.xMotivo());
            }

            NfeImportPersistenceService.ResultadoPagina resultadoPagina = persistenceService.persistirPagina(controle.id, resposta);
            totalNovas += resultadoPagina.notasNovas();
            encaminharAoMercadinhoSeHabilitado(resultadoPagina.idsElegiveisMercadinho());

            nsuAtual = resposta.ultNSU();

            if (!resposta.sucessoComDocumentos() || !resposta.possuiMaisPaginas()) {
                break;
            }
        }

        Log.infof("Importacao concluida: %d nota(s) nova(s) em %d pagina(s). ultimo cStat=%s ultNSU=%s",
                totalNovas, paginas, ultimaResposta != null ? ultimaResposta.cStat() : null, nsuAtual);

        return ImportResultado.concluido(totalNovas, paginas,
                ultimaResposta != null ? ultimaResposta.cStat() : null,
                ultimaResposta != null ? ultimaResposta.xMotivo() : null,
                nsuAtual);
    }

    private void encaminharAoMercadinhoSeHabilitado(java.util.List<Long> idsNfeCompleta) {
        if (idsNfeCompleta.isEmpty() || !mercadinhoConfig.enviarAposImportar()) {
            return;
        }
        if (!mercadinhoConfig.configurado()) {
            Log.warn("mercadinho.enviar-apos-importar esta habilitado, mas mercadinho.api/mercadinho.token "
                    + "nao estao configurados - pulando o encaminhamento automatico desta pagina.");
            return;
        }
        for (Long notaFiscalId : idsNfeCompleta) {
            // Best-effort: cada falha ja fica registrada na propria nota (erroEnvioMercadinho) e
            // pode ser reprocessada depois via POST /mercadinho/sincronizar - nao interrompe o loop.
            mercadinhoEnvioService.enviar(notaFiscalId);
        }
    }
}
