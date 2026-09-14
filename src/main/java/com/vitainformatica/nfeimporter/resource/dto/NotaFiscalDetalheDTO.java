package com.vitainformatica.nfeimporter.resource.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import com.vitainformatica.nfeimporter.domain.NotaFiscal;

public record NotaFiscalDetalheDTO(
        Long id,
        String nsu,
        String chaveAcesso,
        String tipoDocumento,
        String schemaOrigem,
        String cnpjEmitente,
        String nomeEmitente,
        String cnpjDestinatario,
        LocalDateTime dataEmissao,
        BigDecimal valorTotal,
        String numeroProtocolo,
        String situacao,
        String ambiente,
        Instant dataImportacao,
        String xml) {

    public static NotaFiscalDetalheDTO de(NotaFiscal n) {
        return new NotaFiscalDetalheDTO(n.id, n.nsu, n.chaveAcesso,
                n.tipoDocumento != null ? n.tipoDocumento.name() : null,
                n.schemaOrigem, n.cnpjEmitente, n.nomeEmitente, n.cnpjDestinatario,
                n.dataEmissao, n.valorTotal, n.numeroProtocolo, n.situacao, n.ambiente,
                n.dataImportacao, n.xml);
    }
}
