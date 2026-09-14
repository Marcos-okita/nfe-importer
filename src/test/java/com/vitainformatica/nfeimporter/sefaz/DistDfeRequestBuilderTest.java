package com.vitainformatica.nfeimporter.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DistDfeRequestBuilderTest {

    @Test
    void deveFormatarNsuCom15Digitos() {
        assertEquals("000000000000000", DistDfeRequestBuilder.formatarNsu("0"));
        assertEquals("000000000000123", DistDfeRequestBuilder.formatarNsu("123"));
        assertEquals("000000000000123", DistDfeRequestBuilder.formatarNsu("000000000000123"));
    }

    @Test
    void deveMontarXmlDeConsultaPorDistNsu() {
        String xml = DistDfeRequestBuilder.distNsu(1, 35, "12345678000199", "42");

        assertTrue(xml.contains("<tpAmb>1</tpAmb>"));
        assertTrue(xml.contains("<cUFAutor>35</cUFAutor>"));
        assertTrue(xml.contains("<CNPJ>12345678000199</CNPJ>"));
        assertTrue(xml.contains("<ultNSU>000000000000042</ultNSU>"));
        assertTrue(xml.contains("xmlns=\"http://www.portalfiscal.inf.br/nfe\""));
    }

    @Test
    void deveMontarEnvelopeSoapComCabecalhoECorpo() {
        String distDfeInt = DistDfeRequestBuilder.distNsu(1, 35, "12345678000199", "0");
        String envelope = DistDfeRequestBuilder.envelopeSoap(distDfeInt, 35);

        assertTrue(envelope.contains("soap12:Envelope"));
        assertTrue(envelope.contains("nfeCabecMsg"));
        assertTrue(envelope.contains("<cUF>35</cUF>"));
        assertTrue(envelope.contains("nfeDistDFeInteresse"));
        assertTrue(envelope.contains("nfeDadosMsg"));
        assertTrue(envelope.contains(distDfeInt));
    }

    @Test
    void soapActionDeveSerConsistenteComONamespaceDoWsdl() {
        assertEquals("http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe/nfeDistDFeInteresse",
                DistDfeRequestBuilder.soapAction());
    }
}
