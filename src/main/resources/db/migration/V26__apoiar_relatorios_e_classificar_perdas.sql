-- Apoio aos relatórios gerenciais, perdas classificadas por motivo e permissão de relatórios.

-- ─── 1. Custo unitário atual por produto ───
-- Usado como fallback no CMV quando o item vendido não tem custo gravado (item_pedido.custo_unitario nulo).
-- Produto padrão: o custo cadastrado. Drink: a soma do custo dos ingredientes da receita
-- (ML proporcional ao volume da embalagem; UN pelo custo da unidade).
CREATE OR REPLACE VIEW vw_custo_produto AS
SELECT p.id AS produto_id, p.preco_custo AS custo_unitario
FROM produto p
WHERE p.tipo <> 'DRINK'
UNION ALL
SELECT d.id,
       COALESCE(SUM(
           cd.quantidade * i.preco_custo
           / CASE WHEN cd.unidade_consumo = 'ML' THEN NULLIF(i.volume_embalagem_ml, 0) ELSE 1 END
       ), 0)
FROM produto d
LEFT JOIN composicao_drink cd ON cd.drink_id = d.id
LEFT JOIN produto i ON i.id = cd.produto_id
WHERE d.tipo = 'DRINK'
GROUP BY d.id;

-- ─── 2. Motivo estruturado das perdas ───
-- Antes o relatório só tinha o texto livre do motivo para separar quebra, vencimento e consumo interno.
ALTER TABLE movimentacao_estoque ADD COLUMN IF NOT EXISTS motivo_perda VARCHAR(30);

UPDATE movimentacao_estoque
SET motivo_perda = CASE
        WHEN motivo ILIKE '%vencid%' OR motivo ILIKE '%vencimento%' THEN 'VENCIMENTO'
        WHEN motivo ILIKE '%consumo interno%' THEN 'CONSUMO_INTERNO'
        ELSE 'QUEBRA'
    END
WHERE tipo = 'PERDA' AND motivo_perda IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'movimentacao_estoque'::regclass AND conname = 'ck_movimentacao_estoque_motivo_perda'
    ) THEN
        ALTER TABLE movimentacao_estoque ADD CONSTRAINT ck_movimentacao_estoque_motivo_perda
            CHECK (motivo_perda IN ('QUEBRA', 'VENCIMENTO', 'CONSUMO_INTERNO'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'movimentacao_estoque'::regclass AND conname = 'ck_movimentacao_estoque_perda_classificada'
    ) THEN
        ALTER TABLE movimentacao_estoque ADD CONSTRAINT ck_movimentacao_estoque_perda_classificada
            CHECK ((tipo = 'PERDA') = (motivo_perda IS NOT NULL));
    END IF;
END $$;

-- ─── 3. Permissão de relatórios ───
INSERT INTO funcionalidade (codigo, nome, descricao)
VALUES ('VISUALIZAR_RELATORIOS', 'Visualizar relatórios', 'Consultar relatórios gerenciais de vendas, margem e perdas')
ON CONFLICT (codigo) DO UPDATE
SET nome = EXCLUDED.nome,
    descricao = EXCLUDED.descricao;

INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT perfil.id, funcionalidade.id
FROM perfil
JOIN funcionalidade ON funcionalidade.codigo = 'VISUALIZAR_RELATORIOS'
WHERE UPPER(BTRIM(perfil.nome)) IN ('GERENTE', 'FINANCEIRO')
ON CONFLICT DO NOTHING;
