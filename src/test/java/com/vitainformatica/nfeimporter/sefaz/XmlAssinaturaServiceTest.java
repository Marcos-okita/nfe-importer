package com.vitainformatica.nfeimporter.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import javax.xml.crypto.KeySelector;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Verifica que a assinatura produzida por {@link XmlAssinaturaService} e criptograficamente valida
 * de verdade - nao basta o XML "ter uma tag Signature", a assinatura precisa validar contra o
 * conteudo assinado e a chave publica do certificado, exatamente como a SEFAZ faria ao receber o
 * evento de manifestacao.
 */
class XmlAssinaturaServiceTest {

    private static final char[] SENHA = "senha-teste-123".toCharArray();
    private static final String CHAVE_ACESSO = "35240512345678000199550010000001231234567890";

    @TempDir
    Path tempDir;

    @Test
    void assinaturaGeradaDeveSerValidaCriptograficamente() throws Exception {
        Path pfx = tempDir.resolve("teste.p12");
        gerarKeystoreTeste(pfx, "CN=NFe Importer Teste, O=Vita Informatica, C=BR");

        KeyStore keyStore = carregarKeyStore(pfx);
        CertificadoEntradaLoader.Entrada entrada = CertificadoEntradaLoader.carregarDoKeyStore(keyStore, SENHA);

        String idEvento = ManifestacaoEventoRequestBuilder.idEvento(CHAVE_ACESSO, 1);
        String envEvento = ManifestacaoEventoRequestBuilder.cienciaOperacao(2, CHAVE_ACESSO, "11222333000181", Instant.now(), 1);

        String xmlAssinado = XmlAssinaturaService.assinarElementoPorId(
                envEvento, idEvento, entrada.chavePrivada(), entrada.cadeiaCompleta());

        assertTrue(xmlAssinado.contains("<Signature"), "o XML resultante deveria conter o elemento Signature");
        assertTrue(validarAssinatura(xmlAssinado, idEvento, entrada.certificado()),
                "a assinatura deveria validar com a chave publica do certificado");
    }

    @Test
    void assinaturaNaoDeveValidarSeOConteudoForAlteradoDepois() throws Exception {
        Path pfx = tempDir.resolve("teste2.p12");
        gerarKeystoreTeste(pfx, "CN=NFe Importer Teste, O=Vita Informatica, C=BR");

        KeyStore keyStore = carregarKeyStore(pfx);
        CertificadoEntradaLoader.Entrada entrada = CertificadoEntradaLoader.carregarDoKeyStore(keyStore, SENHA);

        String idEvento = ManifestacaoEventoRequestBuilder.idEvento(CHAVE_ACESSO, 1);
        String envEvento = ManifestacaoEventoRequestBuilder.cienciaOperacao(2, CHAVE_ACESSO, "11222333000181", Instant.now(), 1);
        String xmlAssinado = XmlAssinaturaService.assinarElementoPorId(
                envEvento, idEvento, entrada.chavePrivada(), entrada.cadeiaCompleta());

        // Adultera o conteudo assinado (troca o CNPJ do autor, que nao aparece dentro da chave de
        // acesso nem do atributo Id) sem re-assinar - a validacao deve falhar.
        String xmlAdulterado = xmlAssinado.replace("11222333000181", "99999999000191");

        assertTrue(!validarAssinatura(xmlAdulterado, idEvento, entrada.certificado()),
                "a assinatura deveria falhar apos o conteudo assinado ser alterado");
    }

    private static boolean validarAssinatura(String xmlAssinado, String idAlvo, X509Certificate certificado) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlAssinado.getBytes(StandardCharsets.UTF_8)));

        // Mesmo passo necessario ao assinar: o validador precisa saber que o atributo "Id" e um ID
        // para conseguir resolver a referencia "#idAlvo" dentro do SignedInfo.
        marcarAtributoIdComoId(doc, idAlvo);

        NodeList signatureNodes = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        assertEquals(1, signatureNodes.getLength(), "deveria haver exatamente um elemento Signature");
        Element signatureElement = (Element) signatureNodes.item(0);

        DOMValidateContext contexto = new DOMValidateContext(
                KeySelector.singletonKeySelector(certificado.getPublicKey()), signatureElement);
        // O padrao da NF-e exige RSA-SHA1 (nao e escolha nossa - e o algoritmo fixado pelo schema
        // oficial de eventos), mas o JDK bloqueia SHA-1 por padrao em modo "secure validation" ao
        // desserializar/validar uma assinatura. Desabilitamos aqui so para o teste conseguir
        // validar o que de fato sera enviado a SEFAZ.
        contexto.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        XMLSignatureFactory fabrica = XMLSignatureFactory.getInstance("DOM");
        XMLSignature assinatura = fabrica.unmarshalXMLSignature(contexto);
        return assinatura.validate(contexto);
    }

    private static void marcarAtributoIdComoId(Document doc, String idAlvo) {
        NodeList todos = doc.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            Element el = (Element) todos.item(i);
            if (idAlvo.equals(el.getAttribute("Id"))) {
                el.setIdAttribute("Id", true);
                return;
            }
        }
        throw new IllegalStateException("Elemento com Id=" + idAlvo + " nao encontrado");
    }

    private static KeyStore carregarKeyStore(Path pfx) throws Exception {
        try (var in = java.nio.file.Files.newInputStream(pfx)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(in, SENHA);
            return keyStore;
        }
    }

    private static void gerarKeystoreTeste(Path destino, String dn) throws IOException, InterruptedException {
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool").toString();

        Process processo = new ProcessBuilder(
                keytool, "-genkeypair",
                "-alias", "teste",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-dname", dn,
                "-validity", "3650",
                "-keystore", destino.toString(),
                "-storetype", "PKCS12",
                "-storepass", new String(SENHA),
                "-keypass", new String(SENHA),
                "-noprompt")
                .redirectErrorStream(true)
                .start();

        String saida = new String(processo.getInputStream().readAllBytes());
        boolean terminou = processo.waitFor(30, TimeUnit.SECONDS);
        if (!terminou || processo.exitValue() != 0) {
            throw new IllegalStateException("Falha ao gerar certificado de teste com keytool. Saida: " + saida);
        }
    }
}
