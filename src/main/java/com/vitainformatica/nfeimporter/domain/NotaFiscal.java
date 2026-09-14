package com.vitainformatica.nfeimporter.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
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
}
