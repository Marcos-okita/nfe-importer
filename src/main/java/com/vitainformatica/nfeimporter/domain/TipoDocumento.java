package com.vitainformatica.nfeimporter.domain;

/**
 * Classifica o tipo de documento retornado pelo webservice de Distribuicao de DFe,
 * derivado do atributo {@code schema} de cada {@code docZip}.
 */
public enum TipoDocumento {
    /** Resumo da NF-e (resNFe_v*.xsd) - contem apenas os dados principais, sem o XML completo. */
    RESUMO_NFE,
    /** NF-e completa + protocolo de autorizacao (procNFe_v*.xsd). */
    NFE_COMPLETA,
    /** Resumo de um evento associado a uma NF-e (resEvento_v*.xsd), ex: cancelamento. */
    RESUMO_EVENTO,
    /** Evento completo (procEventoNFe_v*.xsd). */
    EVENTO_COMPLETO,
    OUTRO;

    public static TipoDocumento porSchema(String schema) {
        if (schema == null) {
            return OUTRO;
        }
        String s = schema.toLowerCase();
        if (s.startsWith("resnfe")) {
            return RESUMO_NFE;
        }
        if (s.startsWith("procnfe")) {
            return NFE_COMPLETA;
        }
        if (s.startsWith("resevento")) {
            return RESUMO_EVENTO;
        }
        if (s.startsWith("proceventonfe")) {
            return EVENTO_COMPLETO;
        }
        return OUTRO;
    }
}
