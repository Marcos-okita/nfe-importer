package com.vitainformatica.nfeimporter.mercadinho;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MultipartFormDataBuilderTest {

    @Test
    void deveMontarCorpoMultipartComUmArquivo() {
        MultipartFormDataBuilder builder = new MultipartFormDataBuilder();
        byte[] corpo = builder.adicionarArquivo("file", "nfe.xml", "application/xml",
                "<NFe>conteudo</NFe>".getBytes(StandardCharsets.UTF_8))
                .build();

        String texto = new String(corpo, StandardCharsets.UTF_8);
        String boundary = builder.boundary();

        assertTrue(texto.startsWith("--" + boundary + "\r\n"), "deveria comecar com o boundary de abertura");
        assertTrue(texto.contains("Content-Disposition: form-data; name=\"file\"; filename=\"nfe.xml\"\r\n"));
        assertTrue(texto.contains("Content-Type: application/xml\r\n\r\n"));
        assertTrue(texto.contains("<NFe>conteudo</NFe>"));
        assertTrue(texto.endsWith("--" + boundary + "--\r\n"), "deveria terminar com o boundary de fechamento");
    }

    @Test
    void contentTypeDeveIncluirOBoundary() {
        MultipartFormDataBuilder builder = new MultipartFormDataBuilder();
        assertTrue(builder.contentType().equals("multipart/form-data; boundary=" + builder.boundary()));
    }
}
