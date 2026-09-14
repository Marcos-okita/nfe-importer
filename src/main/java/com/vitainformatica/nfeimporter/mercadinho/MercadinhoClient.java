package com.vitainformatica.nfeimporter.mercadinho;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.vitainformatica.nfeimporter.config.MercadinhoConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Cliente do endpoint de importacao de XML do mercadinho. Envia o XML como um upload
 * {@code multipart/form-data} (campo "file"), equivalente a:
 * <pre>
 * curl -X POST "&lt;api&gt;&lt;caminho-importacao&gt;" \
 *   -H "Authorization: Bearer &lt;token&gt;" \
 *   -F "file=@nfe.xml;type=application/xml"
 * </pre>
 */
@ApplicationScoped
public class MercadinhoClient {

    @Inject
    MercadinhoConfig config;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public RespostaEnvio enviarXml(String nomeArquivo, String xml) {
        if (!config.configurado()) {
            throw new MercadinhoComunicacaoException(
                    "Integracao com o mercadinho nao configurada (mercadinho.api / mercadinho.token ausentes)", null);
        }
        return enviarXml(config.api().orElseThrow(), config.token().orElseThrow(), config.caminhoImportacao(),
                Duration.ofSeconds(config.timeoutSegundos()), nomeArquivo, xml);
    }

    /**
     * Pacote-visivel e sem dependencia de CDI/config para permitir testar contra um servidor HTTP
     * local, sem precisar bootstrapar o Quarkus.
     */
    RespostaEnvio enviarXml(String apiBase, String token, String caminhoImportacao, Duration timeout,
            String nomeArquivo, String xml) {
        MultipartFormDataBuilder multipart = new MultipartFormDataBuilder()
                .adicionarArquivo("file", nomeArquivo, "application/xml", xml.getBytes(StandardCharsets.UTF_8));
        byte[] corpo = multipart.build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(concatenarUrl(apiBase, caminhoImportacao)))
                .timeout(timeout)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", multipart.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean sucesso = response.statusCode() / 100 == 2;
            return new RespostaEnvio(sucesso, response.statusCode(), response.body());
        } catch (IOException e) {
            throw new MercadinhoComunicacaoException(
                    "Falha de comunicacao com o mercadinho em " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MercadinhoComunicacaoException("Chamada ao mercadinho interrompida", e);
        }
    }

    private static String concatenarUrl(String base, String caminho) {
        String baseSemBarraFinal = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String caminhoComBarraInicial = caminho.startsWith("/") ? caminho : "/" + caminho;
        return baseSemBarraFinal + caminhoComBarraInicial;
    }

    public record RespostaEnvio(boolean sucesso, int statusHttp, String corpo) {
    }

    public static class MercadinhoComunicacaoException extends RuntimeException {
        public MercadinhoComunicacaoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
