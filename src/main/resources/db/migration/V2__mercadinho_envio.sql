-- Rastreamento do encaminhamento de cada NF-e ao mercadinho (POST multipart do XML).
ALTER TABLE nota_fiscal
    ADD COLUMN enviado_mercadinho     TINYINT(1)   NOT NULL DEFAULT 0,
    ADD COLUMN data_envio_mercadinho  DATETIME(6)  NULL,
    ADD COLUMN erro_envio_mercadinho  VARCHAR(500) NULL;

CREATE INDEX idx_nota_fiscal_enviado_mercadinho ON nota_fiscal (enviado_mercadinho);
