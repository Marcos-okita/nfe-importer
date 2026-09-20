package com.vitainformatica.nfeimporter.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ManifestacaoEventoRequestBuilderTest {

    private static final String CHAVE = "35240512345678000199550010000001231234567890";

    @Test
    void idEventoDeveTer54CaracteresNoFormatoEsperado() {
        String id = ManifestacaoEventoRequestBuilder.idEvento(CHAVE, 1);

        assertEquals(54, id.length());
        assertEquals("ID210210" + CHAVE + "01", id);
    }

    @Test
    void deveMontarEnvEventoDeCienciaDaOperacaoComOsCamposEsperados() {
        String xml = ManifestacaoEventoRequestBuilder.cienciaOperacao(1, CHAVE, "12345678000199", Instant.now(), 1);

        assertTrue(xml.contains("<envEvento xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.00\">"));
        assertTrue(xml.contains("<idLote>1</idLote>"));
        assertTrue(xml.contains("<cOrgao>91</cOrgao>"));
        assertTrue(xml.contains("<tpAmb>1</tpAmb>"));
        assertTrue(xml.contains("<CNPJ>12345678000199</CNPJ>"));
        assertTrue(xml.contains("<chNFe>" + CHAVE + "</chNFe>"));
        assertTrue(xml.contains("<tpEvento>210210</tpEvento>"));
        assertTrue(xml.contains("<nSeqEvento>1</nSeqEvento>"));
        assertTrue(xml.contains("<descEvento>Ciencia da Operacao</descEvento>"));
        assertTrue(xml.contains("Id=\"" + ManifestacaoEventoRequestBuilder.idEvento(CHAVE, 1) + "\""));
    }

    @Test
    void deveMontarEnvelopeSoap12ComNfeDadosMsgDiretoNoBodySemWrapper() {
        String envEvento = ManifestacaoEventoRequestBuilder.cienciaOperacao(2, CHAVE, "12345678000199", Instant.now(),
                1);
        String envelope = ManifestacaoEventoRequestBuilder.envelopeSoap(envEvento);

        // SOAP 1.2, mesmo padrao usado na Distribuicao de DFe.
        assertTrue(envelope.contains("soap12:Envelope"));
        assertTrue(envelope.contains("<soap12:Body>"));

        // Estilo "document/literal bare" (confirmado no WSDL real: a wsdl:part referencia
        // um elemento global via element=, nao type=) - nfeDadosMsg e filho DIRETO do Body,
        // sem nenhum wrapper <nfeRecepcaoEventoNF> em volta. Ver comentario no builder.
        assertTrue(envelope.contains("<soap12:Body><nfeDadosMsg xmlns=\"" + "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4" + "\">"),
                "nfeDadosMsg deveria ser filho direto do soap12:Body, sem wrapper nfeRecepcaoEventoNF");
        assertTrue(!envelope.contains("nfeRecepcaoEventoNF"),
                "nao deveria haver wrapper com o nome da operacao (estilo document/literal bare)");
        assertTrue(envelope.contains(envEvento));
    }

    @Test
    void soapActionDeveSerConsistenteComOWsdlReal() {
        // Confirmado contra o WSDL real de producao (2026-09-17): a operacao e
        // "nfeRecepcaoEventoNF",
        // nao "nfeRecepcaoEvento".
        assertEquals("http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4/nfeRecepcaoEventoNF",
                ManifestacaoEventoRequestBuilder.soapAction());
    }
}
