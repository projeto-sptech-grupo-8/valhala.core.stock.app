-- Remove código de barras e descrição da tabela de produto por estarem fora de uso.
DROP INDEX IF EXISTS idx_produto_codigo_barras;

ALTER TABLE produto
    DROP COLUMN IF EXISTS codigo_barras,
    DROP COLUMN IF EXISTS descricao;
