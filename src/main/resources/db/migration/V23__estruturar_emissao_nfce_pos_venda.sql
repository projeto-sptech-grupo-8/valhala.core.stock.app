-- Estrutura a emissão pós-venda de NFC-e (modelo 65) a partir de pedidos concluídos no caixa.

DO $$
BEGIN
    IF to_regclass('public.nota_fiscal') IS NULL
        OR to_regclass('public.item_nota_fiscal') IS NULL
        OR to_regclass('public.pedido') IS NULL
        OR to_regclass('public.sequencia_documento') IS NULL THEN
        RAISE EXCEPTION
            'As tabelas nota_fiscal, item_nota_fiscal, pedido e sequencia_documento são obrigatórias. Aplique as migrations anteriores antes da V23.';
    END IF;
END $$;

-- ============================================================
-- 1. SEQUÊNCIA DE DOCUMENTOS (INCLUSÃO DE NFCE)
-- ============================================================

ALTER TABLE sequencia_documento
    DROP CONSTRAINT IF EXISTS ck_sequencia_documento_tipo;

ALTER TABLE sequencia_documento
    ADD CONSTRAINT ck_sequencia_documento_tipo
        CHECK (tipo IN ('PEDIDO', 'ORCAMENTO', 'NFCE'));

-- ============================================================
-- 2. EVOLUÇÃO DE NOTA_FISCAL E ITEM_NOTA_FISCAL
-- ============================================================

ALTER TABLE nota_fiscal
    ADD COLUMN IF NOT EXISTS modelo VARCHAR(2) NOT NULL DEFAULT '65',
    ADD COLUMN IF NOT EXISTS ambiente VARCHAR(15) NOT NULL DEFAULT 'HOMOLOGACAO',
    ADD COLUMN IF NOT EXISTS provedor VARCHAR(30) NOT NULL DEFAULT 'INTERNO',
    ADD COLUMN IF NOT EXISTS protocolo_autorizacao VARCHAR(50),
    ADD COLUMN IF NOT EXISTS url_consulta VARCHAR(500),
    ADD COLUMN IF NOT EXISTS xml_conteudo TEXT,
    ADD COLUMN IF NOT EXISTS emitida_por_usuario_id UUID,
    ADD COLUMN IF NOT EXISTS cancelada_em TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancelada_por_usuario_id UUID,
    ADD COLUMN IF NOT EXISTS motivo_cancelamento TEXT,
    ADD COLUMN IF NOT EXISTS atualizada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE item_nota_fiscal
    ADD COLUMN IF NOT EXISTS unidade_medida VARCHAR(10) NOT NULL DEFAULT 'UN',
    ADD COLUMN IF NOT EXISTS valor_desconto NUMERIC(12,2) NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'nota_fiscal'::regclass
          AND conname = 'ck_nota_fiscal_modelo'
    ) THEN
        ALTER TABLE nota_fiscal ADD CONSTRAINT ck_nota_fiscal_modelo
            CHECK (modelo IN ('65', '55'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'nota_fiscal'::regclass
          AND conname = 'ck_nota_fiscal_status'
    ) THEN
        ALTER TABLE nota_fiscal ADD CONSTRAINT ck_nota_fiscal_status
            CHECK (status IN ('AUTORIZADA', 'CANCELADA', 'REJEITADA'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'nota_fiscal'::regclass
          AND conname = 'ck_nota_fiscal_ambiente'
    ) THEN
        ALTER TABLE nota_fiscal ADD CONSTRAINT ck_nota_fiscal_ambiente
            CHECK (ambiente IN ('HOMOLOGACAO', 'PRODUCAO'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'nota_fiscal'::regclass
          AND conname = 'ck_nota_fiscal_cancelamento'
    ) THEN
        ALTER TABLE nota_fiscal ADD CONSTRAINT ck_nota_fiscal_cancelamento
            CHECK (
                status <> 'CANCELADA'
                OR (cancelada_em IS NOT NULL AND motivo_cancelamento IS NOT NULL)
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'nota_fiscal'::regclass
          AND conname = 'fk_nota_fiscal_emitida_por'
    ) THEN
        ALTER TABLE nota_fiscal ADD CONSTRAINT fk_nota_fiscal_emitida_por
            FOREIGN KEY (emitida_por_usuario_id)
            REFERENCES usuario(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'nota_fiscal'::regclass
          AND conname = 'fk_nota_fiscal_cancelada_por'
    ) THEN
        ALTER TABLE nota_fiscal ADD CONSTRAINT fk_nota_fiscal_cancelada_por
            FOREIGN KEY (cancelada_por_usuario_id)
            REFERENCES usuario(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'item_nota_fiscal'::regclass
          AND conname = 'chk_item_nota_valor_desconto'
    ) THEN
        ALTER TABLE item_nota_fiscal ADD CONSTRAINT chk_item_nota_valor_desconto
            CHECK (valor_desconto >= 0);
    END IF;
END $$;

-- Garante no máximo uma nota fiscal AUTORIZADA por pedido; se cancelada, o pedido pode receber nova emissão.
CREATE UNIQUE INDEX IF NOT EXISTS uq_nota_fiscal_pedido_autorizada
    ON nota_fiscal(pedido_id)
    WHERE pedido_id IS NOT NULL
      AND status = 'AUTORIZADA';

-- Garante unicidade da numeração fiscal por estabelecimento, modelo e série.
CREATE UNIQUE INDEX IF NOT EXISTS uq_nota_fiscal_estab_modelo_serie_numero
    ON nota_fiscal(estabelecimento_id, modelo, serie, numero)
    WHERE numero IS NOT NULL
      AND serie IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_nota_fiscal_estabelecimento_status
    ON nota_fiscal(estabelecimento_id, status);

CREATE INDEX IF NOT EXISTS idx_nota_fiscal_emitida_em
    ON nota_fiscal(emitida_em DESC);
