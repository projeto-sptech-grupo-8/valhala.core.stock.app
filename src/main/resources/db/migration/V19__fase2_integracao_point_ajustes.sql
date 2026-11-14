DO $$
BEGIN
    IF to_regclass('public.pagamento_pedido') IS NULL
        OR to_regclass('public.reserva_estoque') IS NULL
        OR to_regclass('public.evento_webhook_pagamento') IS NULL
        OR to_regclass('public.movimentacao_estoque') IS NULL THEN
        RAISE EXCEPTION
            'As tabelas pagamento_pedido, reserva_estoque, evento_webhook_pagamento e movimentacao_estoque são obrigatórias. Aplique as migrations anteriores antes da V19.';
    END IF;
END $$;

-- Permite tanto os valores canônicos da V11 ('USUARIO', 'SISTEMA', 'WEBHOOK')
-- quanto 'PEDIDO' para baixas síncronas originadas diretamente no fechamento do pedido.
ALTER TABLE movimentacao_estoque
    DROP CONSTRAINT IF EXISTS ck_movimentacao_estoque_origem;

ALTER TABLE movimentacao_estoque
    ADD CONSTRAINT ck_movimentacao_estoque_origem
    CHECK (origem IN ('USUARIO', 'SISTEMA', 'WEBHOOK', 'PEDIDO')) NOT VALID;

-- Índice para o job de reconciliação ativa buscar rapidamente cobranças Point pendentes há mais de N segundos.
CREATE INDEX IF NOT EXISTS idx_pagamento_point_reconciliacao
    ON pagamento_pedido (provedor, status, solicitado_em);
