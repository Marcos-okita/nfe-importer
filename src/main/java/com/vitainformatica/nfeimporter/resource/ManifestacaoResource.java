package com.vitainformatica.nfeimporter.resource;

import com.vitainformatica.nfeimporter.service.ManifestacaoService;
import com.vitainformatica.nfeimporter.service.ManifestacaoService.ResultadoManifestacao;
import com.vitainformatica.nfeimporter.service.ManifestacaoService.ResultadoSincronizacao;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.transaction.Transactional;

import com.vitainformatica.nfeimporter.domain.ManifestacaoDestinatario;

/**
 * Controle manual da Manifestacao do Destinatario ("Ciencia da Operacao"). O envio automatico
 * (apos cada resumo de NF-e novo importado da SEFAZ) e feito por
 * {@link com.vitainformatica.nfeimporter.service.NfeImportService}; estes endpoints servem para
 * reenviar uma chave especifica ou recuperar pendencias.
 */
@Path("/manifestacao")
@Produces(MediaType.APPLICATION_JSON)
public class ManifestacaoResource {

    @Inject
    ManifestacaoService service;

    /** Envia (ou reenvia) a Ciencia da Operacao para uma chave de acesso especifica. */
    @POST
    @Path("/chave/{chaveAcesso}")
    public ResultadoManifestacao enviar(@PathParam("chaveAcesso") String chaveAcesso) {
        return service.enviarPorChave(chaveAcesso);
    }

    /** Reenvia todas as manifestacoes ainda pendentes (ate {@code limite} por vez). */
    @POST
    @Path("/sincronizar")
    public ResultadoSincronizacao sincronizar(@QueryParam("limite") @DefaultValue("100") int limite) {
        return service.sincronizarPendentes(limite);
    }

    /** Consulta o status de manifestacao de uma chave (sem enviar nada). */
    @GET
    @Path("/chave/{chaveAcesso}")
    @Transactional
    public StatusManifestacaoDTO status(@PathParam("chaveAcesso") String chaveAcesso) {
        ManifestacaoDestinatario m = ManifestacaoDestinatario.find("chaveAcesso", chaveAcesso).firstResult();
        if (m == null) {
            throw new NotFoundException("Nenhum controle de manifestacao para a chave " + chaveAcesso);
        }
        return new StatusManifestacaoDTO(m.chaveAcesso, m.enviada, m.dataEnvio, m.protocolo, m.cStat, m.xMotivo, m.tentativas);
    }

    public record StatusManifestacaoDTO(
            String chaveAcesso,
            boolean enviada,
            java.time.Instant dataEnvio,
            String protocolo,
            Integer cStat,
            String xMotivo,
            int tentativas) {
    }
}
