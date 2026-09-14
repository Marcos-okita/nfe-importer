package com.vitainformatica.nfeimporter.sefaz;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import com.vitainformatica.nfeimporter.config.NfeConfig;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Cliente do webservice SOAP "NFeDistribuicaoDFe" da SEFAZ, responsavel por consultar, com o
 * certificado digital do CNPJ destinatario, todos os documentos fiscais (resumos e/ou NF-e
 * completas) emitidos tendo esse CNPJ como destinatario.
 */
@ApplicationScoped
public class NfeDistribuicaoClient {

    @Inject
    NfeConfig config;

    private volatile HttpClient httpClient;

    /**
     * Consulta o proximo lote de documentos a partir do NSU informado (paginacao contínua).
     * Use {@code ultNsu = "0"} para iniciar a distribuicao do zero (todo o historico disponivel
     * no ambiente da SEFAZ, tipicamente os ultimos meses).
     */
    public DistDfeResponse consultarDistribuicao(String ultNsu) {
        boolean producao = isProducao();
        int tpAmb = producao ? 1 : 2;
        int cUf = CodigoUf.deSigla(config.uf());

        String distDfeIntXml = DistDfeRequestBuilder.distNsu(tpAmb, cUf, config.cnpj(), ultNsu);
        String envelopeSoap = DistDfeRequestBuilder.envelopeSoap(distDfeIntXml, cUf);
        String endpoint = producao ? config.distribuicao().endpointProducao() : config.distribuicao().endpointHomologacao();

        String respostaXml = enviar(endpoint, envelopeSoap);
        return DistDfeResponseParser.parse(respostaXml);
    }

    private boolean isProducao() {
        return "producao".equalsIgnoreCase(config.ambiente().trim());
    }

    private String enviar(String endpoint, String envelopeSoap) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(config.distribuicao().timeoutSegundos()))
                    .header("Content-Type", "application/soap+xml; charset=utf-8; action=\"" + DistDfeRequestBuilder.soapAction() + "\"")
                    .POST(HttpRequest.BodyPublishers.ofString(envelopeSoap, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new SefazComunicacaoException(
                        "SEFAZ retornou HTTP " + response.statusCode() + " ao consultar distribuicao de DFe. Corpo: "
                                + resumir(response.body()));
            }
            return response.body();
        } catch (IOException e) {
            throw new SefazComunicacaoException("Falha de comunicacao com o webservice da SEFAZ em " + endpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SefazComunicacaoException("Chamada ao webservice da SEFAZ interrompida", e);
        }
    }

    private HttpClient httpClient() {
        HttpClient local = httpClient;
        if (local == null) {
            synchronized (this) {
                local = httpClient;
                if (local == null) {
                    Log.info("Carregando certificado digital A1 para autenticacao mTLS com a SEFAZ: "
                            + config.certificado().caminho());
                    local = MutualTlsHttpClientFactory.criar(
                            Path.of(config.certificado().caminho()),
                            config.certificado().senha().toCharArray(),
                            Duration.ofSeconds(config.distribuicao().timeoutSegundos()));
                    httpClient = local;
                }
            }
        }
        return local;
    }

    private static String resumir(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.length() > 1000 ? texto.substring(0, 1000) + "..." : texto;
    }

    public static class SefazComunicacaoException extends RuntimeException {
        public SefazComunicacaoException(String message) {
            super(message);
        }

        public SefazComunicacaoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
