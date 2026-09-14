package com.vitainformatica.nfeimporter.scheduler;

import com.vitainformatica.nfeimporter.config.NfeConfig;
import com.vitainformatica.nfeimporter.service.ImportResultado;
import com.vitainformatica.nfeimporter.service.NfeImportService;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Dispara a importacao periodica das notas fiscais. A expressao cron e configuravel via
 * {@code nfe.import.cron} (padrao: uma vez por hora) - a propria SEFAZ recomenda nao consultar
 * a Distribuicao de DFe com mais frequencia que essa por CNPJ.
 * <p>
 * Opcionalmente ({@code nfe.import.executar-no-startup}, padrao {@code true}) tambem dispara uma
 * importacao assim que a aplicacao termina de subir, para nao esperar ate a proxima execucao
 * agendada logo apos um deploy/restart. Roda em background (nao atrasa o startup) e continua
 * respeitando o intervalo minimo entre consultas.
 */
@ApplicationScoped
public class NfeImportScheduler {

    @Inject
    NfeImportService importService;

    @Inject
    NfeConfig config;

    void aoIniciar(@Observes StartupEvent evento) {
        if (!config.import_().executarNoStartup()) {
            return;
        }
        // Roda em thread separada para nao atrasar o startup da aplicacao (a consulta a SEFAZ
        // pode levar alguns segundos, ou minutos se houver muitas paginas de backlog).
        Thread thread = new Thread(() -> executarImportacao("inicial (startup)"), "nfe-import-startup");
        thread.setDaemon(true);
        thread.start();
    }

    @Scheduled(cron = "{nfe.import.cron}")
    void executarImportacaoAgendada() {
        executarImportacao("agendada");
    }

    private void executarImportacao(String origem) {
        Log.infof("Iniciando importacao %s de notas fiscais (Distribuicao de DFe)...", origem);
        try {
            ImportResultado resultado = importService.importar(false);
            if (resultado.pulado()) {
                Log.infof("Importacao %s pulada: %s", origem, resultado.motivoPulo());
            } else {
                Log.infof("Importacao %s concluida: %d nota(s) nova(s), %d pagina(s) consultada(s).",
                        origem, resultado.notasNovas(), resultado.paginasConsultadas());
            }
        } catch (Exception e) {
            // Nao propaga: uma falha (ex: SEFAZ fora do ar) nao deve derrubar o agendador nem o
            // startup da aplicacao - a proxima execucao (agendada) tentara novamente.
            Log.errorf(e, "Falha na importacao %s de notas fiscais", origem);
        }
    }
}
