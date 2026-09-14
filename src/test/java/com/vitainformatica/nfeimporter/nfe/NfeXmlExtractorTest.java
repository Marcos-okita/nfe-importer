package com.vitainformatica.nfeimporter.nfe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.vitainformatica.nfeimporter.domain.TipoDocumento;

class NfeXmlExtractorTest {

    @Test
    void deveExtrairDadosDeResumoDeNfe() {
        String xml = """
                <resNFe xmlns="http://www.portalfiscal.inf.br/nfe">
                    <chNFe>35240512345678000199550010000001231234567890</chNFe>
                    <CNPJ>12345678000199</CNPJ>
                    <xNome>Fornecedor Exemplo LTDA</xNome>
                    <IE>123456789</IE>
                    <dhEmi>2024-05-10T14:23:00-03:00</dhEmi>
                    <tpNF>1</tpNF>
                    <vNF>1500.50</vNF>
                    <digVal>abc123==</digVal>
                    <dhRecbto>2024-05-10T14:25:00-03:00</dhRecbto>
                    <nProt>135240000001234</nProt>
                    <cSitNFe>1</cSitNFe>
                </resNFe>
                """;

        DadosDocumentoFiscal dados = NfeXmlExtractor.extrair(xml, TipoDocumento.RESUMO_NFE);

        assertEquals("35240512345678000199550010000001231234567890", dados.chaveAcesso());
        assertEquals("12345678000199", dados.cnpjEmitente());
        assertEquals("Fornecedor Exemplo LTDA", dados.nomeEmitente());
        assertEquals(LocalDateTime.of(2024, 5, 10, 14, 23, 0), dados.dataEmissao());
        assertEquals(new BigDecimal("1500.50"), dados.valorTotal());
        assertEquals("135240000001234", dados.numeroProtocolo());
        assertEquals("Autorizada", dados.situacao());
    }

    @Test
    void deveExtrairDadosDeNfeCompleta() {
        String xml = """
                <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
                    <NFe>
                        <infNFe Id="NFe35240512345678000199550010000001231234567890" versao="4.00">
                            <ide>
                                <dhEmi>2024-05-10T14:23:00-03:00</dhEmi>
                            </ide>
                            <emit>
                                <CNPJ>12345678000199</CNPJ>
                                <xNome>Fornecedor Exemplo LTDA</xNome>
                            </emit>
                            <dest>
                                <CNPJ>99999999000191</CNPJ>
                            </dest>
                            <total>
                                <ICMSTot>
                                    <vNF>2500.00</vNF>
                                </ICMSTot>
                            </total>
                        </infNFe>
                    </NFe>
                    <protNFe>
                        <infProt>
                            <nProt>135240000009999</nProt>
                            <xMotivo>Autorizado o uso da NF-e</xMotivo>
                        </infProt>
                    </protNFe>
                </nfeProc>
                """;

        DadosDocumentoFiscal dados = NfeXmlExtractor.extrair(xml, TipoDocumento.NFE_COMPLETA);

        assertEquals("35240512345678000199550010000001231234567890", dados.chaveAcesso());
        assertEquals("12345678000199", dados.cnpjEmitente());
        assertEquals("Fornecedor Exemplo LTDA", dados.nomeEmitente());
        assertEquals(new BigDecimal("2500.00"), dados.valorTotal());
        assertEquals("135240000009999", dados.numeroProtocolo());
        assertEquals("Autorizado o uso da NF-e", dados.situacao());
    }

    @Test
    void deveClassificarTipoDocumentoPeloSchema() {
        assertEquals(com.vitainformatica.nfeimporter.domain.TipoDocumento.RESUMO_NFE,
                com.vitainformatica.nfeimporter.domain.TipoDocumento.porSchema("resNFe_v1.01.xsd"));
        assertEquals(com.vitainformatica.nfeimporter.domain.TipoDocumento.NFE_COMPLETA,
                com.vitainformatica.nfeimporter.domain.TipoDocumento.porSchema("procNFe_v4.00.xsd"));
        assertEquals(com.vitainformatica.nfeimporter.domain.TipoDocumento.RESUMO_EVENTO,
                com.vitainformatica.nfeimporter.domain.TipoDocumento.porSchema("resEvento_v1.01.xsd"));
        assertEquals(com.vitainformatica.nfeimporter.domain.TipoDocumento.OUTRO,
                com.vitainformatica.nfeimporter.domain.TipoDocumento.porSchema(null));
        assertNotNull(com.vitainformatica.nfeimporter.domain.TipoDocumento.porSchema("algoDesconhecido.xsd"));
    }
}
