package com.vitainformatica.nfeimporter.sefaz;

import java.util.List;

/**
 * Resposta do webservice de Distribuicao de DFe ({@code retDistDFeInt}).
 * <p>
 * Codigos de {@code cStat} relevantes:
 * <ul>
 *   <li>137 - Nenhum documento localizado</li>
 *   <li>138 - Documento(s) localizado(s)</li>
 *   <li>656 - Consumo Indevido (excesso de consultas; a SEFAZ pode bloquear temporariamente o CNPJ)</li>
 * </ul>
 */
public record DistDfeResponse(
        int cStat,
        String xMotivo,
        String ultNSU,
        String maxNSU,
        List<DistDfeDocumento> documentos) {

    private static final int NENHUM_DOCUMENTO = 137;
    private static final int DOCUMENTO_LOCALIZADO = 138;
    private static final int CONSUMO_INDEVIDO = 656;

    public boolean semNovosDocumentos() {
        return cStat == NENHUM_DOCUMENTO;
    }

    public boolean sucessoComDocumentos() {
        return cStat == DOCUMENTO_LOCALIZADO;
    }

    public boolean consumoIndevido() {
        return cStat == CONSUMO_INDEVIDO;
    }

    /** Indica se ha mais paginas a buscar (ultNSU ainda nao alcancou maxNSU). */
    public boolean possuiMaisPaginas() {
        try {
            return Long.parseLong(ultNSU) < Long.parseLong(maxNSU);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
