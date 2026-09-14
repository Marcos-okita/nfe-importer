package com.vitainformatica.nfeimporter.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuracao da integracao com o mercadinho: encaminha o XML de cada NF-e importada via
 * {@code POST <api><caminho-importacao>} multipart/form-data (campo "file"), equivalente a:
 * <pre>
 * curl -X POST "https://mercadinho.vitainformatica.com/api/notas-fiscais/importar/xml" \
 *   -H "Authorization: Bearer TOKEN" \
 *   -F "file=@/caminho/nfe.xml;type=application/xml"
 * </pre>
 * {@code api}/{@code token} sao opcionais (nao {@code String} puro) de proposito: essa integracao
 * e um extra sobre a importacao da SEFAZ, entao a aplicacao precisa continuar subindo mesmo sem
 * elas configuradas - {@link #configurado()} e usado para decidir se ha o que enviar.
 */
@ConfigMapping(prefix = "mercadinho")
public interface MercadinhoConfig {

    /** URL base da API do mercadinho (ex: https://mercadinho.vitainformatica.com/). */
    Optional<String> api();

    /** Token de autenticacao (enviado como "Authorization: Bearer token"). */
    Optional<String> token();

    @WithName("caminho-importacao")
    @WithDefault("/api/notas-fiscais/importar/xml")
    String caminhoImportacao();

    @WithName("timeout-segundos")
    @WithDefault("30")
    int timeoutSegundos();

    /** Se true, encaminha automaticamente cada nota nova assim que ela e importada da SEFAZ. */
    @WithName("enviar-apos-importar")
    @WithDefault("true")
    boolean enviarAposImportar();

    /** True se api e token estiverem ambos preenchidos - ou seja, se ha para onde enviar. */
    default boolean configurado() {
        return api().filter(s -> !s.isBlank()).isPresent() && token().filter(s -> !s.isBlank()).isPresent();
    }
}
