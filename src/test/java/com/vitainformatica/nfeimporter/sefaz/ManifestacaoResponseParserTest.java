package com.vitainformatica.nfeimporter.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ManifestacaoResponseParserTest {

    @Test
    void deveInterpretarEventoRegistradoComSucesso() {
        String xml = envelope("""
                <cStat>128</cStat>
                <xMotivo>Lote de Evento Processado</xMotivo>
                <retEvento versao="1.00">
                    <infEvento>
                        <cStat>135</cStat>
                        <xMotivo>Evento registrado e vinculado a NF-e</xMotivo>
                        <nProt>135240000012345</nProt>
                    </infEvento>
                </retEvento>
                """);

        ManifestacaoResponse resposta = ManifestacaoResponseParser.parse(xml);

        assertEquals(128, resposta.cStatLote());
        assertEquals(135, resposta.cStatEvento());
        assertEquals("135240000012345", resposta.protocolo());
        assertTrue(resposta.sucesso());
        assertFalse(resposta.duplicado());
    }

    @Test
    void deveTratarDuplicidadeComoSucesso() {
        String xml = envelope("""
                <cStat>128</cStat>
                <xMotivo>Lote de Evento Processado</xMotivo>
                <retEvento versao="1.00">
                    <infEvento>
                        <cStat>573</cStat>
                        <xMotivo>Duplicidade de Evento</xMotivo>
                    </infEvento>
                </retEvento>
                """);

        ManifestacaoResponse resposta = ManifestacaoResponseParser.parse(xml);

        assertTrue(resposta.sucesso());
        assertTrue(resposta.duplicado());
        assertNull(resposta.protocolo());
    }

    @Test
    void deveTratarRejeicaoDeLoteSemEventoComoFalha() {
        String xml = envelope("""
                <cStat>215</cStat>
                <xMotivo>Rejeicao: Falha no Schema XML</xMotivo>
                """);

        ManifestacaoResponse resposta = ManifestacaoResponseParser.parse(xml);

        assertEquals(215, resposta.cStatLote());
        assertNull(resposta.cStatEvento());
        assertFalse(resposta.sucesso());
    }

    @Test
    void deveLancarExcecaoQuandoRespostaNaoContemRetEnvEvento() {
        String soapInvalido = "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body><algoInesperado/></soap12:Body></soap12:Envelope>";

        org.junit.jupiter.api.Assertions.assertThrows(
                ManifestacaoResponseParser.SefazRespostaInvalidaException.class,
                () -> ManifestacaoResponseParser.parse(soapInvalido));
    }

    private static String envelope(String conteudoRetEnvEvento) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body>"
                + "<nfeRecepcaoEventoResponse xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">"
                + "<nfeRecepcaoEventoResult>"
                + "<retEnvEvento xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\">"
                + conteudoRetEnvEvento
                + "</retEnvEvento>"
                + "</nfeRecepcaoEventoResult>"
                + "</nfeRecepcaoEventoResponse>"
                + "</soap12:Body>"
                + "</soap12:Envelope>";
    }
}
