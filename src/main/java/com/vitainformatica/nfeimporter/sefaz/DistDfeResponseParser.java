package com.vitainformatica.nfeimporter.sefaz;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Interpreta a resposta SOAP do webservice de Distribuicao de DFe: localiza o elemento
 * {@code retDistDFeInt} (independente do namespace do envelope SOAP usado pelo servidor) e
 * descompacta cada {@code docZip} (base64 + gzip) em XML legivel.
 */
public final class DistDfeResponseParser {

    private DistDfeResponseParser() {
    }

    public static DistDfeResponse parse(String respostaSoapXml) {
        Document doc = SefazXmlUtil.parseDocument(respostaSoapXml);

        Element retDistDFeInt = SefazXmlUtil.primeiroElementoPorNomeLocal(doc, "retDistDFeInt")
                .orElseThrow(() -> new SefazRespostaInvalidaException(
                        "Resposta da SEFAZ nao contem o elemento retDistDFeInt esperado. XML recebido: "
                                + SefazXmlUtil.resumir(respostaSoapXml)));

        int cStat = Integer.parseInt(SefazXmlUtil.textoFilho(retDistDFeInt, "cStat", "0"));
        String xMotivo = SefazXmlUtil.textoFilho(retDistDFeInt, "xMotivo", "");
        String ultNSU = SefazXmlUtil.textoFilho(retDistDFeInt, "ultNSU", "000000000000000");
        String maxNSU = SefazXmlUtil.textoFilho(retDistDFeInt, "maxNSU", "000000000000000");

        List<DistDfeDocumento> documentos = new ArrayList<>();
        NodeList docZipNodes = retDistDFeInt.getElementsByTagNameNS("*", "docZip");
        for (int i = 0; i < docZipNodes.getLength(); i++) {
            Element docZip = (Element) docZipNodes.item(i);
            String nsu = docZip.getAttribute("NSU");
            String schema = docZip.getAttribute("schema");
            String xmlDescompactado = descompactar(docZip.getTextContent());
            documentos.add(new DistDfeDocumento(nsu, schema, xmlDescompactado));
        }

        return new DistDfeResponse(cStat, xMotivo, ultNSU, maxNSU, documentos);
    }

    private static String descompactar(String base64Gzip) {
        byte[] comprimido = Base64.getMimeDecoder().decode(base64Gzip.trim());
        try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(comprimido));
                ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            gzip.transferTo(saida);
            return saida.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SefazRespostaInvalidaException("Falha ao descompactar docZip retornado pela SEFAZ", e);
        }
    }

    public static class SefazRespostaInvalidaException extends RuntimeException {
        public SefazRespostaInvalidaException(String message) {
            super(message);
        }

        public SefazRespostaInvalidaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
