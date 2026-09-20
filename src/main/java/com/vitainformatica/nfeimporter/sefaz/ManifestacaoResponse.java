package com.vitainformatica.nfeimporter.sefaz;

/**
 * Resposta do webservice de Recepcao de Evento ({@code retEnvEvento}) para um unico evento de
 * manifestacao enviado.
 * <p>
 * Codigos de {@code cStat} do evento relevantes:
 * <ul>
 *   <li>135 - Evento registrado e vinculado a NF-e (sucesso)</li>
 *   <li>573 - Duplicidade de Evento (mesmo evento ja havia sido registrado antes - tratamos como
 *       sucesso, pois o efeito desejado - a manifestacao existir - ja esta garantido)</li>
 * </ul>
 */
public record ManifestacaoResponse(
        Integer cStatLote,
        String xMotivoLote,
        Integer cStatEvento,
        String xMotivoEvento,
        String protocolo) {

    private static final int SUCESSO = 135;
    private static final int DUPLICIDADE = 573;

    public boolean sucesso() {
        return cStatEvento != null && (cStatEvento == SUCESSO || cStatEvento == DUPLICIDADE);
    }

    public boolean duplicado() {
        return cStatEvento != null && cStatEvento == DUPLICIDADE;
    }
}
