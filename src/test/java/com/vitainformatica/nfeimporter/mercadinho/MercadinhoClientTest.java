package com.vitainformatica.nfeimporter.mercadinho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Sobe um {@link HttpServer} local (embutido no JDK, sem dependencias extras) simulando o
 * endpoint de importacao do mercadinho, para validar que o {@link MercadinhoClient} monta a
 * requisicao exatamente como o curl de referencia:
 * {@code curl -H "Authorization: Bearer TOKEN" -F "file=@nfe.xml;type=application/xml"}.
 */
class MercadinhoClientTest {

    private HttpServer servidor;

    @AfterEach
    void pararServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void deveEnviarXmlComAuthorizationECorpoMultipartCorretos() throws IOException {
        String[] authorizationCapturado = new String[1];
        String[] corpoCapturado = new String[1];
        String[] contentTypeCapturado = new String[1];

        servidor = iniciarServidor("/api/notas-fiscais/importar/xml", exchange -> {
            authorizationCapturado[0] = exchange.getRequestHeaders().getFirst("Authorization");
            contentTypeCapturado[0] = exchange.getRequestHeaders().getFirst("Content-Type");
            corpoCapturado[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            responder(exchange, 200, "ok");
        });

        MercadinhoClient client = new MercadinhoClient();
        MercadinhoClient.RespostaEnvio resposta = client.enviarXml(baseUrl(), "meu-token-123",
                "/api/notas-fiscais/importar/xml", Duration.ofSeconds(5), "35240512345678000199550010000001231234567890.xml",
                "<NFe>conteudo de teste</NFe>");

        assertTrue(resposta.sucesso());
        assertEquals(200, resposta.statusHttp());
        assertEquals("ok", resposta.corpo());

        assertEquals("Bearer meu-token-123", authorizationCapturado[0]);
        assertTrue(contentTypeCapturado[0].startsWith("multipart/form-data; boundary="));
        assertTrue(corpoCapturado[0].contains("name=\"file\""));
        assertTrue(corpoCapturado[0].contains("filename=\"35240512345678000199550010000001231234567890.xml\""));
        assertTrue(corpoCapturado[0].contains("Content-Type: application/xml"));
        assertTrue(corpoCapturado[0].contains("<NFe>conteudo de teste</NFe>"));
    }

    @Test
    void deveTratarRespostaDeErroComoFalhaSemLancarExcecao() throws IOException {
        servidor = iniciarServidor("/api/notas-fiscais/importar/xml",
                exchange -> responder(exchange, 422, "XML invalido"));

        MercadinhoClient client = new MercadinhoClient();
        MercadinhoClient.RespostaEnvio resposta = client.enviarXml(baseUrl(), "token", "/api/notas-fiscais/importar/xml",
                Duration.ofSeconds(5), "nfe.xml", "<NFe/>");

        assertFalse(resposta.sucesso());
        assertEquals(422, resposta.statusHttp());
        assertEquals("XML invalido", resposta.corpo());
    }

    @Test
    void deveLancarExcecaoDeComunicacaoQuandoServidorNaoResponde() {
        MercadinhoClient client = new MercadinhoClient();

        assertThrows(MercadinhoClient.MercadinhoComunicacaoException.class,
                () -> client.enviarXml("http://localhost:1", "token", "/api/notas-fiscais/importar/xml",
                        Duration.ofSeconds(2), "nfe.xml", "<NFe/>"));
    }

    private String baseUrl() {
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    private static HttpServer iniciarServidor(String caminho, com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext(caminho, handler);
        servidor.start();
        return servidor;
    }

    private static void responder(com.sun.net.httpserver.HttpExchange exchange, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
