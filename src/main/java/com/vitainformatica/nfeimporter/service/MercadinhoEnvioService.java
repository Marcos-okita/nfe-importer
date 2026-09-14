package com.vitainformatica.nfeimporter.service;

import java.util.List;

import com.vitainformatica.nfeimporter.mercadinho.MercadinhoClient;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Encaminha o XML de notas fiscais ja importadas para o mercadinho. Nunca propaga excecao para
 * quem chama - toda falha (rede, HTTP, nota inexistente) vira um {@link ResultadoEnvio} com
 * {@code sucesso=false}, e a nota fica marcada como pendente para nova tentativa.
 */
@ApplicationScoped
public class MercadinhoEnvioService {

    @Inject
    MercadinhoClient client;

    @Inject
    MercadinhoEnvioPersistenceService persistenceService;

    public record ResultadoEnvio(Long notaFiscalId, boolean sucesso, String mensagem) {
    }

    public record ResultadoSincronizacao(int total, int sucesso, int falha) {
    }

    public ResultadoEnvio enviar(Long notaFiscalId) {
        MercadinhoEnvioPersistenceService.DadosParaEnvio dados = persistenceService.carregarParaEnvio(notaFiscalId);
        if (dados == null) {
            return new ResultadoEnvio(notaFiscalId, false, "Nota fiscal " + notaFiscalId + " nao encontrada");
        }

        try {
            MercadinhoClient.RespostaEnvio resposta = client.enviarXml(dados.nomeArquivo(), dados.xml());
            if (resposta.sucesso()) {
                persistenceService.marcarEnviado(notaFiscalId);
                Log.infof("Nota fiscal %d encaminhada ao mercadinho com sucesso (HTTP %d)",
                        notaFiscalId, resposta.statusHttp());
                return new ResultadoEnvio(notaFiscalId, true, "HTTP " + resposta.statusHttp());
            }

            String erro = "HTTP " + resposta.statusHttp() + ": " + resumir(resposta.corpo());
            persistenceService.marcarFalha(notaFiscalId, erro);
            Log.warnf("Falha ao encaminhar nota fiscal %d ao mercadinho: %s", notaFiscalId, erro);
            return new ResultadoEnvio(notaFiscalId, false, erro);
        } catch (Exception e) {
            persistenceService.marcarFalha(notaFiscalId, e.getMessage());
            Log.errorf(e, "Erro ao encaminhar nota fiscal %d ao mercadinho", notaFiscalId);
            return new ResultadoEnvio(notaFiscalId, false, String.valueOf(e.getMessage()));
        }
    }

    /** Reenviar todas as notas ainda nao confirmadas como entregues ao mercadinho (ate {@code limite} por vez). */
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

    private static String resumir(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.length() > 300 ? texto.substring(0, 300) + "..." : texto;
    }
}
