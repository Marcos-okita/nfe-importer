package com.vitainformatica.nfeimporter.nfe;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Campos relevantes extraidos de um documento (resumo ou completo) recebido da SEFAZ. */
public record DadosDocumentoFiscal(
        String chaveAcesso,
        String cnpjEmitente,
        String nomeEmitente,
        LocalDateTime dataEmissao,
        BigDecimal valorTotal,
        String numeroProtocolo,
        String situacao) {
}
