package com.vitainformatica.nfeimporter.sefaz;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Assina digitalmente um elemento de um XML seguindo o padrao exigido pela NF-e (envelope XML-DSig
 * "enveloped", canonicalizacao C14N sem comentarios, SHA-1/RSA-SHA1, certificado embutido em
 * {@code KeyInfo}) - o mesmo formato usado para assinar eventos como a Manifestacao do
 * Destinatario. Usa apenas a API padrao do JDK ({@code javax.xml.crypto.dsig}, JSR 105), sem
 * dependencias externas.
 */
public final class XmlAssinaturaService {

    private XmlAssinaturaService() {
    }

    /**
     * Assina o elemento cujo atributo {@code Id} seja igual a {@code idAlvo}, inserindo o
     * {@code <Signature>} resultante como irmao logo apos esse elemento (dentro do mesmo pai) -
     * exatamente a estrutura {@code <evento><infEvento Id="..."/><Signature/></evento>} exigida
     * pelo schema de eventos da NF-e.
     *
     * @param certificado certificado folha (titular) a embutir em {@code KeyInfo/X509Data}.
     *                    Confirmado contra {@code xmldsig-core-schema_v1.01.xsd} (schema oficial
     *                    da SEFAZ para assinatura de eventos): {@code X509DataType} so permite UM
     *                    unico {@code X509Certificate} (sequence sem maxOccurs = exatamente 1) -
     *                    embutir a cadeia completa (folha + AC intermediaria) quebra a validacao
     *                    de schema (cStat 225). Chegamos a tentar com a cadeia completa por
     *                    suspeita de que ajudaria um validador a montar a cadeia de confianca, mas
     *                    esse nao e o formato aceito por este servico - revertido.
     */
    public static String assinarElementoPorId(String xml, String idAlvo, PrivateKey chavePrivada,
            X509Certificate certificado) {
        try {
            Document doc = parseDocument(xml);
            Element elementoAlvo = marcarAtributoIdComoId(doc, idAlvo);

            XMLSignatureFactory fabrica = XMLSignatureFactory.getInstance("DOM");

            Reference referencia = fabrica.newReference(
                    "#" + idAlvo,
                    fabrica.newDigestMethod(DigestMethod.SHA1, null),
                    List.of(
                            fabrica.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                            fabrica.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null)),
                    null, null);

            SignedInfo signedInfo = fabrica.newSignedInfo(
                    fabrica.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (javax.xml.crypto.dsig.spec.C14NMethodParameterSpec) null),
                    fabrica.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                    List.of(referencia));

            KeyInfoFactory keyInfoFactory = fabrica.getKeyInfoFactory();
            X509Data x509Data = keyInfoFactory.newX509Data(List.of(certificado));
            KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(x509Data));

            // Insere o <Signature> como ultimo filho do pai do elemento assinado (= depois de infEvento
            // dentro de <evento>), que e a posicao exigida pelo schema da NF-e.
            DOMSignContext contexto = new DOMSignContext(chavePrivada, elementoAlvo.getParentNode());

            XMLSignature assinatura = fabrica.newXMLSignature(signedInfo, keyInfo);
            assinatura.sign(contexto);

            return serializar(doc);
        } catch (Exception e) {
            throw new AssinaturaXmlException("Falha ao assinar XML (elemento Id=" + idAlvo + ")", e);
        }
    }

    private static Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * A validacao/dereferenciamento de "#id" pela API de assinatura depende do DOM saber que o
     * atributo "Id" e do tipo ID (equivalente a um DTD declarando ID) - por padrao um parser sem
     * DTD/schema nao sabe disso, entao registramos manualmente via {@link Element#setIdAttribute}.
     */
    private static Element marcarAtributoIdComoId(Document doc, String idAlvo) {
        org.w3c.dom.NodeList todos = doc.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            Element el = (Element) todos.item(i);
            if (idAlvo.equals(el.getAttribute("Id"))) {
                el.setIdAttribute("Id", true);
                return el;
            }
        }
        throw new IllegalArgumentException("Nenhum elemento com atributo Id=\"" + idAlvo + "\" encontrado no XML");
    }

    private static String serializar(Document doc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(saida));
        return saida.toString(StandardCharsets.UTF_8);
    }

    public static class AssinaturaXmlException extends RuntimeException {
        public AssinaturaXmlException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
