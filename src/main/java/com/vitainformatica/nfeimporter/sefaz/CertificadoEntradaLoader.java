package com.vitainformatica.nfeimporter.sefaz;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Localiza, dentro de um KeyStore PKCS#12, a entrada com chave privada (alias + chave + cadeia de
 * certificados X.509) - usado tanto pelo {@link CertificadoDigitalValidator} (teste de
 * assinatura/validade) quanto pelo {@link XmlAssinaturaService} (assinatura real de eventos, ex:
 * manifestacao do destinatario).
 */
final class CertificadoEntradaLoader {

    private CertificadoEntradaLoader() {
    }

    /**
     * @param certificado    certificado folha (o do titular) - {@code cadeiaCompleta.get(0)}
     * @param cadeiaCompleta cadeia inteira armazenada no .pfx (folha + AC(s) intermediaria(s) +
     *                       raiz, na ordem em que aparecem no arquivo). Necessaria para assinatura
     *                       XML: alguns validadores tentam montar a cadeia de confianca e falham
     *                       (com erros pouco claros, ex: NullReferenceException do lado da SEFAZ)
     *                       se so o certificado folha estiver embutido no KeyInfo.
     */
    record Entrada(String alias, PrivateKey chavePrivada, X509Certificate certificado, List<X509Certificate> cadeiaCompleta) {
    }

    /** Carrega o arquivo e ja extrai a entrada com chave privada, pronta para assinar/autenticar. */
    static Entrada carregar(Path caminhoPfx, char[] senha) {
        KeyStore keyStore = MutualTlsHttpClientFactory.carregarKeyStore(caminhoPfx, senha);
        return carregarDoKeyStore(keyStore, senha);
    }

    static Entrada carregarDoKeyStore(KeyStore keyStore, char[] senha) {
        String alias = localizarAliasComChavePrivada(keyStore);

        try {
            PrivateKey chavePrivada = (PrivateKey) keyStore.getKey(alias, senha);
            if (chavePrivada == null) {
                throw new IllegalStateException(
                        "O alias '" + alias + "' nao possui chave privada acessivel com a senha informada");
            }

            Certificate[] cadeiaBruta = keyStore.getCertificateChain(alias);
            if (cadeiaBruta == null || cadeiaBruta.length == 0) {
                throw new IllegalStateException("A entrada '" + alias + "' nao possui cadeia de certificados associada");
            }

            List<X509Certificate> cadeia = new ArrayList<>(cadeiaBruta.length);
            for (Certificate certificadoBruto : cadeiaBruta) {
                if (!(certificadoBruto instanceof X509Certificate x509)) {
                    throw new IllegalStateException("A entrada '" + alias + "' do certificado nao e um certificado X.509");
                }
                cadeia.add(x509);
            }

            return new Entrada(alias, chavePrivada, cadeia.get(0), List.copyOf(cadeia));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Nao foi possivel acessar a chave privada do alias '" + alias + "' - a senha do certificado "
                            + "(nfe.certificado.senha) pode estar incorreta",
                    e);
        }
    }

    private static String localizarAliasComChavePrivada(KeyStore keyStore) {
        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) {
                    return alias;
                }
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao listar as entradas do certificado", e);
        }
        throw new IllegalStateException("Nenhuma entrada de chave privada encontrada no arquivo PKCS#12");
    }
}
