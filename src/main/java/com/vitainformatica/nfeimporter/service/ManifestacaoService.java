package com.vitainformatica.nfeimporter.service;

import java.util.List;

import com.vitainformatica.nfeimporter.config.NfeConfig;
import com.vitainformatica.nfeimporter.sefaz.ManifestacaoResponse;
import com.vitainformatica.nfeimporter.sefaz.RecepcaoEventoClient;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Registra a Manifestacao do Destinatario ("Ciencia da Operacao") para NF-e cujo resumo ja foi
 * importado, destravando o recebimento do XML completo numa consulta seguinte a Distribuicao de
 * DFe. Nunca propaga excecao para quem chama - toda falha vira um {@link ResultadoManifestacao}
 * com {@code sucesso=false}, e o registro fica pendente para nova tentativa.
 */
@ApplicationScoped
public class ManifestacaoService {

    /** Sempre 1: no fluxo automatico so enviamos "Ciencia da Operacao" uma unica vez por chave. */
    private static final int N_SEQ_EVENTO = 1;

    @Inject
    RecepcaoEventoClient client;

    @Inject
    ManifestacaoPersistenceService persistenceService;

    @Inject
    NfeConfig config;

    public record ResultadoManifestacao(Long manifestacaoId, boolean sucesso, String mensagem) {
    }

    public record ResultadoSincronizacao(int total, int sucesso, int falha) {
    }

    public ResultadoManifestacao enviar(Long manifestacaoId) {
        ManifestacaoPersistenceService.DadosParaEnvio dados = persistenceService.carregar(manifestacaoId);
        if (dados == null) {
            return new ResultadoManifestacao(manifestacaoId, false, "Registro de manifestacao " + manifestacaoId + " nao encontrado");
        }

        try {
            ManifestacaoResponse resposta = client.enviarCienciaOperacao(dados.chaveAcesso(), N_SEQ_EVENTO);
            persistenceService.marcarResultado(manifestacaoId, resposta);

            // cStatEfetivo/xMotivoEfetivo cobrem tanto o caso normal (evento processado) quanto o
            // lote rejeitado antes de processar qualquer evento (ex: cStat 225 - falha de schema),
            // caso em que cStatEvento/xMotivoEvento vem null.
            String mensagem = "cStat " + resposta.cStatEfetivo() + ": " + resposta.xMotivoEfetivo();
            if (resposta.sucesso()) {
                Log.infof("Manifestacao (Ciencia da Operacao) registrada para chave %s: %s", dados.chaveAcesso(), mensagem);
            } else {
                Log.warnf("Falha ao registrar manifestacao para chave %s: %s", dados.chaveAcesso(), mensagem);
            }
            return new ResultadoManifestacao(manifestacaoId, resposta.sucesso(), mensagem);
        } catch (Exception e) {
            persistenceService.marcarFalha(manifestacaoId, e.getMessage());
            Log.errorf(e, "Erro ao registrar manifestacao para chave %s", dados.chaveAcesso());
            return new ResultadoManifestacao(manifestacaoId, false, String.valueOf(e.getMessage()));
        }
    }

    /** Envia (ou reenvia) a manifestacao para uma chave especifica, criando o controle se ainda nao existir. */
    public ResultadoManifestacao enviarPorChave(String chaveAcesso) {
        Long id = persistenceService.obterOuCriarId(chaveAcesso, config.cnpj());
        return enviar(id);
    }

    /** Reenvia todas as manifestacoes ainda pendentes (ate {@code limite} por vez). */
    public ResultadoSincronizacao sincronizarPendentes(int limite) {
        List<Long> pendentes = persistenceService.listarIdsPendentes(limite);
        int sucesso = 0;
        int falha = 0;
        for (Long id : pendentes) {
            if (enviar(id).sucesso()) {
                sucesso++;
            } else {
                falha++;
            }
        }
        return new ResultadoSincronizacao(pendentes.size(), sucesso, falha);
    }
}
