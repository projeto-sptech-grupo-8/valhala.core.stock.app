-- Adiciona controle de versão otimista em estoque e lote_estoque e reconcilia
-- saldos legados divergentes para lotes (removendo reconciliação em leitura).

ALTER TABLE estoque
    ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;

ALTER TABLE lote_estoque
    ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;

WITH diferenca_positiva AS (
    SELECT e.id AS estoque_id,
           e.quantidade_atual - COALESCE(SUM(l.quantidade_atual), 0) AS diferenca
    FROM estoque e
    LEFT JOIN lote_estoque l ON l.estoque_id = e.id
    GROUP BY e.id, e.quantidade_atual
    HAVING e.quantidade_atual > COALESCE(SUM(l.quantidade_atual), 0)
)
INSERT INTO lote_estoque (estoque_id, codigo, validade, quantidade_atual)
SELECT estoque_id, 'SEM-LOTE', NULL, diferenca
FROM diferenca_positiva
ON CONFLICT (estoque_id, codigo) DO UPDATE
    SET quantidade_atual = lote_estoque.quantidade_atual + EXCLUDED.quantidade_atual,
        atualizado_em = CURRENT_TIMESTAMP,
        versao = lote_estoque.versao + 1;

WITH excesso_estoque AS (
    SELECT e.id AS estoque_id,
           COALESCE(SUM(l.quantidade_atual), 0) - e.quantidade_atual AS excesso
    FROM estoque e
    JOIN lote_estoque l ON l.estoque_id = e.id
    GROUP BY e.id, e.quantidade_atual
    HAVING COALESCE(SUM(l.quantidade_atual), 0) > e.quantidade_atual
),
consumo_fefo AS (
    SELECT l.id,
           LEAST(
               l.quantidade_atual,
               GREATEST(
                   ex.excesso - COALESCE(
                       SUM(l.quantidade_atual) OVER (
                           PARTITION BY l.estoque_id
                           ORDER BY l.validade ASC NULLS LAST, l.criado_em ASC, l.id ASC
                           ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
                       ),
                       0
                   ),
                   0
               )
           ) AS consumo
    FROM lote_estoque l
    JOIN excesso_estoque ex ON ex.estoque_id = l.estoque_id
    WHERE l.quantidade_atual > 0
)
UPDATE lote_estoque l
SET quantidade_atual = l.quantidade_atual - c.consumo,
    atualizado_em = CURRENT_TIMESTAMP,
    versao = l.versao + 1
FROM consumo_fefo c
WHERE l.id = c.id
  AND c.consumo > 0;
