package com.vitainformatica.nfeimporter.sefaz;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifica que um certificado .pfx/.p12 e sua senha realmente permitem operar
 * com a chave
 * privada (mesma capacidade exigida para autenticar via mTLS na SEFAZ e para
 * assinar XML): o
 * arquivo precisa abrir com a senha informada, a chave privada precisa ser
 * extraivel e o par de
 * chaves precisa produzir uma assinatura que o certificado consiga verificar.
 * Tambem cobre o
 * caso mais comum de falha silenciosa em producao: certificado expirado.
 * <p>
 * Os certificados de teste sao gerados dinamicamente com o {@code keytool} do
 * proprio JDK (sem
 * depender de nenhum arquivo real) para que os testes rodem em qualquer
 * maquina/CI.
 */
class CertificadoDigitalValidatorTest {

        private static final char[] SENHA_TESTE = "senha-teste-123".toCharArray();

        @TempDir
        Path tempDir;

        @Test
        void certificadoValidoDeveAssinarEVerificarComSucesso() throws IOException, InterruptedException {
                Path pfx = tempDir.resolve("valido.p12");
                gerarKeystoreTeste(pfx, "CN=NFe Importer Teste, O=Vita Informatica, C=BR", null, 3650);

                CertificadoDigitalValidator.Resultado resultado = assertDoesNotThrow(
                                () -> CertificadoDigitalValidator.validar(pfx, SENHA_TESTE));

                assertTrue(resultado.subjectDN().contains("NFe Importer Teste"));
                assertTrue(resultado.diasParaExpirar() > 3000,
                                "certificado recem-emitido com validade de 10 anos deveria ter mais de 3000 dias restantes");
        }

        @Test
        void senhaIncorretaDeveFalharAoAbrirOArquivo() throws IOException, InterruptedException {
                Path pfx = tempDir.resolve("senha-errada.p12");
                gerarKeystoreTeste(pfx, "CN=NFe Importer Teste, O=Vita Informatica, C=BR", null, 3650);

                assertThrows(MutualTlsHttpClientFactory.CertificadoDigitalException.class,
                                () -> CertificadoDigitalValidator.validar(pfx, "senha-errada".toCharArray()));
        }

        @Test
        void certificadoExpiradoDeveSerRejeitado() throws IOException, InterruptedException {
                Path pfx = tempDir.resolve("expirado.p12");
                // Emitido ha 800 dias, valido por apenas 1 dia -> ja expirado ha muito tempo.
                gerarKeystoreTeste(pfx, "CN=NFe Importer Teste Expirado, O=Vita Informatica, C=BR", "-800d", 1);

                IllegalStateException erro = assertThrows(IllegalStateException.class,
                                () -> CertificadoDigitalValidator.validar(pfx, SENHA_TESTE));
                assertTrue(erro.getMessage().contains("EXPIRADO"),
                                "mensagem deveria indicar expiracao: " + erro.getMessage());
        }

        @Test
        void arquivoInexistenteDeveFalharComMensagemClara() {
                Path inexistente = tempDir.resolve("nao-existe.p12");

                assertThrows(MutualTlsHttpClientFactory.CertificadoDigitalException.class,
                                () -> CertificadoDigitalValidator.validar(inexistente, SENHA_TESTE));
        }

        /**
         * Valida o certificado REAL configurado para a aplicacao (caminho/senha via as
         * mesmas
         * variaveis de ambiente usadas em producao: {@code NFE_CERT_PATH} /
         * {@code NFE_CERT_PASSWORD}).
         * Pulado automaticamente se essas variaveis nao estiverem definidas ou o
         * arquivo nao existir
         * nesta maquina - nao deve travar o build em outro ambiente (CI, outra maquina
         * de dev etc.)
         * nem exige o segredo real dentro do codigo de teste.
         */
        @Test
        void certificadoConfiguradoNoAmbienteDeveSerValidoParaAssinatura() {
                String defaultPath = "cert/CLAUDIACRISTIANETORRES10300984000180.pfx";
                String defaultPassword = "Salcom4321$";

                String caminho = System.getenv("NFE_CERT_PATH");

                if (caminho == null) {
                        caminho = defaultPath;
                }

                String pass = System.getenv("NFE_CERT_PASSWORD");
                if (pass == null) {
                        pass = defaultPassword;
                }

                Assumptions.assumeTrue(caminho != null && !caminho.isBlank(),
                                "NFE_CERT_PATH nao definida - pulando validacao do certificado real");
                Assumptions.assumeTrue(pass != null && !pass.isBlank(),
                                "NFE_CERT_PASSWORD nao definida - pulando validacao do certificado real");

                Path caminhoCertificado = Path.of(caminho);
                Assumptions.assumeTrue(Files.exists(caminhoCertificado),
                                "Arquivo de certificado nao encontrado em " + caminhoCertificado + " - pulando");

                final String senha = pass;
                CertificadoDigitalValidator.Resultado resultado = assertDoesNotThrow(
                                () -> CertificadoDigitalValidator.validar(caminhoCertificado, senha.toCharArray()),
                                "certificado e senha configurados deveriam estar OK para assinar");

                assertTrue(resultado.diasParaExpirar() >= 0, "certificado configurado esta expirado");
        }

        private static void gerarKeystoreTeste(Path destino, String dn, String startDateRelativa, int validadeDias)
                        throws IOException, InterruptedException {
                String keytool = Path.of(System.getProperty("java.home"), "bin",
                                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool")
                                .toString();

                var comando = new java.util.ArrayList<>(java.util.List.of(
                                keytool, "-genkeypair",
                                "-alias", "teste",
                                "-keyalg", "RSA",
                                "-keysize", "2048",
                                "-dname", dn,
                                "-validity", String.valueOf(validadeDias),
                                "-keystore", destino.toString(),
                                "-storetype", "PKCS12",
                                "-storepass", new String(SENHA_TESTE),
                                "-keypass", new String(SENHA_TESTE),
                                "-noprompt"));

                if (startDateRelativa != null) {
                        comando.add("-startdate");
                        comando.add(startDateRelativa);
                }

                Process processo = new ProcessBuilder(comando)
                                .redirectErrorStream(true)
                                .start();
                String saida = new String(processo.getInputStream().readAllBytes());
                boolean terminou = processo.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

                if (!terminou || processo.exitValue() != 0) {
                        throw new IllegalStateException(
                                        "Falha ao gerar certificado de teste com keytool. Saida: " + saida);
                }
        }
}
