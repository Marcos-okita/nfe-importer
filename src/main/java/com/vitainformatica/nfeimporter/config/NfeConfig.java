package com.vitainformatica.nfeimporter.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuracao da integracao com o webservice de Distribuicao de DFe da SEFAZ.
 * Ver valores/defaults em {@code application.yml} (prefixo "nfe").
 */
@ConfigMapping(prefix = "nfe")
public interface NfeConfig {

    /** CNPJ (somente digitos) do destinatario/titular do certificado, cujas notas serao buscadas. */
    String cnpj();

    /** Sigla da UF do titular do certificado (ex: "SP", "RJ"). Usada para obter o codigo IBGE (cUFAutor). */
    String uf();

    /** "producao" ou "homologacao". */
    @WithDefault("producao")
    String ambiente();

    Certificado certificado();

    Distribuicao distribuicao();

    Manifestacao manifestacao();

    @WithName("import")
    Import import_();

    interface Certificado {
        /** Caminho absoluto do arquivo .pfx/.p12 (certificado A1). */
        String caminho();

        /** Senha do arquivo PKCS#12. */
        String senha();
    }

    interface Distribuicao {
        @WithName("endpoint-producao")
        String endpointProducao();

        @WithName("endpoint-homologacao")
        String endpointHomologacao();

        @WithName("timeout-segundos")
        @WithDefault("30")
        int timeoutSegundos();
    }

    interface Manifestacao {
        @WithName("endpoint-producao")
        String endpointProducao();

        @WithName("endpoint-homologacao")
        String endpointHomologacao();

        @WithName("timeout-segundos")
        @WithDefault("30")
        int timeoutSegundos();

        /** Se true, registra automaticamente "Ciencia da Operacao" para cada resumo de NF-e novo. */
        @WithName("enviar-automaticamente")
        @WithDefault("true")
        boolean enviarAutomaticamente();
    }

    interface Import {
        String cron();

        @WithName("intervalo-minimo-minutos")
        @WithDefault("60")
        int intervaloMinimoMinutos();

        @WithName("max-paginas-por-execucao")
        @WithDefault("50")
        int maxPaginasPorExecucao();

        /** Se true, dispara uma importacao assim que a aplicacao sobe, alem do agendamento periodico. */
        @WithName("executar-no-startup")
        @WithDefault("true")
        boolean executarNoStartup();
    }
}
