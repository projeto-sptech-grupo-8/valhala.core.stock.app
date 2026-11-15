-- Adiciona suporte à configuração de fardo em produtos padrão.
ALTER TABLE produto
    ADD COLUMN IF NOT EXISTS fardo BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS unidades_por_fardo INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'produto'::regclass AND conname = 'ck_produto_fardo_consistencia'
    ) THEN
        ALTER TABLE produto ADD CONSTRAINT ck_produto_fardo_consistencia
            CHECK ((fardo = FALSE AND unidades_por_fardo IS NULL)
                OR (fardo = TRUE AND unidades_por_fardo >= 2)) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'produto'::regclass AND conname = 'ck_produto_fardo_tipo'
    ) THEN
        ALTER TABLE produto ADD CONSTRAINT ck_produto_fardo_tipo
            CHECK (fardo = FALSE OR tipo = 'PADRAO') NOT VALID;
    END IF;
END $$;
