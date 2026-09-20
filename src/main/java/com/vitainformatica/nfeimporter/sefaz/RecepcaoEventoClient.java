package com.vitainformatica.nfeimporter.sefaz;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import com.vitainformatica.nfeimporter.config.NfeConfig;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Cliente do webservice SOAP "NFeRecepcaoEvento4" da SEFAZ, usado para
 * registrar a Manifestacao do
 * Destinatario (evento "Ciencia da Operacao") - o que destrava, numa consulta
 * posterior a
 * Distribuicao de DFe, o recebimento do XML completo (procNFe) da NF-e
 * correspondente.
 * <p>
 * Ao contrario da consulta de distribuicao (que so exige mTLS), o evento aqui
 * precisa ser
 * digitalmente assinado (XML-DSig) com o certificado - ver
 * {@link XmlAssinaturaService}.
 */
@ApplicationScoped
public class RecepcaoEventoClient {

    @Inject
    NfeConfig config;

    private volatile HttpClient httpClient;
    private volatile CertificadoEntradaLoader.Entrada certificado;

    public ManifestacaoResponse enviarCienciaOperacao(String chaveAcesso, int nSeqEvento) {
        boolean producao = isProducao();
        int tpAmb = producao ? 1 : 2;
        // Tipo Código de orgão (UF da tabela do IBGE + 90 SUFRAMA + 91 - RFB
        int cOrgao = 91; // RFB - codigo do orgao autorizador (UF do autor do evento), nao Ambiente
                         // Nacional

        String envEventoXml = ManifestacaoEventoRequestBuilder.cienciaOperacao(
                tpAmb, cOrgao, chaveAcesso, config.cnpj(), Instant.now(), nSeqEvento);
        String idEvento = ManifestacaoEventoRequestBuilder.idEvento(chaveAcesso, nSeqEvento);

        CertificadoEntradaLoader.Entrada entrada = certificado();
        String envEventoAssinado = XmlAssinaturaService.assinarElementoPorId(
                envEventoXml, idEvento, entrada.chavePrivada(), entrada.certificado());

        String envelopeSoap = ManifestacaoEventoRequestBuilder.envelopeSoap(envEventoAssinado);
        String endpoint = producao ? config.manifestacao().endpointProducao()
                : config.manifestacao().endpointHomologacao();

        Log.infof(
                "Enviando evento de manifestacao (Ciencia da Operacao) para chave %s, nSeqEvento %d, ambiente %s, endpoint %s",
                chaveAcesso, nSeqEvento, producao ? "producao" : "homologacao", endpoint);
        // Log.infof("Envelope SOAP:\n%s", envelopeSoap);

        String respostaXml = enviar(endpoint, envelopeSoap);
        return ManifestacaoResponseParser.parse(respostaXml);
    }

    private boolean isProducao() {
        return "producao".equalsIgnoreCase(config.ambiente().trim());
    }

    private String enviar(String endpoint, String envelopeSoap) {
        try {
            // SOAP 1.2: action embutida no Content-Type (sem header SOAPAction separado) -
            // mesmo padrao usado por NfeDistribuicaoClient para a Distribuicao de DFe.
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(config.manifestacao().timeoutSegundos()))
                    .header("Content-Type", "application/soap+xml; charset=utf-8; action=\""
                            + ManifestacaoEventoRequestBuilder.soapAction() + "\"")
                    .POST(HttpRequest.BodyPublishers.ofString(envelopeSoap, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTP status: " + response.statusCode());
            System.out.println("Content-Type: " +
                    response.headers().firstValue("Content-Type").orElse(""));
            System.out.println("BODY:");
            System.out.println(response.body());
            if (response.statusCode() / 100 != 2) {
                throw new SefazComunicacaoException(
                        "SEFAZ retornou HTTP " + response.statusCode() + " ao registrar manifestacao. Corpo: "
                                + resumir(response.body()));
            }
            return response.body();
        } catch (IOException e) {
            throw new SefazComunicacaoException(
                    "Falha de comunicacao com o webservice de Recepcao de Evento em " + endpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SefazComunicacaoException("Chamada ao webservice de Recepcao de Evento interrompida", e);
        }
    }

    private HttpClient httpClient() {
        HttpClient local = httpClient;
        if (local == null) {
            synchronized (this) {
                local = httpClient;
                if (local == null) {
                    local = MutualTlsHttpClientFactory.criar(
                            Path.of(config.certificado().caminho()),
                            config.certificado().senha().toCharArray(),
                            Duration.ofSeconds(config.manifestacao().timeoutSegundos()));
                    httpClient = local;
                }
            }
        }
        return local;
    }

    private CertificadoEntradaLoader.Entrada certificado() {
        CertificadoEntradaLoader.Entrada local = certificado;
        if (local == null) {
            synchronized (this) {
                local = certificado;
                if (local == null) {
                    Log.info("Carregando certificado digital A1 para assinatura de eventos de manifestacao: "
                            + config.certificado().caminho());
                    local = CertificadoEntradaLoader.carregar(
                            Path.of(config.certificado().caminho()),
                            config.certificado().senha().toCharArray());
                    certificado = local;
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
