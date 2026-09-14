package com.vitainformatica.nfeimporter.resource;

import com.vitainformatica.nfeimporter.service.MercadinhoEnvioService;
import com.vitainformatica.nfeimporter.service.MercadinhoEnvioService.ResultadoEnvio;
import com.vitainformatica.nfeimporter.service.MercadinhoEnvioService.ResultadoSincronizacao;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Controle manual do encaminhamento de NF-e ao mercadinho. O encaminhamento automatico (apos cada
 * importacao da SEFAZ) e feito por {@link com.vitainformatica.nfeimporter.service.NfeImportService};
 * estes endpoints servem para reenviar uma nota especifica ou recuperar pendencias (ex: apos uma
 * indisponibilidade do mercadinho).
 */
@Path("/mercadinho")
@Produces(MediaType.APPLICATION_JSON)
public class MercadinhoResource {

    @Inject
    MercadinhoEnvioService envioService;

    /** Envia (ou reenvia) o XML de uma nota fiscal especifica ao mercadinho. */
    @POST
    @Path("/notas-fiscais/{id}")
    public ResultadoEnvio enviar(@PathParam("id") Long id) {
        return envioService.enviar(id);
    }

    /** Reenvia todas as notas ainda nao confirmadas como entregues ao mercadinho (NF-e completas apenas). */
    @POST
    @Path("/sincronizar")
    public ResultadoSincronizacao sincronizar(@QueryParam("limite") @DefaultValue("100") int limite) {
        return envioService.sincronizarPendentes(limite);
    }
}
