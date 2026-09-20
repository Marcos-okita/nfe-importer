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
 * <p>
 * O lote pode ser rejeitado ANTES de qualquer evento ser processado (ex: {@code cStat 225} -
 * "Rejeicao: Falha no Esquema XML do lote de NF-e") - nesse caso a resposta nao traz
 * {@code retEvento}/{@code infEvento} nenhum, entao {@code cStatEvento}/{@code xMotivoEvento}
 * ficam {@code null}. Use {@link #cStatEfetivo()}/{@link #xMotivoEfetivo()} para obter o status
 * relevante em qualquer um dos dois casos, sem precisar checar qual dos dois esta preenchido.
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

    /** cStat do evento, se o lote chegou a ser processado; senao, cStat do proprio lote. */
    public Integer cStatEfetivo() {
        return cStatEvento != null ? cStatEvento : cStatLote;
    }

    /** xMotivo do evento, se disponivel; senao, xMotivo do lote. */
    public String xMotivoEfetivo() {
        return cStatEvento != null ? xMotivoEvento : xMotivoLote;
    }
}
