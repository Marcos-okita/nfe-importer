package com.vitainformatica.nfeimporter.mercadinho;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Monta manualmente um corpo {@code multipart/form-data} com um unico campo de arquivo -
 * equivalente ao {@code curl -F "file=@arquivo;type=..."}. O {@code java.net.http.HttpClient} do
 * JDK nao tem suporte nativo a multipart, entao construimos o corpo byte a byte seguindo a
 * RFC 7578.
 */
final class MultipartFormDataBuilder {

    private final String boundary = "----NfeImporterBoundary" + UUID.randomUUID();
    private final ByteArrayOutputStream corpo = new ByteArrayOutputStream();

    String boundary() {
        return boundary;
    }

    String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    MultipartFormDataBuilder adicionarArquivo(String nomeCampo, String nomeArquivo, String contentType, byte[] conteudo) {
        try {
            corpo.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            corpo.write(("Content-Disposition: form-data; name=\"" + nomeCampo + "\"; filename=\"" + nomeArquivo + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            corpo.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            corpo.write(conteudo);
            corpo.write("\r\n".getBytes(StandardCharsets.UTF_8));
            return this;
        } catch (IOException e) {
            // ByteArrayOutputStream nunca lanca IOException de verdade - mantido so para satisfazer a assinatura.
            throw new UncheckedIOException(e);
        }
    }

    byte[] build() {
        try {
            corpo.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return corpo.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
