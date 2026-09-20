package com.vitainformatica.nfeimporter.sefaz;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.vitainformatica.nfeimporter.sefaz.MutualTlsHttpClientFactory.CertificadoDigitalException;

/**
 * Valida que um certificado digital (.pfx/.p12) e a respectiva senha estao realmente utilizaveis
 * para autenticar/assinar junto a SEFAZ - nao basta o arquivo abrir, a chave privada precisa
 * conseguir gerar uma assinatura valida com o par de chaves do certificado, e o certificado nao
 * pode estar expirado (a causa mais comum de falha "silenciosa" em producao com e-CNPJ).
 */
public final class CertificadoDigitalValidator {

    private static final byte[] DESAFIO = "nfe-importer:teste-de-assinatura".getBytes(StandardCharsets.UTF_8);

    private CertificadoDigitalValidator() {
    }

    public record Resultado(
            String alias,
            String subjectDN,
            LocalDate validoAte,
            long diasParaExpirar) {

        public boolean expiraEmBreve(int diasLimite) {
            return diasParaExpirar <= diasLimite;
        }
    }

    /**
     * @throws CertificadoDigitalException se o arquivo nao existir ou a senha estiver incorreta
     *         (falha ao abrir o keystore)
     * @throws IllegalStateException se o keystore abrir mas a chave privada nao puder ser usada
     *         para assinar (senha da chave divergente, par de chaves incompativel) ou o
     *         certificado estiver fora do periodo de validade
     */
    public static Resultado validar(Path caminhoPfx, char[] senha) {
        CertificadoEntradaLoader.Entrada entrada = CertificadoEntradaLoader.carregar(caminhoPfx, senha);
        X509Certificate certificado = entrada.certificado();

        if (!assinaturaEhValida(entrada.chavePrivada(), certificado)) {
            throw new IllegalStateException(
                    "O par de chaves do certificado nao produziu uma assinatura verificavel - "
                            + "arquivo corrompido ou chave/certificado incompativeis");
        }

        try {
            certificado.checkValidity();
        } catch (CertificateExpiredException e) {
            throw new IllegalStateException(
                    "Certificado EXPIRADO em " + certificado.getNotAfter() + " - solicite a renovacao do e-CNPJ antes "
                            + "de usar esta aplicacao em producao",
                    e);
        } catch (CertificateNotYetValidException e) {
            throw new IllegalStateException(
                    "Certificado ainda nao entrou em vigor (valido a partir de " + certificado.getNotBefore() + ")", e);
        }

        LocalDate validoAte = certificado.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        long diasParaExpirar = ChronoUnit.DAYS.between(LocalDate.now(), validoAte);

        return new Resultado(entrada.alias(), certificado.getSubjectX500Principal().getName(), validoAte, diasParaExpirar);
    }

    private static boolean assinaturaEhValida(PrivateKey chavePrivada, X509Certificate certificado) {
        try {
            String algoritmoAssinatura = algoritmoAssinaturaPara(chavePrivada.getAlgorithm());

            Signature assinador = Signature.getInstance(algoritmoAssinatura);
            assinador.initSign(chavePrivada);
            assinador.update(DESAFIO);
            byte[] assinatura = assinador.sign();

            Signature verificador = Signature.getInstance(algoritmoAssinatura);
            verificador.initVerify(certificado.getPublicKey());
            verificador.update(DESAFIO);
            return verificador.verify(assinatura);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao testar a assinatura com o par de chaves do certificado", e);
        }
    }

    private static String algoritmoAssinaturaPara(String algoritmoChave) {
        return switch (algoritmoChave) {
            case "RSA" -> "SHA256withRSA";
            case "EC" -> "SHA256withECDSA";
            default -> "SHA256with" + algoritmoChave;
        };
    }
}
