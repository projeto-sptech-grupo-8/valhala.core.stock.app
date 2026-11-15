DO $$
BEGIN
    IF to_regclass('public.pedido') IS NULL
        OR to_regclass('public.item_pedido') IS NULL
        OR to_regclass('public.pagamento_pedido') IS NULL
        OR to_regclass('public.funcionalidade') IS NULL THEN
        RAISE EXCEPTION
            'As tabelas de pedido, pagamento e funcionalidade são obrigatórias. Aplique as migrations anteriores antes da V18.';
    END IF;
END $$;

INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT perfil.id, funcionalidade.id
FROM perfil
CROSS JOIN funcionalidade
WHERE UPPER(BTRIM(perfil.nome)) IN ('ATENDENTE', 'CAIXA')
  AND funcionalidade.codigo IN (
      'CAIXA_OPERAR',
      'CAIXA_FECHAR',
      'CAIXA_VISUALIZAR',
      'CAIXA_CANCELAR'
  )
ON CONFLICT DO NOTHING;

ALTER TABLE pedido
    ADD COLUMN IF NOT EXISTS tipo_desconto VARCHAR(20),
    ADD COLUMN IF NOT EXISTS valor_desconto_informado NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS status_desconto VARCHAR(30) NOT NULL DEFAULT 'NENHUM',
    ADD COLUMN IF NOT EXISTS desconto_solicitado NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS motivo_desconto TEXT,
    ADD COLUMN IF NOT EXISTS desconto_decidido_por_usuario_id UUID,
    ADD COLUMN IF NOT EXISTS desconto_decidido_em TIMESTAMP,
    ADD COLUMN IF NOT EXISTS observacao_decisao_desconto TEXT,
    ADD COLUMN IF NOT EXISTS motivo_cancelamento TEXT,
    ADD COLUMN IF NOT EXISTS cancelado_por_usuario_id UUID,
    ADD COLUMN IF NOT EXISTS motivo_estorno TEXT,
    ADD COLUMN IF NOT EXISTS estornado_por_usuario_id UUID,
    ADD COLUMN IF NOT EXISTS estornado_em TIMESTAMP;

ALTER TABLE pedido DROP CONSTRAINT IF EXISTS ck_pedido_status;
ALTER TABLE pedido ADD CONSTRAINT ck_pedido_status
    CHECK (
        status IN (
            'EM_ATENDIMENTO',
            'AGUARDANDO_MAQUINA',
            'AGUARDANDO_PAGAMENTO',
            'PAGAMENTO_PARCIAL',
            'CONCLUIDO',
            'CANCELADO',
            'ESTORNADO'
        )
    );

ALTER TABLE pedido DROP CONSTRAINT IF EXISTS ck_pedido_datas_status;
ALTER TABLE pedido ADD CONSTRAINT ck_pedido_datas_status
    CHECK (
        (status <> 'CONCLUIDO' OR concluido_em IS NOT NULL)
        AND (status <> 'CANCELADO' OR cancelado_em IS NOT NULL)
        AND (status <> 'ESTORNADO' OR estornado_em IS NOT NULL)
    );

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pedido'::regclass
          AND conname = 'ck_pedido_tipo_desconto'
    ) THEN
        ALTER TABLE pedido ADD CONSTRAINT ck_pedido_tipo_desconto
            CHECK (tipo_desconto IS NULL OR tipo_desconto IN ('PERCENTUAL', 'VALOR'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pedido'::regclass
          AND conname = 'ck_pedido_status_desconto'
    ) THEN
        ALTER TABLE pedido ADD CONSTRAINT ck_pedido_status_desconto
            CHECK (status_desconto IN ('NENHUM', 'PENDENTE_AUTORIZACAO', 'APROVADO', 'NEGADO'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pedido'::regclass
          AND conname = 'ck_pedido_desconto_solicitado'
    ) THEN
        ALTER TABLE pedido ADD CONSTRAINT ck_pedido_desconto_solicitado
            CHECK (desconto_solicitado IS NULL OR desconto_solicitado >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pedido'::regclass
          AND conname = 'fk_pedido_desconto_decidido_por'
    ) THEN
        ALTER TABLE pedido ADD CONSTRAINT fk_pedido_desconto_decidido_por
            FOREIGN KEY (desconto_decidido_por_usuario_id)
            REFERENCES usuario(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pedido'::regclass
          AND conname = 'fk_pedido_cancelado_por'
    ) THEN
        ALTER TABLE pedido ADD CONSTRAINT fk_pedido_cancelado_por
            FOREIGN KEY (cancelado_por_usuario_id)
            REFERENCES usuario(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pedido'::regclass
          AND conname = 'fk_pedido_estornado_por'
    ) THEN
        ALTER TABLE pedido ADD CONSTRAINT fk_pedido_estornado_por
            FOREIGN KEY (estornado_por_usuario_id)
            REFERENCES usuario(id)
            ON DELETE SET NULL;
    END IF;
END $$;
