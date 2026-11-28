-- Valida todas as constraints CHECK historicamente criadas como NOT VALID,
-- ajusta o escopo de idempotência de pagamento_pedido para (pedido_id, chave_idempotencia)
-- e cria índices parciais para eliminar SeqScans nas consultas Point.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conrelid::regclass AS tabela, c.conname AS nome
        FROM pg_constraint c
        JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE n.nspname = 'public'
          AND c.contype = 'c'
          AND NOT c.convalidated
        ORDER BY c.conrelid::regclass::text, c.conname
    LOOP
        EXECUTE format('ALTER TABLE %s VALIDATE CONSTRAINT %I', r.tabela, r.nome);
    END LOOP;
END $$;

DROP INDEX IF EXISTS uq_pagamento_chave_idempotencia;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pagamento_chave_idempotencia
    ON pagamento_pedido (pedido_id, chave_idempotencia)
    WHERE chave_idempotencia IS NOT NULL
      AND BTRIM(chave_idempotencia) <> '';

CREATE INDEX IF NOT EXISTS idx_pagamento_order_externa_id
    ON pagamento_pedido (order_externa_id)
    WHERE order_externa_id IS NOT NULL
      AND BTRIM(order_externa_id) <> '';

CREATE INDEX IF NOT EXISTS idx_pagamento_terminal_id_status
    ON pagamento_pedido (terminal_id, status)
    WHERE terminal_id IS NOT NULL;
