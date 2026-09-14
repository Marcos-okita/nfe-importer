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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Interpreta a resposta SOAP do webservice de Distribuicao de DFe: localiza o elemento
 * {@code retDistDFeInt} (independente do namespace do envelope SOAP usado pelo servidor) e
 * descompacta cada {@code docZip} (base64 + gzip) em XML legivel.
 */
public final class DistDfeResponseParser {

    private DistDfeResponseParser() {
    }

    public static DistDfeResponse parse(String respostaSoapXml) {
        Document doc = parseDocument(respostaSoapXml);

        Element retDistDFeInt = primeiroElementoPorNomeLocal(doc, "retDistDFeInt")
                .orElseThrow(() -> new SefazRespostaInvalidaException(
                        "Resposta da SEFAZ nao contem o elemento retDistDFeInt esperado. XML recebido: "
                                + resumir(respostaSoapXml)));

        int cStat = Integer.parseInt(textoFilho(retDistDFeInt, "cStat", "0"));
        String xMotivo = textoFilho(retDistDFeInt, "xMotivo", "");
        String ultNSU = textoFilho(retDistDFeInt, "ultNSU", "000000000000000");
        String maxNSU = textoFilho(retDistDFeInt, "maxNSU", "000000000000000");

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

    private static Document parseDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Protecao basica contra XXE - a resposta vem de um servidor confiavel (SEFAZ via mTLS),
            // mas nao ha motivo para resolver entidades externas de qualquer forma.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new SefazRespostaInvalidaException("Resposta da SEFAZ nao e um XML valido: " + resumir(xml), e);
        }
    }

    private static java.util.Optional<Element> primeiroElementoPorNomeLocal(Document doc, String nomeLocal) {
        NodeList nodes = doc.getElementsByTagNameNS("*", nomeLocal);
        if (nodes.getLength() == 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of((Element) nodes.item(0));
    }

    private static String textoFilho(Element pai, String nomeLocal, String valorPadrao) {
        NodeList filhos = pai.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            Node filho = filhos.item(i);
            if (filho.getNodeType() == Node.ELEMENT_NODE && nomeLocal.equals(filho.getLocalName())) {
                return filho.getTextContent();
            }
        }
        return valorPadrao;
    }

    private static String resumir(String xml) {
        if (xml == null) {
            return "";
        }
        return xml.length() > 500 ? xml.substring(0, 500) + "..." : xml;
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
