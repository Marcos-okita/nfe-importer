package com.vitainformatica.nfeimporter.resource.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.vitainformatica.nfeimporter.domain.NotaFiscal;

public record NotaFiscalResumoDTO(
        Long id,
        String nsu,
        String chaveAcesso,
        String tipoDocumento,
        String cnpjEmitente,
        String nomeEmitente,
        LocalDateTime dataEmissao,
        BigDecimal valorTotal,
        String situacao) {

    public static NotaFiscalResumoDTO de(NotaFiscal n) {
        return new NotaFiscalResumoDTO(n.id, n.nsu, n.chaveAcesso,
                n.tipoDocumento != null ? n.tipoDocumento.name() : null,
                n.cnpjEmitente, n.nomeEmitente, n.dataEmissao, n.valorTotal, n.situacao);
    }
}
