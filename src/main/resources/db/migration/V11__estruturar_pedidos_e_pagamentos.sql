-- Evolução das tabelas comerciais existentes para o PDV:
--   * pedido passa a identificar o canal/atendimento e a sessão de caixa;
--   * pagamento_pedido passa a representar a parcela interna e a tentativa
--     externa (order/payment) com provedor, terminal, idempotência e ciclo de vida.
--
-- A tabela pagamento_pedido continua sendo a fonte interna da parcela.
-- Cada parcela eletrônica aponta para uma única order do Mercado Pago.

DO $$
BEGIN
    IF to_regclass('public.pedido') IS NULL
        OR to_regclass('public.orcamento') IS NULL
        OR to_regclass('public.pagamento_pedido') IS NULL
        OR to_regclass('public.sessao_caixa') IS NULL THEN
        RAISE EXCEPTION
            'As tabelas pedido, orcamento, pagamento_pedido e sessao_caixa são obrigatórias. Execute as migrations anteriores antes desta.';
    END IF;
END $$;

-- ============================================================
-- SEQUÊNCIAS DE DOCUMENTOS
-- ============================================================

-- A numeração de pedidos e orçamentos é por estabelecimento. O UPDATE com
-- RETURNING nesta tabela serializa a geração concorrente sem depender de
-- MAX(numero) + 1, que sofre corrida entre atendentes.
CREATE TABLE IF NOT EXISTS sequencia_documento (
    estabelecimento_id UUID NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    ultimo_numero BIGINT NOT NULL DEFAULT 0,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_sequencia_documento
        PRIMARY KEY (estabelecimento_id, tipo),

    CONSTRAINT ck_sequencia_documento_tipo
        CHECK (tipo IN ('PEDIDO', 'ORCAMENTO')),

    CONSTRAINT ck_sequencia_documento_ultimo
        CHECK (ultimo_numero >= 0),

    CONSTRAINT fk_sequencia_documento_estabelecimento
        FOREIGN KEY (estabelecimento_id)
        REFERENCES estabelecimento(id)
);

-- Preserva a numeração já existente em bancos que tenham pedidos ou orçamentos.
INSERT INTO sequencia_documento (estabelecimento_id, tipo, ultimo_numero)
SELECT estabelecimento_id, 'PEDIDO', MAX(numero)
FROM pedido
GROUP BY estabelecimento_id
ON CONFLICT (estabelecimento_id, tipo) DO UPDATE
SET ultimo_numero = GREATEST(sequencia_documento.ultimo_numero, EXCLUDED.ultimo_numero),
    atualizado_em = CURRENT_TIMESTAMP;

INSERT INTO sequencia_documento (estabelecimento_id, tipo, ultimo_numero)
SELECT estabelecimento_id, 'ORCAMENTO', MAX(numero)
FROM orcamento
GROUP BY estabelecimento_id
ON CONFLICT (estabelecimento_id, tipo) DO UPDATE
SET ultimo_numero = GREATEST(sequencia_documento.ultimo_numero, EXCLUDED.ultimo_numero),
    atualizado_em = CURRENT_TIMESTAMP;

-- ============================================================
-- PEDIDO
-- ============================================================

