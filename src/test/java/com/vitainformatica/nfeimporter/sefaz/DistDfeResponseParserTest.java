package com.vitainformatica.nfeimporter.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

class DistDfeResponseParserTest {

    @Test
    void deveInterpretarRespostaSemDocumentos() {
        String soap = envelopeComRetDist("""
                <cStat>137</cStat>
                <xMotivo>Nenhum documento localizado</xMotivo>
                <ultNSU>000000000000010</ultNSU>
                <maxNSU>000000000000010</maxNSU>
                """);

        DistDfeResponse resposta = DistDfeResponseParser.parse(soap);

        assertTrue(resposta.semNovosDocumentos());
        assertFalse(resposta.sucessoComDocumentos());
        assertEquals("000000000000010", resposta.ultNSU());
        assertFalse(resposta.possuiMaisPaginas());
        assertTrue(resposta.documentos().isEmpty());
    }

    @Test
    void deveDescompactarDocumentosDoLote() throws IOException {
        String resNfeXml = "<resNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><chNFe>1234</chNFe></resNFe>";
        String base64Gzip = gzipBase64(resNfeXml);

        String soap = envelopeComRetDist("""
                <cStat>138</cStat>
                <xMotivo>Documento localizado</xMotivo>
                <ultNSU>000000000000011</ultNSU>
                <maxNSU>000000000000020</maxNSU>
                <loteDistDFeInt>
                    <docZip NSU="000000000000011" schema="resNFe_v1.01.xsd">%s</docZip>
                </loteDistDFeInt>
                """.formatted(base64Gzip));

        DistDfeResponse resposta = DistDfeResponseParser.parse(soap);

        assertTrue(resposta.sucessoComDocumentos());
        assertTrue(resposta.possuiMaisPaginas());
        assertEquals(1, resposta.documentos().size());
        DistDfeDocumento doc = resposta.documentos().get(0);
        assertEquals("000000000000011", doc.nsu());
        assertEquals("resNFe_v1.01.xsd", doc.schema());
        assertEquals(resNfeXml, doc.xml());
    }

    @Test
    void deveLancarExcecaoQuandoRespostaNaoContemRetDistDFeInt() {
        String soapInvalido = "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body><algoInesperado/></soap12:Body></soap12:Envelope>";

        org.junit.jupiter.api.Assertions.assertThrows(
                DistDfeResponseParser.SefazRespostaInvalidaException.class,
                () -> DistDfeResponseParser.parse(soapInvalido));
    }

    private static String envelopeComRetDist(String conteudoRetDist) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body>"
                + "<nfeDistDFeInteresseResponse xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe\">"
                + "<nfeDistDFeInteresseResult>"
                + "<retDistDFeInt xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.35\">"
                + conteudoRetDist
                + "</retDistDFeInt>"
                + "</nfeDistDFeInteresseResult>"
                + "</nfeDistDFeInteresseResponse>"
                + "</soap12:Body>"
                + "</soap12:Envelope>";
    }

    private static String gzipBase64(String texto) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(texto.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }
}
