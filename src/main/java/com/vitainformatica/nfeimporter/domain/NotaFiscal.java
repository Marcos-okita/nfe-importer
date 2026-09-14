package com.vitainformatica.nfeimporter.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Um documento fiscal (resumo ou NF-e/evento completo) importado da Distribuicao de DFe da SEFAZ,
 * em que o CNPJ configurado em {@code nfe.cnpj} figura como destinatario.
 */
@Entity
@Table(name = "nota_fiscal", uniqueConstraints = @UniqueConstraint(columnNames = "nsu"))
public class NotaFiscal extends PanacheEntityBase {

    // Usamos PanacheEntityBase + IDENTITY (em vez do PanacheEntity padrao, que gera uma SEQUENCE)
    // porque o schema do Flyway (V1__create_tables.sql) define "id BIGINT AUTO_INCREMENT" - o
    // MariaDB nao tem essa sequencia criada, entao o padrao do Hibernate 6 (SEQUENCE) falharia
    // com "Unknown SEQUENCE: 'nota_fiscal_SEQ'".
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Numero Sequencial Unico do documento no fluxo de distribuicao da SEFAZ. */
    @Column(nullable = false, length = 20)
    public String nsu;

    @Column(name = "chave_acesso", length = 44)
    public String chaveAcesso;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 30)
    public TipoDocumento tipoDocumento;

    @Column(name = "schema_origem", length = 60)
    public String schemaOrigem;

    @Column(name = "cnpj_emitente", length = 14)
    public String cnpjEmitente;

    @Column(name = "nome_emitente", length = 200)
    public String nomeEmitente;

    @Column(name = "cnpj_destinatario", length = 14)
    public String cnpjDestinatario;

    @Column(name = "data_emissao")
    public LocalDateTime dataEmissao;

    @Column(name = "valor_total", precision = 15, scale = 2)
    public BigDecimal valorTotal;

    @Column(name = "numero_protocolo", length = 20)
    public String numeroProtocolo;

    @Column(length = 100)
    public String situacao;

    @Column(length = 20)
    public String ambiente;

    @Lob
    @Column(name = "xml", columnDefinition = "LONGTEXT", nullable = false)
    public String xml;

    @Column(name = "data_importacao", nullable = false)
    public Instant dataImportacao;

    /** Indica se o XML desta nota ja foi encaminhado com sucesso ao mercadinho. */
    @Column(name = "enviado_mercadinho", nullable = false)
    public boolean enviadoMercadinho = false;

    @Column(name = "data_envio_mercadinho")
    public Instant dataEnvioMercadinho;

    /** Mensagem da ultima falha ao tentar enviar ao mercadinho (null se nunca falhou ou ja foi enviada). */
    @Column(name = "erro_envio_mercadinho", length = 500)
    public String erroEnvioMercadinho;

    public static NotaFiscal porChaveAcesso(String chaveAcesso) {
        return find("chaveAcesso", chaveAcesso).firstResult();
    }

    public static boolean existePorNsu(String nsu) {
        return count("nsu", nsu) > 0;
    }

    public static List<NotaFiscal> listarPaginado(int pagina, int tamanhoPagina) {
        return findAll(io.quarkus.panache.common.Sort.by("dataEmissao").descending())
                .page(pagina, tamanhoPagina)
                .list();
    }

    /**
     * Notas ainda nao confirmadas como entregues ao mercadinho. Restrita a NF-e completas: resumos
     * (resNFe) e eventos nao carregam itens/valores, entao nao ha o que enviar para elas ainda.
     */
    public static List<Long> listarIdsPendentesDeEnvioMercadinho(int limite) {
        // Atribuido a uma variavel tipada explicitamente (em vez de encadear direto) porque o
        // javac nao consegue inferir T=NotaFiscal no find(...) generico quando o resultado e
        // encadeado ate um .map(lambda) sem um alvo de tipo explicito no meio do caminho.
        PanacheQuery<NotaFiscal> query = find("enviadoMercadinho = false and tipoDocumento = ?1",
                Sort.by("dataEmissao"), TipoDocumento.NFE_COMPLETA);
        return query.page(0, limite).stream().map(n -> n.id).toList();
    }
}
