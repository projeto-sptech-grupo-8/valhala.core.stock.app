-- A baixa causada pela venda de um drink precisa preservar a receita usada
-- naquele instante, mesmo que a receita seja alterada posteriormente.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'movimentacao_estoque'::regclass
          AND conname = 'ck_movimentacao_saida_drink_receita'
    ) THEN
        ALTER TABLE movimentacao_estoque
            ADD CONSTRAINT ck_movimentacao_saida_drink_receita CHECK (
                tipo <> 'SAIDA_DRINK' OR receita_aplicada IS NOT NULL
            ) NOT VALID;
    END IF;
END $$;
