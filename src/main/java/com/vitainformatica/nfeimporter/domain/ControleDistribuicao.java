package com.vitainformatica.nfeimporter.domain;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Cursor de paginacao (NSU) da Distribuicao de DFe, por CNPJ + ambiente. A SEFAZ nao possui um
 * conceito de "sessao" - cada chamada informa o ultimo NSU processado, entao esse estado precisa
 * ser persistido entre execucoes do job de importacao.
 */
@Entity
@Table(name = "controle_distribuicao", uniqueConstraints = @UniqueConstraint(columnNames = { "cnpj", "ambiente" }))
public class ControleDistribuicao extends PanacheEntityBase {

    // PanacheEntityBase + IDENTITY (nao o PanacheEntity padrao, que gera uma SEQUENCE) para bater
    // com o "id BIGINT AUTO_INCREMENT" do Flyway - ver comentario equivalente em NotaFiscal.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 14)
    public String cnpj;

    @Column(nullable = false, length = 20)
    public String ambiente;

    @Column(name = "ultimo_nsu_processado", nullable = false, length = 20)
    public String ultimoNsuProcessado = "000000000000000";

    @Column(name = "ultima_consulta_em")
    public Instant ultimaConsultaEm;

    @Column(name = "ultimo_cstat")
    public Integer ultimoCstat;

    @Column(name = "ultimo_xmotivo", length = 200)
    public String ultimoXmotivo;

    public static ControleDistribuicao obterOuCriar(String cnpj, String ambiente) {
        ControleDistribuicao controle = find("cnpj = ?1 and ambiente = ?2", cnpj, ambiente).firstResult();
        if (controle == null) {
            controle = new ControleDistribuicao();
            controle.cnpj = cnpj;
            controle.ambiente = ambiente;
            controle.persist();
        }
        return controle;
    }
}
