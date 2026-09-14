package com.vitainformatica.nfeimporter.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CodigoUfTest {

    @Test
    void deveResolverCodigosConhecidos() {
        assertEquals(35, CodigoUf.deSigla("SP"));
        assertEquals(35, CodigoUf.deSigla("sp"));
        assertEquals(33, CodigoUf.deSigla(" RJ "));
        assertEquals(53, CodigoUf.deSigla("DF"));
    }

    @Test
    void deveLancarExcecaoParaUfInvalida() {
        assertThrows(IllegalArgumentException.class, () -> CodigoUf.deSigla("XX"));
        assertThrows(IllegalArgumentException.class, () -> CodigoUf.deSigla(null));
    }
}
