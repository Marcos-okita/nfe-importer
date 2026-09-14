package com.vitainformatica.nfeimporter.service;

/** Resultado de uma execucao (manual ou agendada) do processo de importacao. */
public record ImportResultado(
        boolean pulado,
        String motivoPulo,
        int notasNovas,
        int paginasConsultadas,
        Integer ultimoCstat,
        String ultimoXMotivo,
        String ultimoNsuProcessado) {

    public static ImportResultado pulado(long minutosRestantes) {
        return new ImportResultado(true,
                "Consulta pulada: faltam ~" + minutosRestantes + " min para completar o intervalo minimo configurado "
                        + "entre consultas (protege contra o cStat 656 - Consumo Indevido da SEFAZ)",
                0, 0, null, null, null);
    }

    public static ImportResultado concluido(int notasNovas, int paginas, Integer ultimoCstat, String ultimoXMotivo,
            String ultimoNsu) {
        return new ImportResultado(false, null, notasNovas, paginas, ultimoCstat, ultimoXMotivo, ultimoNsu);
    }
}
