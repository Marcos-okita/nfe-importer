package com.vitainformatica.nfeimporter.sefaz;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Monta um {@link HttpClient} configurado para autenticacao mutua (mTLS) usando o certificado
 * digital e-CNPJ (A1, arquivo .pfx/.p12) exigido pela SEFAZ para os webservices de NF-e.
 * <p>
 * A confianca no servidor usa o trust store padrao da JVM (cacerts) - os certificados TLS dos
 * servidores da SEFAZ sao emitidos por autoridades certificadoras publicas comuns, entao isso
 * costuma funcionar sem configuracao adicional.
 */
public final class MutualTlsHttpClientFactory {

    private MutualTlsHttpClientFactory() {
    }

    public static HttpClient criar(Path caminhoCertificadoPfx, char[] senha, Duration timeoutConexao) {
        try {
            KeyStore keyStore = carregarKeyStore(caminhoCertificadoPfx, senha);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, senha);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null); // usa o trust store padrao da JVM

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(timeoutConexao)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
        } catch (GeneralSecurityException e) {
            throw new CertificadoDigitalException(
                    "Falha ao carregar o certificado digital em " + caminhoCertificadoPfx
                            + ". Verifique o caminho do arquivo, a senha configurada (nfe.certificado.senha) "
                            + "e se o arquivo e um PKCS#12 (.pfx/.p12) valido.",
                    e);
        }
    }

    /**
     * Carrega o KeyStore PKCS#12 do certificado. Pacote-visivel para reuso por
     * {@link CertificadoDigitalValidator}, que faz uma validacao mais profunda (assinatura,
     * validade) sem precisar montar um HttpClient/SSLContext inteiro.
     */
    static KeyStore carregarKeyStore(Path caminho, char[] senha) {
        if (!Files.isReadable(caminho)) {
            throw new CertificadoDigitalException(
                    "Arquivo de certificado nao encontrado ou sem permissao de leitura: " + caminho, null);
        }
        try (InputStream in = Files.newInputStream(caminho)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(in, senha);
            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw new CertificadoDigitalException(
                    "Falha ao carregar o certificado digital em " + caminho
                            + ". Verifique o caminho do arquivo, a senha configurada (nfe.certificado.senha) "
                            + "e se o arquivo e um PKCS#12 (.pfx/.p12) valido.",
                    e);
        }
    }

    public static class CertificadoDigitalException extends RuntimeException {
        public CertificadoDigitalException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
