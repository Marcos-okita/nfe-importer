package com.vitainformatica.nfeimporter.sefaz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reproduz, passo a passo, exatamente a mesma sequencia de chamadas que
 * {@link RecepcaoEventoClient#enviarCienciaOperacao(String, int)} faz (montar o envEvento, assinar,
 * envelopar em SOAP) - sem rede, sem Quarkus/CDI - para inspecionar visualmente o XML resultante em
 * cada etapa e confirmar se o {@code <evento>} realmente fica vazio apos a assinatura (suspeita
 * levantada ao ler o codigo) ou se o problema esta em outro lugar (ex: processo rodando com
 * bytecode antigo).
 */
class RecepcaoEventoClientLogicTest {

    private static final char[] SENHA = "senha-teste-123".toCharArray();

    @TempDir
    Path tempDir;

    @Test
    void reproduzSequenciaDeEnviarCienciaOperacao() throws Exception {
        // Mesmos dados do caso real que esta sendo depurado.
        String chaveAcesso = "35260937999642000580550010000054211978057649";
        String cnpjAutor = "10300984000180";
        int tpAmb = 1; // producao
        int cOrgao = 35; // SP - codigo do orgao autorizador (UF do autor do evento), nao Ambiente Nacional
        int nSeqEvento = 1;

        // --- Passo 1 (igual ao enviarCienciaOperacao): monta o envEvento sem assinatura ---
        String envEventoXml = ManifestacaoEventoRequestBuilder.cienciaOperacao(
                tpAmb, cOrgao, chaveAcesso, cnpjAutor, Instant.now(), nSeqEvento);
        String idEvento = ManifestacaoEventoRequestBuilder.idEvento(chaveAcesso, nSeqEvento);

        System.out.println("=== 1) envEvento SEM assinatura ===");
        System.out.println(envEventoXml);
        System.out.println();

        assertTrue(envEventoXml.contains("<infEvento Id=\"" + idEvento + "\">"), "infEvento deveria estar presente antes de assinar");
        assertTrue(envEventoXml.contains("<cOrgao>35</cOrgao>"), "campos do infEvento deveriam estar presentes antes de assinar");
        assertTrue(!envEventoXml.contains("<evento versao=\"1.00\"></evento>"), "evento nao deveria estar vazio antes de assinar");

        // --- Passo 2 (igual ao enviarCienciaOperacao): carrega certificado e assina ---
        Path pfx = tempDir.resolve("teste.p12");
        gerarKeystoreTeste(pfx, "CN=NFe Importer Teste, O=Vita Informatica, C=BR");
        KeyStore keyStore = carregarKeyStore(pfx);
        CertificadoEntradaLoader.Entrada entrada = CertificadoEntradaLoader.carregarDoKeyStore(keyStore, SENHA);

        String envEventoAssinado = XmlAssinaturaService.assinarElementoPorId(
                envEventoXml, idEvento, entrada.chavePrivada(), entrada.certificado());

        System.out.println("=== 2) envEvento ASSINADO ===");
        System.out.println(envEventoAssinado);
        System.out.println();

        // A pergunta central do usuario: o <evento> fica vazio (so com <Signature>, sem infEvento)
        // depois de assinar?
        assertTrue(envEventoAssinado.contains("<infEvento Id=\"" + idEvento + "\">"),
                "infEvento NAO deveria sumir apos assinar - se essa asserção falhar, o bug esta em XmlAssinaturaService");
        assertTrue(envEventoAssinado.contains("<cOrgao>35</cOrgao>"),
                "os campos de dentro do infEvento (cOrgao, tpAmb, CNPJ, chNFe...) NAO deveriam sumir apos assinar");
        assertTrue(envEventoAssinado.contains("<Signature"), "a assinatura deveria ter sido adicionada");
        assertTrue(!envEventoAssinado.contains("<evento versao=\"1.00\"><Signature"),
                "evento nao deveria conter SO a Signature (sem infEvento antes dela)");

        // --- Passo 3 (igual ao enviarCienciaOperacao): envelopa em SOAP ---
        String envelopeSoap = ManifestacaoEventoRequestBuilder.envelopeSoap(envEventoAssinado);

        System.out.println("=== 3) Envelope SOAP final (o que de fato seria enviado a SEFAZ) ===");
        System.out.println(envelopeSoap);
        System.out.println();
        System.out.println("SOAPAction: " + ManifestacaoEventoRequestBuilder.soapAction());

        assertTrue(envelopeSoap.contains("<infEvento Id=\"" + idEvento + "\">"),
                "o envelope final deveria conter o infEvento preenchido, nao vazio");
        assertTrue(envelopeSoap.contains(envEventoAssinado), "o envelope deveria conter o envEvento assinado por inteiro, sem cortes");
    }

    private static KeyStore carregarKeyStore(Path pfx) throws Exception {
        try (var in = Files.newInputStream(pfx)) {
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
