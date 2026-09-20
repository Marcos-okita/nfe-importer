package com.vitainformatica.nfeimporter.sefaz;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Interpreta a resposta SOAP do webservice de Recepcao de Evento ({@code retEnvEvento}), extraindo
 * tanto o status do lote quanto o status do evento individual (dentro de {@code retEvento/infEvento}).
 */
public final class ManifestacaoResponseParser {

    private ManifestacaoResponseParser() {
    }

    public static ManifestacaoResponse parse(String respostaSoapXml) {
        Document doc = SefazXmlUtil.parseDocument(respostaSoapXml);

        Element retEnvEvento = SefazXmlUtil.primeiroElementoPorNomeLocal(doc, "retEnvEvento")
                .orElseThrow(() -> new SefazRespostaInvalidaException(
                        "Resposta da SEFAZ nao contem o elemento retEnvEvento esperado. XML recebido: "
                                + SefazXmlUtil.resumir(respostaSoapXml)));

        Integer cStatLote = parseIntOuNulo(SefazXmlUtil.textoFilho(retEnvEvento, "cStat", null));
        String xMotivoLote = SefazXmlUtil.textoFilho(retEnvEvento, "xMotivo", null);

        Element infEvento = SefazXmlUtil.primeiroElementoPorNomeLocal(retEnvEvento, "infEvento").orElse(null);
        if (infEvento == null) {
            // Lote rejeitado antes mesmo de processar o evento (ex: XML mal formado, assinatura invalida) -
            // so temos o status do lote.
            return new ManifestacaoResponse(cStatLote, xMotivoLote, null, null, null);
        }

        Integer cStatEvento = parseIntOuNulo(SefazXmlUtil.textoFilho(infEvento, "cStat", null));
        String xMotivoEvento = SefazXmlUtil.textoFilho(infEvento, "xMotivo", null);
        String protocolo = SefazXmlUtil.textoFilho(infEvento, "nProt", null);

        return new ManifestacaoResponse(cStatLote, xMotivoLote, cStatEvento, xMotivoEvento, protocolo);
    }

    private static Integer parseIntOuNulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static class SefazRespostaInvalidaException extends RuntimeException {
        public SefazRespostaInvalidaException(String message) {
            super(message);
        }
    }
}
