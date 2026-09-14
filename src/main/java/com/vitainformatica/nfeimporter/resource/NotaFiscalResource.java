package com.vitainformatica.nfeimporter.resource;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vitainformatica.nfeimporter.domain.NotaFiscal;
import com.vitainformatica.nfeimporter.domain.TipoDocumento;
import com.vitainformatica.nfeimporter.resource.dto.NotaFiscalDetalheDTO;
import com.vitainformatica.nfeimporter.resource.dto.NotaFiscalResumoDTO;
import com.vitainformatica.nfeimporter.service.ImportResultado;
import com.vitainformatica.nfeimporter.service.NfeImportService;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/notas-fiscais")
@Produces(MediaType.APPLICATION_JSON)
public class NotaFiscalResource {

    @Inject
    NfeImportService importService;

    @GET
    @Transactional
    public List<NotaFiscalResumoDTO> listar(
            @QueryParam("pagina") @DefaultValue("0") int pagina,
            @QueryParam("tamanho") @DefaultValue("20") int tamanho,
            @QueryParam("cnpjEmitente") String cnpjEmitente,
            @QueryParam("tipoDocumento") String tipoDocumento,
            @QueryParam("dataInicio") String dataInicio,
            @QueryParam("dataFim") String dataFim) {

        int tamanhoEfetivo = Math.min(Math.max(tamanho, 1), 200);

        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> params = new HashMap<>();

        if (cnpjEmitente != null && !cnpjEmitente.isBlank()) {
            query.append(" and cnpjEmitente = :cnpjEmitente");
            params.put("cnpjEmitente", cnpjEmitente.trim());
        }
        if (tipoDocumento != null && !tipoDocumento.isBlank()) {
            query.append(" and tipoDocumento = :tipoDocumento");
            params.put("tipoDocumento", TipoDocumento.valueOf(tipoDocumento.trim().toUpperCase()));
        }
        if (dataInicio != null && !dataInicio.isBlank()) {
            query.append(" and dataEmissao >= :dataInicio");
            params.put("dataInicio", LocalDate.parse(dataInicio).atStartOfDay());
        }
        if (dataFim != null && !dataFim.isBlank()) {
            query.append(" and dataEmissao < :dataFim");
            params.put("dataFim", LocalDate.parse(dataFim).plusDays(1).atStartOfDay());
        }

        return NotaFiscal.find(query.toString(), Sort.by("dataEmissao").descending(), params)
                .page(Page.of(pagina, tamanhoEfetivo))
                .<NotaFiscal>list()
                .stream()
                .map(NotaFiscalResumoDTO::de)
                .toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public NotaFiscalDetalheDTO buscarPorId(@PathParam("id") Long id) {
        NotaFiscal nota = NotaFiscal.findById(id);
        if (nota == null) {
            throw new NotFoundException("Nota fiscal " + id + " nao encontrada");
        }
        return NotaFiscalDetalheDTO.de(nota);
    }

    @GET
    @Path("/chave/{chaveAcesso}")
    @Transactional
    public NotaFiscalDetalheDTO buscarPorChave(@PathParam("chaveAcesso") String chaveAcesso) {
        NotaFiscal nota = NotaFiscal.porChaveAcesso(chaveAcesso);
        if (nota == null) {
            throw new NotFoundException("Nota fiscal com chave de acesso " + chaveAcesso + " nao encontrada");
        }
        return NotaFiscalDetalheDTO.de(nota);
    }

    /**
     * Dispara manualmente a importacao. Por padrao respeita o intervalo minimo configurado entre
     * consultas; use {@code forcar=true} apenas para depuracao pontual - chamadas excessivas ao
     * webservice da SEFAZ podem resultar em bloqueio temporario do CNPJ (cStat 656).
     */
    @POST
    @Path("/importar")
    public ImportResultado importar(@QueryParam("forcar") @DefaultValue("false") boolean forcar) {
        return importService.importar(forcar);
    }

    @GET
    @Path("/estatisticas")
    @Transactional
    public Map<String, Object> estatisticas() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalNotas", NotaFiscal.count());
        for (TipoDocumento tipo : TipoDocumento.values()) {
            stats.put("total" + tipo.name(), NotaFiscal.count("tipoDocumento", tipo));
        }
        return stats;
    }
}
