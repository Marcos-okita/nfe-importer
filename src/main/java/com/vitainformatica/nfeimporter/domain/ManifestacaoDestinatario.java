package com.vitainformatica.nfeimporter.domain;

import java.time.Instant;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Controle da Manifestacao do Destinatario (evento "Ciencia da Operacao") por chave de acesso -
 * separado de {@link NotaFiscal} porque uma mesma chave pode aparecer em mais de uma linha (o
 * resumo e, depois de manifestada, a NF-e completa chegam como NSUs diferentes).
 */
@Entity
@Table(name = "manifestacao_destinatario", uniqueConstraints = @UniqueConstraint(columnNames = "chave_acesso"))
public class ManifestacaoDestinatario extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "chave_acesso", nullable = false, length = 44)
    public String chaveAcesso;

    @Column(name = "cnpj_autor", nullable = false, length = 14)
    public String cnpjAutor;

    @Column(nullable = false)
    public boolean enviada = false;

    @Column(name = "data_envio")
    public Instant dataEnvio;

    @Column(length = 20)
    public String protocolo;

    @Column(name = "cstat")
    public Integer cStat;

    @Column(name = "xmotivo", length = 200)
    public String xMotivo;

    @Column(nullable = false)
    public int tentativas = 0;

    public static ManifestacaoDestinatario obterOuCriar(String chaveAcesso, String cnpjAutor) {
        ManifestacaoDestinatario manifestacao = find("chaveAcesso", chaveAcesso).firstResult();
        if (manifestacao == null) {
            manifestacao = new ManifestacaoDestinatario();
            manifestacao.chaveAcesso = chaveAcesso;
            manifestacao.cnpjAutor = cnpjAutor;
            manifestacao.persist();
        }
        return manifestacao;
    }

    public static List<Long> listarIdsPendentes(int limite) {
        PanacheQuery<ManifestacaoDestinatario> query = find("enviada = false", Sort.by("id"));
        return query.page(0, limite).stream().map(m -> m.id).toList();
    }
}