ALTER TABLE pedido
    ADD COLUMN IF NOT EXISTS canal VARCHAR(30) NOT NULL DEFAULT 'BALCAO',
    ADD COLUMN IF NOT EXISTS identificador_atendimento VARCHAR(60),
    ADD COLUMN IF NOT EXISTS sessao_caixa_id UUID,
    ADD COLUMN IF NOT EXISTS atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE pedido
    ALTER COLUMN status SET DEFAULT 'EM_ATENDIMENTO';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pedido'::regclass
          AND conname = 'ck_pedido_canal'
    ) THEN
        ALTER TABLE pedido ADD CONSTRAINT ck_pedido_canal
            CHECK (canal IN ('BALCAO', 'MESA', 'RETIRADA')) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pedido'::regclass
          AND conname = 'ck_pedido_status'
    ) THEN
        ALTER TABLE pedido ADD CONSTRAINT ck_pedido_status
            CHECK (
                status IN (
                    'EM_ATENDIMENTO',
                    'AGUARDANDO_MAQUINA',
                    'AGUARDANDO_PAGAMENTO',
                    'PAGAMENTO_PARCIAL',
                    'CONCLUIDO',
                    'CANCELADO'
                )
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pedido'::regclass
          AND conname = 'ck_pedido_canal_mesa'
    ) THEN
        ALTER TABLE pedido ADD CONSTRAINT ck_pedido_canal_mesa
            CHECK (canal <> 'MESA' OR identificador_atendimento IS NOT NULL) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pedido'::regclass
          AND conname = 'ck_pedido_datas_status'
    ) THEN
        ALTER TABLE pedido ADD CONSTRAINT ck_pedido_datas_status
            CHECK (
                (status <> 'CONCLUIDO' OR concluido_em IS NOT NULL)
                AND (status <> 'CANCELADO' OR cancelado_em IS NOT NULL)
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pedido'::regclass
          AND conname = 'fk_pedido_sessao_caixa'
    ) THEN
        ALTER TABLE pedido ADD CONSTRAINT fk_pedido_sessao_caixa
            FOREIGN KEY (sessao_caixa_id, estabelecimento_id)
            REFERENCES sessao_caixa(id, estabelecimento_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pedido_sessao_caixa
    ON pedido(sessao_caixa_id);

CREATE INDEX IF NOT EXISTS idx_pedido_estabelecimento_status
    ON pedido(estabelecimento_id, status);

CREATE INDEX IF NOT EXISTS idx_pedido_criado_em
    ON pedido(criado_em DESC);

-- Base da tela de pedidos simultâneos: somente pedidos ainda editáveis ou
-- aguardando pagamento entram neste índice parcial.
CREATE INDEX IF NOT EXISTS idx_pedido_em_atendimento
    ON pedido(estabelecimento_id, atualizado_em DESC)
    WHERE status IN (
        'EM_ATENDIMENTO',
        'AGUARDANDO_MAQUINA',
        'AGUARDANDO_PAGAMENTO',
        'PAGAMENTO_PARCIAL'
    );

COMMENT ON COLUMN pedido.canal IS
    'Canal do atendimento: BALCAO, MESA ou RETIRADA';

COMMENT ON COLUMN pedido.identificador_atendimento IS
    'Rótulo livre para distinguir pedidos simultâneos, como "Mesa 3" ou "Balcão 2"';

COMMENT ON COLUMN pedido.sessao_caixa_id IS
    'Sessão em que o pedido foi aberto; o recebimento pode ocorrer em outra sessão';

COMMENT ON COLUMN pedido.atualizado_em IS
    'Última alteração do pedido, usada para ordenar os atendimentos em aberto';

-- ============================================================
-- PAGAMENTO
-- ============================================================

ALTER TABLE pagamento_pedido
    ADD COLUMN IF NOT EXISTS sessao_caixa_id UUID,
    ADD COLUMN IF NOT EXISTS provedor VARCHAR(30) NOT NULL DEFAULT 'INTERNO',
    ADD COLUMN IF NOT EXISTS metodo_pagamento VARCHAR(40),
    ADD COLUMN IF NOT EXISTS order_externa_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS payment_externo_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS chave_idempotencia VARCHAR(100),
    ADD COLUMN IF NOT EXISTS status_detalhe VARCHAR(100),
    ADD COLUMN IF NOT EXISTS parcelas INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS politica_juros VARCHAR(30),
    ADD COLUMN IF NOT EXISTS valor_cobrado NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS valor_recebido NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS valor_troco NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS valor_estornado NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS comprovante_referencia VARCHAR(255),
    ADD COLUMN IF NOT EXISTS comprovante_dados JSONB,
    ADD COLUMN IF NOT EXISTS motivo_cancelamento TEXT,
    ADD COLUMN IF NOT EXISTS motivo_estorno TEXT,
    ADD COLUMN IF NOT EXISTS solicitado_em TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancelado_em TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estornado_em TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancelado_por_usuario_id UUID,
    ADD COLUMN IF NOT EXISTS estornado_por_usuario_id UUID,
    ADD COLUMN IF NOT EXISTS atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE pagamento_pedido
    ALTER COLUMN status SET DEFAULT 'PENDENTE';

-- Referências externas antigas indicam integração com o Mercado Pago.
-- O ajuste evita classificar esses registros como pagamento interno.
UPDATE pagamento_pedido
SET provedor = 'MERCADO_PAGO'
WHERE provedor = 'INTERNO'
  AND referencia_externa IS NOT NULL
  AND BTRIM(referencia_externa) <> '';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_forma'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_forma
            CHECK (forma IN ('DINHEIRO', 'DEBITO', 'CREDITO', 'PIX')) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_status'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_status
            CHECK (
                status IN (
                    'PENDENTE',
                    'PENDENTE_NA_POINT',
                    'EM_TERMINAL',
                    'APROVADO',
                    'RECUSADO',
                    'EXPIRADO',
                    'CANCELAMENTO_SOLICITADO',
                    'CANCELADO',
                    'ACAO_NECESSARIA',
                    'PENDENTE_CONCILIACAO',
                    'ESTORNO_EM_ANDAMENTO',
                    'ESTORNADO'
                )
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_provedor'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_provedor
            CHECK (provedor IN ('MERCADO_PAGO', 'INTERNO', 'MANUAL')) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_politica_juros'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_politica_juros
            CHECK (
                politica_juros IS NULL
                OR politica_juros IN ('ABSORVIDA_PELA_LOJA', 'REPASSADA_AO_CLIENTE')
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_parcelas'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_parcelas
            CHECK (parcelas >= 1) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_valor_cobrado'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_valor_cobrado
            CHECK (valor_cobrado IS NULL OR valor_cobrado >= valor) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_valor_recebido'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_valor_recebido
            CHECK (valor_recebido IS NULL OR valor_recebido >= valor) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_troco'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_troco
            CHECK (
                (valor_recebido IS NULL AND valor_troco = 0)
                OR (
                    valor_recebido IS NOT NULL
                    AND valor_troco = valor_recebido - valor
                    AND valor_troco >= 0
                )
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_dinheiro_valores'
    ) THEN
        -- Valor recebido e troco são exclusivos do dinheiro.
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_dinheiro_valores
            CHECK (
                forma = 'DINHEIRO'
                OR (valor_recebido IS NULL AND valor_troco = 0)
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_dinheiro_cobrado'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_dinheiro_cobrado
            CHECK (forma <> 'DINHEIRO' OR valor_cobrado IS NULL) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_dinheiro_aprovado'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_dinheiro_aprovado
            CHECK (
                status <> 'APROVADO'
                OR forma <> 'DINHEIRO'
                OR valor_recebido IS NOT NULL
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_valor_estornado'
    ) THEN
        -- O estorno pode devolver o valor cobrado do cliente, inclusive juros
        -- repassados. Em dinheiro/manual, o limite continua sendo o valor da venda.
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_valor_estornado
            CHECK (
                valor_estornado >= 0
                AND valor_estornado <= COALESCE(valor_cobrado, valor)
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_estornado_status'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_estornado_status
            CHECK (status <> 'ESTORNADO' OR valor_estornado > 0) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_forma_parcelas'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_forma_parcelas
            CHECK (forma = 'CREDITO' OR parcelas = 1) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_forma_provedor'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_forma_provedor
            CHECK (
                (forma = 'DINHEIRO' AND provedor IN ('INTERNO', 'MANUAL'))
                OR (
                    forma IN ('DEBITO', 'CREDITO', 'PIX')
                    AND provedor IN ('MERCADO_PAGO', 'MANUAL')
                )
            ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_aprovado_pago_em'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_aprovado_pago_em
            CHECK (status <> 'APROVADO' OR pago_em IS NOT NULL) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_cancelado_em'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_cancelado_em
            CHECK (status <> 'CANCELADO' OR cancelado_em IS NOT NULL) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_estornado_em'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_estornado_em
            CHECK (status <> 'ESTORNADO' OR estornado_em IS NOT NULL) NOT VALID;
    END IF;

    -- Dinheiro só pode ser registrado dentro de uma sessão de caixa.
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'ck_pagamento_dinheiro_sessao'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT ck_pagamento_dinheiro_sessao
            CHECK (forma <> 'DINHEIRO' OR sessao_caixa_id IS NOT NULL) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'fk_pagamento_sessao_caixa'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT fk_pagamento_sessao_caixa
            FOREIGN KEY (sessao_caixa_id)
            REFERENCES sessao_caixa(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'fk_pagamento_cancelado_por'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT fk_pagamento_cancelado_por
            FOREIGN KEY (cancelado_por_usuario_id)
            REFERENCES usuario(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pagamento_pedido'::regclass
          AND conname = 'fk_pagamento_estornado_por'
    ) THEN
        ALTER TABLE pagamento_pedido ADD CONSTRAINT fk_pagamento_estornado_por
            FOREIGN KEY (estornado_por_usuario_id)
            REFERENCES usuario(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- Referência interna enviada ao Mercado Pago. O limite da API é 64 caracteres,
-- mas a coluna mantém 150 por compatibilidade com o esboço original.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pagamento_referencia_externa
    ON pagamento_pedido(referencia_externa)
    WHERE referencia_externa IS NOT NULL
      AND BTRIM(referencia_externa) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_pagamento_chave_idempotencia
    ON pagamento_pedido(chave_idempotencia)
    WHERE chave_idempotencia IS NOT NULL
      AND BTRIM(chave_idempotencia) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_pagamento_order_externa
    ON pagamento_pedido(provedor, order_externa_id)
    WHERE order_externa_id IS NOT NULL
      AND BTRIM(order_externa_id) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_pagamento_payment_externo
    ON pagamento_pedido(provedor, payment_externo_id)
    WHERE payment_externo_id IS NOT NULL
      AND BTRIM(payment_externo_id) <> '';

-- Uma única cobrança eletrônica ativa por terminal. Estados de conciliação
-- também bloqueiam o terminal até que a order seja resolvida.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pagamento_point_ativo_terminal
    ON pagamento_pedido(terminal_id)
    WHERE terminal_id IS NOT NULL
      AND status IN (
          'PENDENTE_NA_POINT',
          'EM_TERMINAL',
          'CANCELAMENTO_SOLICITADO',
          'ACAO_NECESSARIA',
          'PENDENTE_CONCILIACAO'
      );

CREATE INDEX IF NOT EXISTS idx_pagamento_sessao_caixa
    ON pagamento_pedido(sessao_caixa_id);

CREATE INDEX IF NOT EXISTS idx_pagamento_status
    ON pagamento_pedido(status);

CREATE INDEX IF NOT EXISTS idx_pagamento_pedido_status
    ON pagamento_pedido(pedido_id, status);

COMMENT ON COLUMN pagamento_pedido.sessao_caixa_id IS
    'Sessão que recebeu efetivamente o pagamento; pode diferir da sessão de abertura do pedido';

COMMENT ON COLUMN pagamento_pedido.provedor IS
    'MERCADO_PAGO para cobranças na Point, INTERNO para dinheiro e MANUAL para contingência autorizada';

COMMENT ON COLUMN pagamento_pedido.valor IS
    'Valor aplicado à venda, já líquido de troco';

COMMENT ON COLUMN pagamento_pedido.valor_cobrado IS
    'Valor bruto enviado ao provedor; pode ser maior que valor quando os juros são repassados ao cliente';

COMMENT ON COLUMN pagamento_pedido.valor_recebido IS
    'Valor físico recebido em dinheiro; nulo enquanto o pagamento não foi confirmado';

COMMENT ON COLUMN pagamento_pedido.valor_troco IS
    'Troco entregue ao cliente; sempre zero para pagamentos eletrônicos';

COMMENT ON COLUMN pagamento_pedido.referencia_externa IS
    'external_reference da order do Mercado Pago, única por tentativa';

COMMENT ON COLUMN pagamento_pedido.chave_idempotencia IS
    'X-Idempotency-Key persistida para repetição segura da operação externa';

COMMENT ON COLUMN pagamento_pedido.order_externa_id IS
    'Identificador da order no Mercado Pago';

COMMENT ON COLUMN pagamento_pedido.payment_externo_id IS
    'Identificador da transação/pagamento no provedor';

COMMENT ON COLUMN pagamento_pedido.terminal_id IS
    'Terminal Point que processou a cobrança; snapshot no momento da order';

COMMENT ON COLUMN pagamento_pedido.pago_em IS
    'Momento em que o pagamento foi aprovado ou confirmado';
