package com.vitainformatica.nfeimporter.nfe;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.vitainformatica.nfeimporter.domain.TipoDocumento;

import io.quarkus.logging.Log;

/**
 * Extrai os campos de interesse (chave, emitente, valor, datas, protocolo...) do XML de cada
 * documento retornado pela Distribuicao de DFe, de acordo com o seu tipo/schema.
 * <p>
 * Usa busca por nome local de elemento (ignorando o namespace) para ser tolerante a pequenas
 * variacoes entre versoes de schema (ex: resNFe_v1.01.xsd vs. futuras revisoes).
 */
public final class NfeXmlExtractor {

    private NfeXmlExtractor() {
    }

    public static DadosDocumentoFiscal extrair(String xml, TipoDocumento tipo) {
        Document doc = parseDocument(xml);
        return switch (tipo) {
            case RESUMO_NFE -> extrairResNFe(doc);
            case NFE_COMPLETA -> extrairProcNFe(doc);
            case RESUMO_EVENTO, EVENTO_COMPLETO -> extrairEvento(doc);
            case OUTRO -> extrairGenerico(doc);
        };
    }

    private static DadosDocumentoFiscal extrairResNFe(Document doc) {
        Element raiz = doc.getDocumentElement();
        String chave = texto(raiz, "chNFe");
        String cnpjEmitente = texto(raiz, "CNPJ");
        String nomeEmitente = texto(raiz, "xNome");
        LocalDateTime dataEmissao = parseData(texto(raiz, "dhEmi"));
        BigDecimal valor = parseDecimal(texto(raiz, "vNF"));
        String protocolo = texto(raiz, "nProt");
        String situacao = descreverSituacaoResumo(texto(raiz, "cSitNFe"));
        return new DadosDocumentoFiscal(chave, cnpjEmitente, nomeEmitente, dataEmissao, valor, protocolo, situacao);
    }

    private static DadosDocumentoFiscal extrairProcNFe(Document doc) {
        Element raiz = doc.getDocumentElement();
        Element infNFe = primeiroDescendente(raiz, "infNFe");
        String chave = null;
        if (infNFe != null) {
            String id = infNFe.getAttribute("Id");
            chave = id != null && id.length() >= 44 ? id.substring(id.length() - 44) : id;
        }

        Element emit = primeiroDescendente(raiz, "emit");
        String cnpjEmitente = emit != null ? texto(emit, "CNPJ") : null;
        String nomeEmitente = emit != null ? texto(emit, "xNome") : null;

        Element ide = primeiroDescendente(raiz, "ide");
        LocalDateTime dataEmissao = ide != null ? parseData(texto(ide, "dhEmi")) : null;

        Element icmsTot = primeiroDescendente(raiz, "ICMSTot");
        BigDecimal valor = icmsTot != null ? parseDecimal(texto(icmsTot, "vNF")) : null;

        Element infProt = primeiroDescendente(raiz, "infProt");
        String protocolo = infProt != null ? texto(infProt, "nProt") : null;
        String situacao = infProt != null ? texto(infProt, "xMotivo") : "NF-e completa";

        return new DadosDocumentoFiscal(chave, cnpjEmitente, nomeEmitente, dataEmissao, valor, protocolo, situacao);
    }

    private static DadosDocumentoFiscal extrairEvento(Document doc) {
        Element raiz = doc.getDocumentElement();
        String chave = primeiroTextoPorNomeLocal(raiz, "chNFe");
        String cnpjAutor = primeiroTextoPorNomeLocal(raiz, "CNPJ");
        String dhEvento = primeiroTextoPorNomeLocal(raiz, "dhEvento");
        String tpEvento = primeiroTextoPorNomeLocal(raiz, "tpEvento");
        String xEvento = primeiroTextoPorNomeLocal(raiz, "xEvento");
        String protocolo = primeiroTextoPorNomeLocal(raiz, "nProt");

        LocalDateTime dataEvento = parseData(dhEvento);
        String situacao = xEvento != null ? xEvento : (tpEvento != null ? "Evento " + tpEvento : "Evento");

        return new DadosDocumentoFiscal(chave, cnpjAutor, null, dataEvento, null, protocolo, situacao);
    }

    private static DadosDocumentoFiscal extrairGenerico(Document doc) {
        Element raiz = doc.getDocumentElement();
        String chave = primeiroTextoPorNomeLocal(raiz, "chNFe");
        return new DadosDocumentoFiscal(chave, null, null, null, null, null, "Documento nao classificado (schema desconhecido)");
    }

    private static String descreverSituacaoResumo(String cSitNFe) {
        if (cSitNFe == null || cSitNFe.isBlank()) {
            return "Resumo de NF-e";
        }
        return switch (cSitNFe.trim()) {
            case "1" -> "Autorizada";
            case "2" -> "Cancelada";
            case "3" -> "Denegada";
            default -> "Resumo de NF-e (situacao " + cSitNFe + ")";
        };
    }

    // --- Helpers de XML ---

    private static Document parseDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("XML de documento fiscal invalido", e);
        }
    }

    /** Texto do primeiro filho direto com o nome local informado. */
    private static String texto(Element pai, String nomeLocal) {
        NodeList filhos = pai.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            Node n = filhos.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && nomeLocal.equals(n.getLocalName())) {
                return n.getTextContent();
            }
        }
        return null;
    }

    /** Primeiro elemento descendente (em qualquer profundidade) com o nome local informado. */
    private static Element primeiroDescendente(Element raiz, String nomeLocal) {
        NodeList nodes = raiz.getElementsByTagNameNS("*", nomeLocal);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private static String primeiroTextoPorNomeLocal(Element raiz, String nomeLocal) {
        Element el = primeiroDescendente(raiz, nomeLocal);
        return el != null ? el.getTextContent() : null;
    }

    private static LocalDateTime parseData(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(valor);
            } catch (DateTimeParseException e2) {
                Log.warnf("Nao foi possivel interpretar a data '%s' do documento fiscal", valor);
                return null;
            }
        }
    }

    private static BigDecimal parseDecimal(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(valor.trim());
        } catch (NumberFormatException e) {
            Log.warnf("Nao foi possivel interpretar o valor numerico '%s' do documento fiscal", valor);
            return null;
        }
    }
}
