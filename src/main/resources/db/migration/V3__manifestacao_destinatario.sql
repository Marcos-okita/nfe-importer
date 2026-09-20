-- Controle da Manifestacao do Destinatario (evento "Ciencia da Operacao") por chave de acesso.
-- Necessario porque uma mesma chave pode gerar mais de uma linha em nota_fiscal (resumo e,
-- depois de manifestada, a NF-e completa chegam como NSUs/linhas diferentes) - o controle de
-- manifestacao e por chave, nao por linha de nota_fiscal.
CREATE TABLE manifestacao_destinatario (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    chave_acesso    VARCHAR(44)  NOT NULL,
    cnpj_autor      VARCHAR(14)  NOT NULL,
    enviada         TINYINT(1)   NOT NULL DEFAULT 0,
    data_envio      DATETIME(6)  NULL,
    protocolo       VARCHAR(20)  NULL,
    cstat           INT          NULL,
    xmotivo         VARCHAR(200) NULL,
    tentativas      INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_manifestacao_destinatario_chave UNIQUE (chave_acesso)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_manifestacao_destinatario_enviada ON manifestacao_destinatario (enviada);
