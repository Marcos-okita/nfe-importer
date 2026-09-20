package com.vitainformatica.nfeimporter.sefaz;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Helpers de parsing XML compartilhados pelos parsers de resposta dos webservices da SEFAZ
 * (Distribuicao de DFe, Recepcao de Evento). Busca por nome local (ignorando namespace/prefixo) -
 * as respostas variam de prefixo entre servidores/versoes, mas o nome local dos elementos e estavel.
 */
final class SefazXmlUtil {

    private SefazXmlUtil() {
    }

    static Document parseDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Protecao basica contra XXE - a resposta vem de um servidor confiavel (SEFAZ via mTLS),
            // mas nao ha motivo para resolver entidades externas de qualquer forma.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new XmlInvalidoException("XML invalido: " + resumir(xml), e);
        }
    }

    static Optional<Element> primeiroElementoPorNomeLocal(Document doc, String nomeLocal) {
        NodeList nodes = doc.getElementsByTagNameNS("*", nomeLocal);
        return nodes.getLength() == 0 ? Optional.empty() : Optional.of((Element) nodes.item(0));
    }

    static Optional<Element> primeiroElementoPorNomeLocal(Element pai, String nomeLocal) {
        NodeList nodes = pai.getElementsByTagNameNS("*", nomeLocal);
        return nodes.getLength() == 0 ? Optional.empty() : Optional.of((Element) nodes.item(0));
    }

    /** Texto do primeiro filho DIRETO (nao descendente qualquer) com o nome local informado. */
    static String textoFilho(Element pai, String nomeLocal, String valorPadrao) {
        NodeList filhos = pai.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            Node filho = filhos.item(i);
            if (filho.getNodeType() == Node.ELEMENT_NODE && nomeLocal.equals(filho.getLocalName())) {
                return filho.getTextContent();
            }
        }
        return valorPadrao;
    }

    static String resumir(String xml) {
        if (xml == null) {
            return "";
        }
        return xml.length() > 500 ? xml.substring(0, 500) + "..." : xml;
    }

    static class XmlInvalidoException extends RuntimeException {
        XmlInvalidoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
