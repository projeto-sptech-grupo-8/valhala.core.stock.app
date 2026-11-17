-- Vendas registradas pela API (pedido, itens e pagamentos), perdas classificadas por motivo
-- e permissões dos módulos de vendas e relatórios.

-- ─── 1. Custo unitário no momento da venda ───
-- O CMV dos relatórios passa a usar o custo gravado na venda, e não o custo atual do produto.
-- Itens já existentes recebem o custo atual, que é a melhor informação disponível.
ALTER TABLE item_pedido ADD COLUMN IF NOT EXISTS custo_unitario NUMERIC(12,4);

UPDATE item_pedido i
SET custo_unitario = COALESCE(
        (SELECT cp.custo_unitario FROM vw_custo_produto cp WHERE cp.produto_id = i.produto_id), 0)
WHERE i.custo_unitario IS NULL;

ALTER TABLE item_pedido ALTER COLUMN custo_unitario SET DEFAULT 0;
ALTER TABLE item_pedido ALTER COLUMN custo_unitario SET NOT NULL;
ALTER TABLE item_pedido ADD CONSTRAINT chk_item_pedido_custo_unitario CHECK (custo_unitario >= 0);

-- ─── 2. Domínios de pedido e pagamento ───
-- NOT VALID: as regras valem para novos registros sem bloquear a migração por dados antigos.
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS motivo_cancelamento VARCHAR(255);

ALTER TABLE pedido ADD CONSTRAINT chk_pedido_tipo
    CHECK (tipo IN ('BALCAO', 'MESA', 'DELIVERY')) NOT VALID;
ALTER TABLE pedido ADD CONSTRAINT chk_pedido_status
    CHECK (status IN ('CONCLUIDO', 'CANCELADO')) NOT VALID;
ALTER TABLE pedido ADD CONSTRAINT chk_pedido_concluido_em
    CHECK (status <> 'CONCLUIDO' OR concluido_em IS NOT NULL) NOT VALID;
ALTER TABLE pedido ADD CONSTRAINT chk_pedido_cancelado_em
    CHECK (status <> 'CANCELADO' OR cancelado_em IS NOT NULL) NOT VALID;

ALTER TABLE pagamento_pedido ADD CONSTRAINT chk_pagamento_forma
    CHECK (forma IN ('PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'DINHEIRO')) NOT VALID;
ALTER TABLE pagamento_pedido ADD CONSTRAINT chk_pagamento_status
    CHECK (status IN ('PAGO', 'ESTORNADO')) NOT VALID;

-- Estorno do cancelamento e rastreio da baixa de cada item vendido.
CREATE INDEX IF NOT EXISTS idx_movimentacao_item_pedido
    ON movimentacao_estoque (item_pedido_id)
    WHERE item_pedido_id IS NOT NULL;

-- ─── 3. Motivo estruturado das perdas ───
-- Antes o relatório classificava pelo texto livre do motivo e contava ajustes negativos como perda.
ALTER TABLE movimentacao_estoque ADD COLUMN IF NOT EXISTS motivo_perda VARCHAR(30);

UPDATE movimentacao_estoque
SET motivo_perda = CASE
        WHEN motivo ILIKE '%vencid%' OR motivo ILIKE '%vencimento%' THEN 'VENCIMENTO'
        WHEN motivo ILIKE '%consumo interno%' THEN 'CONSUMO_INTERNO'
        ELSE 'QUEBRA'
    END
WHERE tipo = 'PERDA' AND motivo_perda IS NULL;

ALTER TABLE movimentacao_estoque ADD CONSTRAINT chk_movimentacao_motivo_perda
    CHECK (motivo_perda IN ('QUEBRA', 'VENCIMENTO', 'CONSUMO_INTERNO'));
ALTER TABLE movimentacao_estoque ADD CONSTRAINT chk_movimentacao_perda_classificada
    CHECK ((tipo = 'PERDA') = (motivo_perda IS NOT NULL));

-- ─── 4. Permissões ───
INSERT INTO funcionalidade (codigo, nome, descricao)
VALUES
    ('REGISTRAR_VENDA', 'Registrar venda', 'Finalizar vendas no caixa, com baixa automática do estoque'),
    ('CANCELAR_VENDA', 'Cancelar venda', 'Cancelar vendas concluídas, devolvendo os itens ao estoque'),
    ('VISUALIZAR_RELATORIOS', 'Visualizar relatórios', 'Consultar relatórios gerenciais de vendas, margem e perdas')
ON CONFLICT (codigo) DO UPDATE
SET nome = EXCLUDED.nome,
    descricao = EXCLUDED.descricao;

INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT perfil.id, funcionalidade.id
FROM perfil
JOIN funcionalidade ON (
        UPPER(BTRIM(perfil.nome)) = 'GERENTE'
        AND funcionalidade.codigo IN ('REGISTRAR_VENDA', 'CANCELAR_VENDA', 'VISUALIZAR_RELATORIOS')
    ) OR (
        UPPER(BTRIM(perfil.nome)) = 'ATENDENTE'
        AND funcionalidade.codigo = 'REGISTRAR_VENDA'
    ) OR (
        UPPER(BTRIM(perfil.nome)) = 'FINANCEIRO'
        AND funcionalidade.codigo = 'VISUALIZAR_RELATORIOS'
    )
ON CONFLICT DO NOTHING;
