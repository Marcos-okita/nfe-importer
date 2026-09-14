-- Tabela de controle do cursor de paginacao (NSU) da Distribuicao de DFe, por CNPJ + ambiente.
CREATE TABLE controle_distribuicao (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    cnpj                    VARCHAR(14)  NOT NULL,
    ambiente                VARCHAR(20)  NOT NULL,
    ultimo_nsu_processado   VARCHAR(20)  NOT NULL DEFAULT '000000000000000',
    ultima_consulta_em      DATETIME(6)  NULL,
    ultimo_cstat            INT          NULL,
    ultimo_xmotivo          VARCHAR(200) NULL,
    CONSTRAINT uk_controle_distribuicao_cnpj_ambiente UNIQUE (cnpj, ambiente)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Documentos fiscais (resumos e/ou completos) importados da Distribuicao de DFe.
CREATE TABLE nota_fiscal (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    nsu                 VARCHAR(20)   NOT NULL,
    chave_acesso        VARCHAR(44)   NULL,
    tipo_documento      VARCHAR(30)   NOT NULL,
    schema_origem       VARCHAR(60)   NULL,
    cnpj_emitente       VARCHAR(14)   NULL,
    nome_emitente       VARCHAR(200)  NULL,
    cnpj_destinatario   VARCHAR(14)   NULL,
    data_emissao        DATETIME(6)   NULL,
    valor_total         DECIMAL(15,2) NULL,
    numero_protocolo    VARCHAR(20)   NULL,
    situacao            VARCHAR(100)  NULL,
    ambiente            VARCHAR(20)   NULL,
    xml                 LONGTEXT      NOT NULL,
    data_importacao     DATETIME(6)   NOT NULL,
    CONSTRAINT uk_nota_fiscal_nsu UNIQUE (nsu)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_nota_fiscal_chave_acesso ON nota_fiscal (chave_acesso);
CREATE INDEX idx_nota_fiscal_cnpj_emitente ON nota_fiscal (cnpj_emitente);
CREATE INDEX idx_nota_fiscal_data_emissao ON nota_fiscal (data_emissao);
CREATE INDEX idx_nota_fiscal_tipo_documento ON nota_fiscal (tipo_documento);
