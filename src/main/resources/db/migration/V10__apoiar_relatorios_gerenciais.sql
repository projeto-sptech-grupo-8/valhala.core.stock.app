-- Apoio às consultas agregadas do módulo de relatórios.

-- Custo unitário por produto: o custo cadastrado para produto padrão e, para drink, a soma do custo
-- dos ingredientes da receita (ML proporcional ao volume da embalagem; UN pelo custo da unidade).
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

CREATE INDEX IF NOT EXISTS idx_pedido_estabelecimento_concluido_em
    ON pedido (estabelecimento_id, concluido_em);
