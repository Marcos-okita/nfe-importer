package com.vitainformatica.nfeimporter.sefaz;

import java.util.Map;

/**
 * Tabela de codigos IBGE das Unidades Federativas, exigida no campo {@code cUFAutor} do
 * pedido de Distribuicao de DFe. Tabela oficial e estavel (nao muda).
 */
public final class CodigoUf {

    private static final Map<String, Integer> CODIGOS = Map.ofEntries(
            Map.entry("RO", 11), Map.entry("AC", 12), Map.entry("AM", 13), Map.entry("RR", 14),
            Map.entry("PA", 15), Map.entry("AP", 16), Map.entry("TO", 17), Map.entry("MA", 21),
            Map.entry("PI", 22), Map.entry("CE", 23), Map.entry("RN", 24), Map.entry("PB", 25),
            Map.entry("PE", 26), Map.entry("AL", 27), Map.entry("SE", 28), Map.entry("BA", 29),
            Map.entry("MG", 31), Map.entry("ES", 32), Map.entry("RJ", 33), Map.entry("SP", 35),
            Map.entry("PR", 41), Map.entry("SC", 42), Map.entry("RS", 43), Map.entry("MS", 50),
            Map.entry("MT", 51), Map.entry("GO", 52), Map.entry("DF", 53));

    private CodigoUf() {
    }

    public static int deSigla(String uf) {
        if (uf == null || uf.isBlank()) {
            throw new IllegalArgumentException("UF invalida ou nao suportada: " + uf);
        }
        Integer codigo = CODIGOS.get(uf.trim().toUpperCase());
        if (codigo == null) {
            throw new IllegalArgumentException("UF invalida ou nao suportada: " + uf);
        }
        return codigo;
    }
}
