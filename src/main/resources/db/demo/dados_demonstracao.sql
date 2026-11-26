-- Dados de demonstração para desenvolvimento (substitui o antigo mockServer do front).
-- Aplicado por DemoDataSeeder somente com o perfil `dev`; nunca em produção.
-- NÃO é uma migração Flyway: dados de demonstração não fazem parte do schema.
-- Script idempotente: pode rodar a cada inicialização sem duplicar registros.
-- Todos os usuários usam a senha 'Senha@123'.
-- Requer a extensão pgcrypto (criada por db/script.sql) para crypt()/gen_salt().

INSERT INTO estabelecimento (razao_social, nome_fantasia, cnpj, email_contato, ativo)
SELECT 'Valhalla Comercio LTDA', 'Valhalla', '00000000000191', 'admin@valhalla.local', TRUE
WHERE NOT EXISTS (SELECT 1 FROM estabelecimento WHERE cnpj = '00000000000191');

-- ─── Perfis e permissões ───
INSERT INTO perfil (estabelecimento_id, nome, descricao)
SELECT e.id, p.nome, p.descricao
FROM estabelecimento e
CROSS JOIN (VALUES
    ('Gerente',    'Acesso total ao estabelecimento.'),
    ('Caixa',      'Atendimento no caixa e nas mesas.'),
    ('Estoquista', 'Recebimento e controle de estoque.'),
    ('Financeiro', 'Contas, fluxo de caixa e relatórios.'),
    ('Consulta',   'Somente leitura de estoque e relatórios.')
) AS p(nome, descricao)
WHERE e.cnpj = '00000000000191'
ON CONFLICT (estabelecimento_id, nome) DO NOTHING;

INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT p.id, f.id
FROM perfil p
JOIN estabelecimento e ON e.id = p.estabelecimento_id AND e.cnpj = '00000000000191'
JOIN (VALUES
    ('Caixa',      'VISUALIZAR_ESTOQUE'),
    ('Caixa',      'CAIXA_OPERAR'),
    ('Caixa',      'CAIXA_FECHAR'),
    ('Caixa',      'CAIXA_VISUALIZAR'),
    ('Caixa',      'CAIXA_CANCELAR'),
    ('Estoquista', 'VISUALIZAR_ESTOQUE'),
    ('Estoquista', 'MOVIMENTAR_ESTOQUE'),
    ('Estoquista', 'GERENCIAR_ESTOQUE'),
    ('Financeiro', 'VISUALIZAR_ESTOQUE'),
    ('Financeiro', 'VISUALIZAR_FINANCEIRO'),
    ('Financeiro', 'GERENCIAR_FINANCEIRO'),
    ('Financeiro', 'VISUALIZAR_RELATORIOS'),
    ('Consulta',   'VISUALIZAR_ESTOQUE'),
    ('Consulta',   'VISUALIZAR_RELATORIOS'),
    ('Consulta',   'VISUALIZAR_FINANCEIRO')
) AS pf(perfil, codigo) ON pf.perfil = p.nome
JOIN funcionalidade f ON f.codigo = pf.codigo
ON CONFLICT DO NOTHING;

-- O Gerente recebe todo o catálogo de funcionalidades existente.
INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT p.id, f.id
FROM perfil p
JOIN estabelecimento e ON e.id = p.estabelecimento_id AND e.cnpj = '00000000000191'
CROSS JOIN funcionalidade f
WHERE p.nome = 'Gerente'
ON CONFLICT DO NOTHING;

-- ─── Usuários ───
INSERT INTO usuario (estabelecimento_id, perfil_id, nome, email, telefone, senha_hash, status, criado_em, atualizado_em)
SELECT e.id, p.id, u.nome, u.email, NULLIF(u.telefone, ''),
       crypt('Senha@123', gen_salt('bf', 12)), u.status, u.criado_em::timestamp, u.criado_em::timestamp
FROM estabelecimento e
CROSS JOIN (VALUES
    ('Dev Gerente',   'dev@meraki.com',     '11999990000', 'Gerente',    'ATIVO',   '2026-01-01 09:00:00'),
    ('Carlos Souza',  'carlos@meraki.com',  '11988881111', 'Estoquista', 'ATIVO',   '2026-02-10 14:20:00'),
    ('Maria Jesus',   'maria@meraki.com',   '11977772222', 'Caixa',      'ATIVO',   '2026-03-05 11:00:00'),
    ('Roberto Alves', 'roberto@meraki.com', '',            'Caixa',      'ATIVO',   '2026-04-18 08:30:00'),
    ('Ana Paula',     'ana@meraki.com',     '11955554444', 'Estoquista', 'INATIVO', '2026-02-22 13:00:00'),
    ('Beatriz Nunes', 'beatriz@meraki.com', '11944443333', 'Financeiro', 'ATIVO',   '2026-05-09 10:10:00')
) AS u(nome, email, telefone, perfil, status, criado_em)
JOIN perfil p ON p.estabelecimento_id = e.id AND p.nome = u.perfil
WHERE e.cnpj = '00000000000191'
  AND NOT EXISTS (SELECT 1 FROM usuario x WHERE LOWER(x.email) = u.email);

-- Sobrescritas individuais de permissão.
INSERT INTO usuario_funcionalidade (usuario_id, funcionalidade_id, efeito)
SELECT u.id, f.id, o.efeito
FROM (VALUES
    ('roberto@meraki.com'::text, 'MOVIMENTAR_ESTOQUE'::text, 'GRANT'::text),
    ('beatriz@meraki.com',       'VISUALIZAR_ESTOQUE',       'REVOKE')
) AS o(email, codigo, efeito)
JOIN usuario u ON LOWER(u.email) = o.email
JOIN funcionalidade f ON f.codigo = o.codigo
ON CONFLICT (usuario_id, funcionalidade_id) DO NOTHING;

-- ─── Categorias (inclui uma inativa sem produtos para teste de filtros/exclusão) ───
INSERT INTO categoria (estabelecimento_id, nome, descricao, ativo)
SELECT e.id, c.nome, c.descricao, c.ativo
FROM estabelecimento e
CROSS JOIN (VALUES
    ('Cerveja',    'Cervejas nacionais e importadas', TRUE),
    ('Vinho',      'Vinhos tintos, brancos e espumantes', TRUE),
    ('Destilados', 'Whiskys, vodkas e destilados', TRUE),
    ('Gin',        'Gins artesanais e clássicos', TRUE),
    ('Combos',     'Kits e combos promocionais', TRUE),
    ('Sem Álcool', 'Águas, energéticos, sucos e mixers', TRUE),
    ('Drinks',     'Drinks preparados na casa', TRUE),
    ('Sazonais',   'Categoria inativa para demonstração de filtros', FALSE)
) AS c(nome, descricao, ativo)
WHERE e.cnpj = '00000000000191'
ON CONFLICT DO NOTHING;

-- ─── Produtos padrão (fracionado, fardo, sem histórico e inativo) ───
INSERT INTO produto (estabelecimento_id, categoria_id, sku, nome, tipo, unidade_medida,
                     preco_custo, preco_venda, fracionado, volume_embalagem_ml,
                     fardo, unidades_por_fardo, ativo)
SELECT e.id, c.id, p.sku, p.nome, 'PADRAO', p.unidade, p.custo, p.preco,
       p.volume IS NOT NULL, p.volume, p.fardo, p.unidades_por_fardo, p.ativo
FROM estabelecimento e
CROSS JOIN (VALUES
    ('BEB-001', 'Heineken 600ml',                 'Cerveja',    'un',  9.50,   14.90,  600,  FALSE, NULL::int, TRUE),
    ('BEB-002', 'Stella Artois 350ml',            'Cerveja',    'un',  6.20,   9.90,   350,  FALSE, NULL::int, TRUE),
    ('BEB-003', 'Budweiser 350ml',                'Cerveja',    'un',  5.80,   8.90,   350,  FALSE, NULL::int, TRUE),
    ('BEB-004', 'Corona Extra 355ml',             'Cerveja',    'un',  8.40,   13.90,  355,  FALSE, NULL::int, TRUE),
    ('BEB-005', 'Vinho Tinto Reserva 750ml',      'Vinho',      'un',  54.00,  89.00,  750,  FALSE, NULL::int, TRUE),
    ('BEB-006', 'Vinho Branco Seco 750ml',        'Vinho',      'un',  44.00,  72.00,  750,  FALSE, NULL::int, TRUE),
    ('BEB-007', 'Espumante Brut 750ml',           'Vinho',      'un',  62.00,  98.00,  750,  FALSE, NULL::int, TRUE),
    ('BEB-008', 'Jack Daniel''s 1L',              'Destilados', 'un',  130.00, 189.00, 1000, FALSE, NULL::int, TRUE),
    ('BEB-009', 'Vodka Absolut 750ml',            'Destilados', 'un',  58.00,  84.00,  750,  FALSE, NULL::int, TRUE),
    ('BEB-010', 'Gin Amazônico Premium 750ml',    'Gin',        'un',  78.00,  119.00, 750,  FALSE, NULL::int, TRUE),
    ('BEB-011', 'Tanqueray London Dry 750ml',     'Gin',        'un',  90.00,  134.00, 750,  FALSE, NULL::int, TRUE),
    ('BEB-012', 'Red Bull 250ml',                 'Sem Álcool', 'un',  8.00,   12.90,  250,  FALSE, NULL::int, TRUE),
    ('BEB-013', 'Água Mineral 500ml',             'Sem Álcool', 'un',  2.10,   4.50,   500,  FALSE, NULL::int, TRUE),
    ('BEB-014', 'Combo Cerveja ×6',               'Combos',     'kit', 34.00,  49.90,  NULL, FALSE, NULL::int, TRUE),
    ('BEB-015', 'Skol Lata 350ml',                'Cerveja',    'un',  3.20,   6.00,   NULL, TRUE,  12,        TRUE),
    ('BEB-016', 'Suco de Limão Natural 1L',       'Sem Álcool', 'un',  6.50,   12.00,  NULL, FALSE, NULL::int, TRUE),
    ('BEB-017', 'Cerveja Sazonal Inverno 500ml',  'Cerveja',    'un',  11.00,  18.00,  NULL, FALSE, NULL::int, FALSE)
) AS p(sku, nome, cat, unidade, custo, preco, volume, fardo, unidades_por_fardo, ativo)
JOIN categoria c ON c.estabelecimento_id = e.id AND LOWER(c.nome) = LOWER(p.cat)
WHERE e.cnpj = '00000000000191'
ON CONFLICT (estabelecimento_id, sku) DO NOTHING;

-- ─── Estoque inicial (ENTRADA_INICIAL apenas para produtos com saldo inicial > 0) ───
-- BEB-016 (ativo, sem histórico) e BEB-017 (inativo, sem histórico) nascem com saldo 0 e sem movimentação,
-- permitindo testar a inativação lógica sem vínculos.
WITH novos AS (
    INSERT INTO estoque (produto_id, quantidade_atual, estoque_minimo)
    SELECT pr.id,
           s.qtd * COALESCE(pr.volume_embalagem_ml, 1),
           s.minimo * COALESCE(pr.volume_embalagem_ml, 1)
    FROM (VALUES
        ('BEB-001', 48, 24), ('BEB-002', 8, 36), ('BEB-003', 36, 24), ('BEB-004', 24, 12),
        ('BEB-005', 12, 6),  ('BEB-006', 18, 6), ('BEB-007', 4, 6),   ('BEB-008', 3, 6),
        ('BEB-009', 7, 4),   ('BEB-010', 5, 4),  ('BEB-011', 3, 4),   ('BEB-012', 72, 48),
        ('BEB-013', 96, 48), ('BEB-014', 20, 10),('BEB-015', 48, 24), ('BEB-016', 0, 4),
        ('BEB-017', 0, 0)
    ) AS s(sku, qtd, minimo)
    JOIN estabelecimento e ON e.cnpj = '00000000000191'
    JOIN produto pr ON pr.estabelecimento_id = e.id AND pr.sku = s.sku
    ON CONFLICT (produto_id) DO NOTHING
    RETURNING id, quantidade_atual
)
INSERT INTO movimentacao_estoque (estoque_id, usuario_id, tipo, quantidade, saldo_anterior, saldo_posterior, motivo, ocorrido_em)
SELECT n.id, u.id, 'ENTRADA_INICIAL', n.quantidade_atual, 0, n.quantidade_atual,
       'Carga de demonstração', CURRENT_TIMESTAMP - INTERVAL '95 days'
FROM novos n
JOIN usuario u ON LOWER(u.email) = 'dev@meraki.com'
WHERE n.quantidade_atual > 0;

-- ─── Lotes e validades de demonstração (FEFO) ───
DELETE FROM movimentacao_estoque_lote ml
USING lote_estoque l, estoque es, produto pr, estabelecimento e
WHERE ml.lote_id = l.id
  AND l.estoque_id = es.id
  AND es.produto_id = pr.id
  AND pr.estabelecimento_id = e.id
  AND e.cnpj = '00000000000191'
  AND l.codigo = 'SEM-LOTE'
  AND pr.sku IN ('BEB-001', 'BEB-002', 'BEB-003', 'BEB-004', 'BEB-012', 'BEB-013', 'BEB-014', 'BEB-015')
  AND NOT EXISTS (SELECT 1 FROM lote_estoque l2 WHERE l2.estoque_id = es.id AND l2.codigo <> 'SEM-LOTE')
  AND NOT EXISTS (SELECT 1 FROM movimentacao_estoque m WHERE m.estoque_id = es.id AND m.tipo <> 'ENTRADA_INICIAL');

DELETE FROM lote_estoque l
USING estoque es, produto pr, estabelecimento e
WHERE l.estoque_id = es.id
  AND es.produto_id = pr.id
  AND pr.estabelecimento_id = e.id
  AND e.cnpj = '00000000000191'
  AND l.codigo = 'SEM-LOTE'
  AND pr.sku IN ('BEB-001', 'BEB-002', 'BEB-003', 'BEB-004', 'BEB-012', 'BEB-013', 'BEB-014', 'BEB-015')
  AND NOT EXISTS (SELECT 1 FROM lote_estoque l2 WHERE l2.estoque_id = es.id AND l2.codigo <> 'SEM-LOTE')
  AND NOT EXISTS (SELECT 1 FROM movimentacao_estoque m WHERE m.estoque_id = es.id AND m.tipo <> 'ENTRADA_INICIAL');

INSERT INTO lote_estoque (estoque_id, codigo, validade, quantidade_atual)
SELECT es.id,
       lt.codigo,
       CURRENT_DATE + lt.dias_validade::int,
       lt.qtd * COALESCE(pr.volume_embalagem_ml, 1)
FROM (VALUES
    ('BEB-001', 'L2607Z', -15,  0),
    ('BEB-001', 'L2609A',  12, 18),
    ('BEB-001', 'L2610B',  75, 30),
    ('BEB-002', 'ST0815',   3,  3),
    ('BEB-002', 'ST0926',  40,  5),
    ('BEB-003', 'BD0812',  -3,  6),
    ('BEB-003', 'BD0905',  25, 12),
    ('BEB-003', 'BD0920', 110, 18),
    ('BEB-004', 'CR0801',   5,  8),
    ('BEB-004', 'CR0915',  90, 16),
    ('BEB-005', 'SEM-LOTE', NULL, 12),
    ('BEB-006', 'SEM-LOTE', NULL, 18),
    ('BEB-007', 'SEM-LOTE', NULL,  4),
    ('BEB-008', 'SEM-LOTE', NULL,  3),
    ('BEB-009', 'SEM-LOTE', NULL,  7),
    ('BEB-010', 'SEM-LOTE', NULL,  5),
    ('BEB-011', 'SEM-LOTE', NULL,  3),
    ('BEB-012', 'RB0710',  -8,  4),
    ('BEB-012', 'RB0830',  20, 20),
    ('BEB-012', 'RB0920', 160, 48),
    ('BEB-013', 'AG0805',   6, 16),
    ('BEB-013', 'AG0901', 200, 80),
    ('BEB-014', 'CB0801', -20,  0),
    ('BEB-014', 'CB0911',  28, 20),
    ('BEB-015', 'SK1012',  55, 48)
) AS lt(sku, codigo, dias_validade, qtd)
JOIN estabelecimento e ON e.cnpj = '00000000000191'
JOIN produto pr ON pr.estabelecimento_id = e.id AND pr.sku = lt.sku
JOIN estoque es ON es.produto_id = pr.id
WHERE NOT EXISTS (SELECT 1 FROM lote_estoque l WHERE l.estoque_id = es.id)
ON CONFLICT (estoque_id, codigo) DO NOTHING;

INSERT INTO movimentacao_estoque_lote (movimentacao_id, lote_id, quantidade)
SELECT m.id, l.id, l.quantidade_atual
FROM movimentacao_estoque m
JOIN estoque es ON es.id = m.estoque_id
JOIN produto pr ON pr.id = es.produto_id
JOIN estabelecimento e ON e.id = pr.estabelecimento_id AND e.cnpj = '00000000000191'
JOIN lote_estoque l ON l.estoque_id = es.id AND l.quantidade_atual > 0
WHERE m.tipo = 'ENTRADA_INICIAL'
  AND NOT EXISTS (SELECT 1 FROM movimentacao_estoque_lote ml WHERE ml.movimentacao_id = m.id);

UPDATE movimentacao_estoque m
SET lote = sub.codigos
FROM (
    SELECT m2.id AS movimentacao_id,
           STRING_AGG(l.codigo, ', ' ORDER BY l.validade NULLS LAST, l.id) AS codigos
    FROM movimentacao_estoque m2
    JOIN movimentacao_estoque_lote ml ON ml.movimentacao_id = m2.id
    JOIN lote_estoque l ON l.id = ml.lote_id
    WHERE m2.tipo = 'ENTRADA_INICIAL' AND m2.lote IS NULL
    GROUP BY m2.id
) sub
WHERE m.id = sub.movimentacao_id;

-- ─── Drinks (DRK-005 sem vendas/saídas para testar inativação de drink) ───
INSERT INTO produto (estabelecimento_id, categoria_id, sku, nome, tipo, unidade_medida, preco_custo, preco_venda)
SELECT e.id, c.id, d.sku, d.nome, 'DRINK', d.unidade, 0, d.preco
FROM estabelecimento e
CROSS JOIN (VALUES
    ('DRK-001', 'Copo Jack Rocks',      'copo', 28.00),
    ('DRK-002', 'Gin Tônica Premium',   'copo', 32.00),
    ('DRK-003', 'Caipirinha de Vodka',  'copo', 24.00),
    ('DRK-004', 'Taça Vinho da Casa',   'taça', 22.00),
    ('DRK-005', 'Mojito Clássico',      'copo', 26.00)
) AS d(sku, nome, unidade, preco)
JOIN categoria c ON c.estabelecimento_id = e.id AND c.nome = 'Drinks'
WHERE e.cnpj = '00000000000191'
ON CONFLICT (estabelecimento_id, sku) DO NOTHING;

INSERT INTO composicao_drink (drink_id, produto_id, quantidade, unidade_consumo)
SELECT d.id, i.id, r.quantidade, 'ML'
FROM estabelecimento e
JOIN (VALUES
    ('DRK-001', 'BEB-008', 50),
    ('DRK-002', 'BEB-010', 60),
    ('DRK-003', 'BEB-009', 60),
    ('DRK-004', 'BEB-005', 150),
    ('DRK-005', 'BEB-013', 100)
) AS r(drink, ingrediente, quantidade) ON TRUE
JOIN produto d ON d.estabelecimento_id = e.id AND d.sku = r.drink
JOIN produto i ON i.estabelecimento_id = e.id AND i.sku = r.ingrediente
WHERE e.cnpj = '00000000000191'
ON CONFLICT (drink_id, produto_id) DO NOTHING;

-- ─── Movimentações históricas: rupturas (recorrente e pontual), perdas por motivo e ajustes ───
-- Todas terminam no mesmo saldo atual registrado em estoque.quantidade_atual.
INSERT INTO movimentacao_estoque (
    estoque_id, usuario_id, origem, tipo, quantidade, saldo_anterior, saldo_posterior,
    motivo, motivo_perda, ocorrido_em
)
SELECT es.id, u.id, 'USUARIO', mv.tipo, mv.quantidade, mv.saldo_anterior, mv.saldo_posterior,
       mv.motivo, mv.motivo_perda,
       date_trunc('day', CURRENT_TIMESTAMP) - (mv.dias_atras || ' days')::interval + (mv.hora || ' hours')::interval
FROM estabelecimento e
JOIN usuario u ON LOWER(u.email) = 'carlos@meraki.com'
JOIN (VALUES
    -- Ruptura RECORRENTE em BEB-002 (Stella Artois 350ml = 2800ml): 11 dias zerado (Recorrente em 7d, 30d e 90d)
    ('BEB-002', 'SAIDA',           2800.000, 2800.000,    0.000, '[DEMO] Zera estoque Stella antes de reposição',      NULL,              15, 14),
    ('BEB-002', 'ENTRADA',         2800.000,    0.000, 2800.000, '[DEMO] Reposição Stella após ruptura recorrente',    NULL,               5,  9),
    -- Ruptura PONTUAL em BEB-007 (Espumante Brut 750ml = 3000ml): 2 dias zerado (Pontual em 30d e 90d)
    ('BEB-007', 'SAIDA',           3000.000, 3000.000,    0.000, '[DEMO] Zera estoque Espumante em evento',            NULL,               3, 18),
    ('BEB-007', 'ENTRADA',         3000.000,    0.000, 3000.000, '[DEMO] Reposição Espumante após ruptura pontual',    NULL,               2, 10),
    -- Perdas (QUEBRA, VENCIMENTO e CONSUMO_INTERNO) nos últimos 7d, 30d e 90d + reposições equivalentes
    ('BEB-001', 'ENTRADA',          600.000, 28800.000, 29400.000, '[DEMO] Reposição Heineken para conferência',       NULL,               6, 10),
    ('BEB-001', 'PERDA',            600.000, 29400.000, 28800.000, '[DEMO] Garrafa quebrada na câmara fria',           'QUEBRA',           5, 16),
    ('BEB-005', 'ENTRADA',          750.000,  9000.000,  9750.000, '[DEMO] Entrada vinho reserva',                     NULL,              45, 10),
    ('BEB-005', 'PERDA',            750.000,  9750.000,  9000.000, '[DEMO] Garrafa de vinho trincada no transporte',   'QUEBRA',          42, 15),
    ('BEB-003', 'ENTRADA',          700.000, 12600.000, 13300.000, '[DEMO] Entrada Budweiser lote anterior',           NULL,              18, 10),
    ('BEB-003', 'PERDA',            700.000, 13300.000, 12600.000, '[DEMO] Lote Budweiser vencido descartado',         'VENCIMENTO',      12, 17),
    ('BEB-012', 'ENTRADA',          500.000, 18000.000, 18500.000, '[DEMO] Entrada Red Bull conferência',              NULL,              25, 11),
    ('BEB-012', 'PERDA',            500.000, 18500.000, 18000.000, '[DEMO] Latas Red Bull vencidas no estoque',        'VENCIMENTO',      19, 14),
    ('BEB-013', 'ENTRADA',         1000.000, 48000.000, 49000.000, '[DEMO] Entrada água mineral uso interno',          NULL,               4,  9),
    ('BEB-013', 'PERDA',           1000.000, 49000.000, 48000.000, '[DEMO] Consumo interno da equipe no turno',        'CONSUMO_INTERNO',  2, 18),
    ('BEB-004', 'ENTRADA',          355.000,  8520.000,  8875.000, '[DEMO] Entrada Corona degustação',                 NULL,              22, 10),
    ('BEB-004', 'PERDA',            355.000,  8875.000,  8520.000, '[DEMO] Degustação promocional com clientes',       'CONSUMO_INTERNO', 20, 19),
    -- Ajustes positivos e negativos para indicadores de estoque
    ('BEB-015', 'AJUSTE_POSITIVO',    2.000,    48.000,    50.000, '[DEMO] Ajuste de contagem positiva Skol',          NULL,              11, 11),
    ('BEB-015', 'AJUSTE_NEGATIVO',    2.000,    50.000,    48.000, '[DEMO] Ajuste de inventário Skol',                 NULL,               9, 11)
) AS mv(sku, tipo, quantidade, saldo_anterior, saldo_posterior, motivo, motivo_perda, dias_atras, hora) ON TRUE
JOIN produto pr ON pr.estabelecimento_id = e.id AND pr.sku = mv.sku
JOIN estoque es ON es.produto_id = pr.id
WHERE e.cnpj = '00000000000191'
  AND NOT EXISTS (
      SELECT 1 FROM movimentacao_estoque m
      WHERE m.estoque_id = es.id AND m.motivo = mv.motivo
  );

-- ─── Caixa, terminal, sessões fechadas e auditoria ───
INSERT INTO configuracao_caixa (
    estabelecimento_id, point_habilitado, dinheiro_habilitado, debito_habilitado,
    credito_habilitado, pix_habilitado, parcelamento_habilitado, parcelas_minimas,
    parcelas_maximas, politica_juros, validade_cobranca_segundos,
    limite_desconto_percentual, limite_desconto_valor, imprimir_comprovante,
    permitir_pagamento_manual, atualizado_por_usuario_id
)
SELECT e.id, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, 1, 6, 'ABSORVIDA_PELA_LOJA', 300,
       10.00, 30.00, TRUE, TRUE, u.id
FROM estabelecimento e
JOIN usuario u ON LOWER(u.email) = 'dev@meraki.com'
WHERE e.cnpj = '00000000000191'
ON CONFLICT (estabelecimento_id) DO NOTHING;

INSERT INTO caixa (estabelecimento_id, nome, ativo)
SELECT e.id, cx.nome, TRUE
FROM estabelecimento e
CROSS JOIN (VALUES ('Caixa Principal'), ('Caixa Bar')) AS cx(nome)
WHERE e.cnpj = '00000000000191'
  AND NOT EXISTS (
      SELECT 1 FROM caixa c
      WHERE c.estabelecimento_id = e.id AND LOWER(BTRIM(c.nome)) = LOWER(cx.nome)
  );

INSERT INTO terminal_caixa (caixa_id, terminal_id, nome, ativo)
SELECT c.id, 'POINT-DEMO-01', 'Point Smart Balcão', TRUE
FROM estabelecimento e
JOIN caixa c ON c.estabelecimento_id = e.id AND LOWER(BTRIM(c.nome)) = 'caixa principal'
WHERE e.cnpj = '00000000000191'
  AND NOT EXISTS (
      SELECT 1 FROM terminal_caixa t
      WHERE t.terminal_id = 'POINT-DEMO-01'
         OR (t.caixa_id = c.id AND LOWER(BTRIM(t.nome)) = 'point smart balcão')
  )
ON CONFLICT (terminal_id) DO NOTHING;

INSERT INTO taxa_pagamento (estabelecimento_id, forma, parcelas, taxa_percentual, taxa_fixa, repassar_ao_cliente, ativo)
SELECT e.id, t.forma, t.parcelas, t.taxa_percentual, 0.00, NULL, TRUE
FROM estabelecimento e
CROSS JOIN (VALUES
    ('DEBITO',  1,  1.9900),
    ('PIX',     1,  0.4900),
    ('CREDITO', 1,  4.9800),
    ('CREDITO', 2,  7.9000),
    ('CREDITO', 3,  9.3000),
    ('CREDITO', 4, 10.6500),
    ('CREDITO', 5, 11.9500),
    ('CREDITO', 6, 13.2000)
) AS t(forma, parcelas, taxa_percentual)
WHERE e.cnpj = '00000000000191'
ON CONFLICT (estabelecimento_id, forma, parcelas) DO NOTHING;

-- Duas sessões de caixa FECHADAS (não deixa sessão ABERTA para não bloquear testes manuais no caixa)
INSERT INTO sessao_caixa (
    estabelecimento_id, caixa_id, operador_usuario_id, status, fundo_troco,
    dinheiro_esperado, dinheiro_contado, diferenca,
    observacao_abertura, observacao_fechamento, aberta_em, fechada_em, fechada_por_usuario_id
)
SELECT e.id, c.id, op.id, 'FECHADA', 200.00, 620.00, 620.00, 0.00,
       '[DEMO] Turno noturno anterior', '[DEMO] Conferência fechada sem divergência',
       date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '15 days' + INTERVAL '17 hours',
       date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '15 days' + INTERVAL '23 hours 50 minutes',
       gerente.id
FROM estabelecimento e
JOIN caixa c ON c.estabelecimento_id = e.id AND LOWER(BTRIM(c.nome)) = 'caixa principal'
JOIN usuario op ON LOWER(op.email) = 'maria@meraki.com'
JOIN usuario gerente ON LOWER(gerente.email) = 'dev@meraki.com'
WHERE e.cnpj = '00000000000191'
  AND NOT EXISTS (
      SELECT 1 FROM sessao_caixa s
      WHERE s.estabelecimento_id = e.id AND s.observacao_abertura = '[DEMO] Turno noturno anterior'
  );

INSERT INTO sessao_caixa (
    estabelecimento_id, caixa_id, operador_usuario_id, status, fundo_troco,
    dinheiro_esperado, dinheiro_contado, diferenca,
    observacao_abertura, observacao_fechamento, justificativa_divergencia,
    aberta_em, fechada_em, fechada_por_usuario_id,
    divergencia_resolvida_por_usuario_id, divergencia_resolvida_em
)
SELECT e.id, c.id, op.id, 'FECHADA', 150.00, 480.00, 475.00, -5.00,
       '[DEMO] Turno recente fechado', '[DEMO] Fechamento com ajuste de troco',
       'Diferença de R$ 5,00 em moedas conferida pela gerência',
       date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '1 day' + INTERVAL '16 hours',
       date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '1 day' + INTERVAL '23 hours 30 minutes',
       gerente.id, gerente.id,
       date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '1 day' + INTERVAL '23 hours 40 minutes'
FROM estabelecimento e
JOIN caixa c ON c.estabelecimento_id = e.id AND LOWER(BTRIM(c.nome)) = 'caixa bar'
JOIN usuario op ON LOWER(op.email) = 'roberto@meraki.com'
JOIN usuario gerente ON LOWER(gerente.email) = 'dev@meraki.com'
WHERE e.cnpj = '00000000000191'
  AND NOT EXISTS (
      SELECT 1 FROM sessao_caixa s
      WHERE s.estabelecimento_id = e.id AND s.observacao_abertura = '[DEMO] Turno recente fechado'
  );

-- ─── Pedidos concluídos, estornado e histórico mensal (para Relatórios, Dashboard e Financeiro) ───
-- Números 1001..1026 reservados para dados de demonstração.
WITH dados_pedidos AS (
    SELECT * FROM (VALUES
        -- Últimos 7 dias (sempre presentes no mês/7d/30d/90d; variados dias da semana, canais e horários)
        (1001::bigint, 'CONCLUIDO', 'MESA',     'Mesa 4',     290.40,  0.00, 290.40, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '6 days' + INTERVAL '20 hours', NULL::timestamp, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
        (1002::bigint, 'CONCLUIDO', 'BALCAO',   'Balcão 1',   273.80, 13.80, 260.00, 'VALOR',       13.80,         'APROVADO',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '5 days' + INTERVAL '21 hours', NULL::timestamp, 'DINHEIRO', 'INTERNO', 1, 300.00,        40.00, NULL::text),
        (1003::bigint, 'CONCLUIDO', 'DELIVERY', 'Delivery 1', 178.00,  0.00, 178.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '4 days' + INTERVAL '22 hours', NULL::timestamp, 'CREDITO',  'MANUAL',  2, NULL::numeric, 0.00, NULL::text),
        (1004::bigint, 'CONCLUIDO', 'RETIRADA', 'Retirada 1', 167.80,  0.00, 167.80, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '3 days' + INTERVAL '19 hours', NULL::timestamp, 'DEBITO',   'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
        (1005::bigint, 'CONCLUIDO', 'MESA',     'Mesa 2',     320.00, 20.00, 300.00, 'VALOR',       20.00,         'APROVADO',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '2 days' + INTERVAL '21 hours', NULL::timestamp, 'CREDITO',  'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
        (1006::bigint, 'CONCLUIDO', 'BALCAO',   'Balcão 2',   196.00,  0.00, 196.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '1 day'  + INTERVAL '18 hours', NULL::timestamp, 'DINHEIRO', 'INTERNO', 1, 200.00,         4.00, NULL::text),
        -- Pedido garantido no mês atual (dia 1 às 11h) para manter o mês atual populado mesmo no dia 1 do mês
        (1007::bigint, 'CONCLUIDO', 'MESA',     'Mesa 1',     298.00,  0.00, 298.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '11 hours',                    NULL::timestamp, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
        -- Pedido garantido na data de hoje (antes do fim do dia) com produtos em ruptura (BEB-002 e BEB-007)
        (1008::bigint, 'CONCLUIDO', 'DELIVERY', 'Delivery 2', 147.50,  0.00, 147.50, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('day', CURRENT_TIMESTAMP) + INTERVAL '10 hours',                      NULL::timestamp, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
        -- Pedidos adicionais entre 8 e 28 dias atrás (30d e 90d)
        (1009::bigint, 'CONCLUIDO', 'MESA',     'Mesa 6',     378.00,  0.00, 378.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '9 days'  + INTERVAL '22 hours', NULL::timestamp, 'CREDITO',  'MANUAL',  2, NULL::numeric, 0.00, NULL::text),
        (1010::bigint, 'CONCLUIDO', 'BALCAO',   'Balcão 3',   149.70,  0.00, 149.70, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '11 days' + INTERVAL '17 hours', NULL::timestamp, 'DEBITO',   'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
        (1011::bigint, 'CONCLUIDO', 'RETIRADA', 'Retirada 2', 238.00,  0.00, 238.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '14 days' + INTERVAL '20 hours', NULL::timestamp, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
        (1012::bigint, 'CONCLUIDO', 'MESA',     'Mesa 7',     189.00,  0.00, 189.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '18 days' + INTERVAL '23 hours', NULL::timestamp, 'CREDITO',  'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
        (1013::bigint, 'CONCLUIDO', 'DELIVERY', 'Delivery 3', 196.00,  0.00, 196.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '21 days' + INTERVAL '20 hours', NULL::timestamp, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
        (1014::bigint, 'CONCLUIDO', 'BALCAO',   'Balcão 4',    99.80,  0.00,  99.80, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('day', CURRENT_TIMESTAMP) - INTERVAL '25 days' + INTERVAL '15 hours', NULL::timestamp, 'DINHEIRO', 'INTERNO', 1, 100.00,         0.20, NULL::text),
        -- Pedido ESTORNADO no mês atual (gera cancelamentos no DRE e exclui do faturamento líquido)
        (1020::bigint, 'ESTORNADO', 'MESA',     'Mesa 9',     134.00,  0.00, 134.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '12 hours',
            date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '13 hours',
            'PIX', 'MANUAL', 1, NULL::numeric, 0.00, 'Cancelamento solicitado pelo cliente'),
        -- Histórico dos 5 meses anteriores (para comparação mensal, 90d, DRE anterior e fluxo de 6 meses)
        (1021::bigint, 'CONCLUIDO', 'MESA',     'Mesa 3',     890.00,  0.00, 890.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('month', CURRENT_TIMESTAMP) - INTERVAL '1 month'  + INTERVAL '12 hours', NULL::timestamp, 'CREDITO', 'MANUAL', 2, NULL::numeric, 0.00, NULL::text),
        (1022::bigint, 'CONCLUIDO', 'BALCAO',   'Balcão 1',   670.00,  0.00, 670.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('month', CURRENT_TIMESTAMP) - INTERVAL '2 months' + INTERVAL '19 hours', NULL::timestamp, 'PIX',     'MANUAL', 1, NULL::numeric, 0.00, NULL::text),
        (1023::bigint, 'CONCLUIDO', 'DELIVERY', 'Delivery 4', 756.00,  0.00, 756.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('month', CURRENT_TIMESTAMP) - INTERVAL '3 months' + INTERVAL '20 hours', NULL::timestamp, 'DEBITO',  'MANUAL', 1, NULL::numeric, 0.00, NULL::text),
        (1024::bigint, 'CONCLUIDO', 'MESA',     'Mesa 5',     595.00,  0.00, 595.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('month', CURRENT_TIMESTAMP) - INTERVAL '4 months' + INTERVAL '21 hours', NULL::timestamp, 'PIX',     'MANUAL', 1, NULL::numeric, 0.00, NULL::text),
        (1025::bigint, 'CONCLUIDO', 'RETIRADA', 'Retirada 3', 534.00,  0.00, 534.00, NULL::varchar, NULL::numeric, 'NENHUM',
            date_trunc('month', CURRENT_TIMESTAMP) - INTERVAL '5 months' + INTERVAL '18 hours', NULL::timestamp, 'CREDITO', 'MANUAL', 1, NULL::numeric, 0.00, NULL::text)
    ) AS p(numero, status, canal, identificador, subtotal, desconto, total, tipo_desconto, valor_desconto_informado,
           status_desconto, concluido_em, estornado_em, forma, provedor, parcelas, valor_recebido, valor_troco, motivo_estorno)
)
INSERT INTO pedido (
    estabelecimento_id, operador_usuario_id, numero, tipo, status, canal, identificador_atendimento,
    sessao_caixa_id, subtotal, desconto, total, tipo_desconto, valor_desconto_informado, status_desconto,
    motivo_desconto, motivo_estorno, estornado_por_usuario_id, criado_em, atualizado_em, concluido_em, estornado_em
)
SELECT e.id, op.id, dp.numero, 'VENDA_PDV', dp.status, dp.canal, dp.identificador,
       sc.id, dp.subtotal, dp.desconto, dp.total, dp.tipo_desconto, dp.valor_desconto_informado, dp.status_desconto,
       CASE WHEN dp.desconto > 0 THEN 'Desconto comercial de balcão' END,
       dp.motivo_estorno,
       CASE WHEN dp.status = 'ESTORNADO' THEN gerente.id END,
       dp.concluido_em - INTERVAL '15 minutes', dp.concluido_em, dp.concluido_em, dp.estornado_em
FROM dados_pedidos dp
JOIN estabelecimento e ON e.cnpj = '00000000000191'
JOIN usuario op ON LOWER(op.email) = 'maria@meraki.com'
JOIN usuario gerente ON LOWER(gerente.email) = 'dev@meraki.com'
JOIN sessao_caixa sc ON sc.estabelecimento_id = e.id AND sc.observacao_abertura = '[DEMO] Turno recente fechado'
WHERE NOT EXISTS (
    SELECT 1 FROM pedido pe WHERE pe.estabelecimento_id = e.id AND pe.numero = dp.numero
);

-- Itens dos pedidos de demonstração (nunca referencia BEB-016, BEB-017 ou DRK-005)
WITH itens_demo AS (
    SELECT * FROM (VALUES
        (1001::bigint, 'BEB-001',  6.000,  14.90,  89.40,  9.50),
        (1001::bigint, 'BEB-005',  2.000,  89.00, 178.00, 54.00),
        (1001::bigint, 'DRK-001',  1.000,  23.00,  23.00,  6.50),
        (1002::bigint, 'BEB-008',  1.000, 189.00, 189.00,130.00),
        (1002::bigint, 'BEB-009',  1.000,  84.80,  84.80, 58.00),
        (1003::bigint, 'BEB-005',  2.000,  89.00, 178.00, 54.00),
        (1004::bigint, 'BEB-004',  6.000,  13.90,  83.40,  8.40),
        (1004::bigint, 'BEB-003',  6.000,   8.90,  53.40,  5.80),
        (1004::bigint, 'BEB-012',  2.000,  12.90,  25.80,  8.00),
        (1004::bigint, 'BEB-013',  1.000,   5.20,   5.20,  2.10),
        (1005::bigint, 'DRK-002', 10.000,  32.00, 320.00,  6.24),
        (1006::bigint, 'BEB-007',  2.000,  98.00, 196.00, 62.00),
        (1007::bigint, 'BEB-001', 20.000,  14.90, 298.00,  9.50),
        (1008::bigint, 'BEB-002',  5.000,   9.90,  49.50,  6.20),
        (1008::bigint, 'BEB-007',  1.000,  98.00,  98.00, 62.00),
        (1009::bigint, 'BEB-008',  2.000, 189.00, 378.00,130.00),
        (1010::bigint, 'BEB-014',  3.000,  49.90, 149.70, 34.00),
        (1011::bigint, 'BEB-010',  2.000, 119.00, 238.00, 78.00),
        (1012::bigint, 'BEB-006',  2.000,  72.00, 144.00, 44.00),
        (1012::bigint, 'BEB-013', 10.000,   4.50,  45.00,  2.10),
        (1013::bigint, 'DRK-003',  6.000,  24.00, 144.00,  4.64),
        (1013::bigint, 'DRK-004',  2.000,  22.00,  44.00, 10.80),
        (1013::bigint, 'BEB-015',  1.000,   8.00,   8.00,  3.20),
        (1014::bigint, 'BEB-014',  2.000,  49.90,  99.80, 34.00),
        (1020::bigint, 'BEB-011',  1.000, 134.00, 134.00, 90.00),
        (1021::bigint, 'BEB-005', 10.000,  89.00, 890.00, 54.00),
        (1022::bigint, 'BEB-011',  5.000, 134.00, 670.00, 90.00),
        (1023::bigint, 'BEB-008',  4.000, 189.00, 756.00,130.00),
        (1024::bigint, 'BEB-010',  5.000, 119.00, 595.00, 78.00),
        (1025::bigint, 'BEB-005',  6.000,  89.00, 534.00, 54.00)
    ) AS it(numero, sku, quantidade, preco_unitario, subtotal, custo_unitario)
)
INSERT INTO item_pedido (
    pedido_id, produto_id, descricao_produto, quantidade, preco_unitario,
    desconto, subtotal, custo_unitario, categoria_id, categoria_nome
)
SELECT pe.id, pr.id, pr.nome, it.quantidade, it.preco_unitario,
       0.00, it.subtotal, it.custo_unitario, c.id, c.nome
FROM itens_demo it
JOIN estabelecimento e ON e.cnpj = '00000000000191'
JOIN pedido pe ON pe.estabelecimento_id = e.id AND pe.numero = it.numero
JOIN produto pr ON pr.estabelecimento_id = e.id AND pr.sku = it.sku
JOIN categoria c ON c.id = pr.categoria_id
WHERE NOT EXISTS (
    SELECT 1 FROM item_pedido ip WHERE ip.pedido_id = pe.id AND ip.produto_id = pr.id
);

-- Pagamentos correspondentes aos pedidos de demonstração
INSERT INTO pagamento_pedido (
    id, pedido_id, sessao_caixa_id, forma, status, provedor, valor,
    valor_recebido, valor_troco, valor_estornado, parcelas,
    motivo_estorno, pago_em, estornado_em, estornado_por_usuario_id, criado_em, atualizado_em
)
SELECT gen_random_uuid(), pe.id, sc.id, dp.forma,
       CASE WHEN pe.status = 'ESTORNADO' THEN 'ESTORNADO' ELSE 'APROVADO' END,
       dp.provedor, pe.total, dp.valor_recebido, dp.valor_troco,
       CASE WHEN pe.status = 'ESTORNADO' THEN pe.total ELSE 0.00 END,
       dp.parcelas, dp.motivo_estorno, pe.concluido_em, pe.estornado_em,
       CASE WHEN pe.status = 'ESTORNADO' THEN gerente.id END,
       pe.concluido_em, COALESCE(pe.estornado_em, pe.concluido_em)
FROM (VALUES
    (1001::bigint, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1002::bigint, 'DINHEIRO', 'INTERNO', 1, 300.00,        40.00, NULL::text),
    (1003::bigint, 'CREDITO',  'MANUAL',  2, NULL::numeric, 0.00, NULL::text),
    (1004::bigint, 'DEBITO',   'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1005::bigint, 'CREDITO',  'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1006::bigint, 'DINHEIRO', 'INTERNO', 1, 200.00,         4.00, NULL::text),
    (1007::bigint, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1008::bigint, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1009::bigint, 'CREDITO',  'MANUAL',  2, NULL::numeric, 0.00, NULL::text),
    (1010::bigint, 'DEBITO',   'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1011::bigint, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1012::bigint, 'CREDITO',  'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1013::bigint, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1014::bigint, 'DINHEIRO', 'INTERNO', 1, 100.00,         0.20, NULL::text),
    (1020::bigint, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, 'Cancelamento solicitado pelo cliente'),
    (1021::bigint, 'CREDITO',  'MANUAL',  2, NULL::numeric, 0.00, NULL::text),
    (1022::bigint, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1023::bigint, 'DEBITO',   'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1024::bigint, 'PIX',      'MANUAL',  1, NULL::numeric, 0.00, NULL::text),
    (1025::bigint, 'CREDITO',  'MANUAL',  1, NULL::numeric, 0.00, NULL::text)
) AS dp(numero, forma, provedor, parcelas, valor_recebido, valor_troco, motivo_estorno)
JOIN estabelecimento e ON e.cnpj = '00000000000191'
JOIN pedido pe ON pe.estabelecimento_id = e.id AND pe.numero = dp.numero
JOIN usuario gerente ON LOWER(gerente.email) = 'dev@meraki.com'
JOIN sessao_caixa sc ON sc.estabelecimento_id = e.id AND sc.observacao_abertura = '[DEMO] Turno recente fechado'
WHERE NOT EXISTS (SELECT 1 FROM pagamento_pedido pg WHERE pg.pedido_id = pe.id);

INSERT INTO sequencia_documento (estabelecimento_id, tipo, ultimo_numero)
SELECT e.id, 'PEDIDO', 1025
FROM estabelecimento e
WHERE e.cnpj = '00000000000191'
ON CONFLICT (estabelecimento_id, tipo) DO UPDATE
SET ultimo_numero = GREATEST(sequencia_documento.ultimo_numero, EXCLUDED.ultimo_numero),
    atualizado_em = CURRENT_TIMESTAMP;

-- Movimentações e auditoria do caixa para popular histórico e auditoria do PDV
INSERT INTO movimentacao_caixa (sessao_caixa_id, tipo, valor, motivo, usuario_id, ocorrido_em)
SELECT sc.id, mc.tipo, mc.valor, mc.motivo, op.id, sc.aberta_em + INTERVAL '2 hours'
FROM estabelecimento e
JOIN sessao_caixa sc ON sc.estabelecimento_id = e.id AND sc.observacao_abertura = '[DEMO] Turno recente fechado'
JOIN usuario op ON LOWER(op.email) = 'roberto@meraki.com'
CROSS JOIN (VALUES
    ('REFORCO', 100.00, '[DEMO] Reforço inicial de cédulas para troco'),
    ('SANGRIA', 120.00, '[DEMO] Sangria preventiva de caixa')
) AS mc(tipo, valor, motivo)
WHERE e.cnpj = '00000000000191'
  AND NOT EXISTS (
      SELECT 1 FROM movimentacao_caixa m WHERE m.sessao_caixa_id = sc.id AND m.motivo = mc.motivo
  );

INSERT INTO auditoria_caixa (
    estabelecimento_id, sessao_caixa_id, usuario_id, usuario_nome, origem,
    tipo_evento, entidade, pedido_id, pedido_identificador, valor, motivo,
    autorizado_por_usuario_id, autorizado_por_nome, descricao, ocorrido_em
)
SELECT e.id, sc.id, op.id, op.nome, 'API',
       'DESCONTO_CONCEDIDO', 'PEDIDO', pe.id, pe.identificador_atendimento, pe.desconto,
       '[DEMO] Desconto comercial aprovado para cliente frequente',
       gerente.id, gerente.nome, 'Desconto aplicado no pedido #' || pe.numero, pe.concluido_em
FROM estabelecimento e
JOIN sessao_caixa sc ON sc.estabelecimento_id = e.id AND sc.observacao_abertura = '[DEMO] Turno recente fechado'
JOIN usuario op ON LOWER(op.email) = 'maria@meraki.com'
JOIN usuario gerente ON LOWER(gerente.email) = 'dev@meraki.com'
JOIN pedido pe ON pe.estabelecimento_id = e.id AND pe.numero = 1005
WHERE e.cnpj = '00000000000191'
  AND NOT EXISTS (
      SELECT 1 FROM auditoria_caixa a
      WHERE a.estabelecimento_id = e.id AND a.motivo = '[DEMO] Desconto comercial aprovado para cliente frequente'
  );

-- ─── Financeiro: contas a pagar e receber (ABERTA, PAGA e INATIVA; vencidas, 7d, 30d e histórico 6 meses) ───
INSERT INTO conta_financeira (
    estabelecimento_id, tipo, descricao, valor, vencimento, centro_custo, categoria,
    status, pago_em, valor_pago, observacao, criado_por_usuario_id
)
SELECT e.id, cf.tipo, cf.descricao, cf.valor, cf.vencimento, cf.centro_custo, cf.categoria,
       cf.status, cf.pago_em, cf.valor_pago, cf.observacao, gerente.id
FROM estabelecimento e
JOIN usuario gerente ON LOWER(gerente.email) = 'dev@meraki.com'
CROSS JOIN (VALUES
    -- Contas a PAGAR em aberto (vencida, <= 7d, 8..30d e > 30d)
    ('PAGAR',   'Fornecedor Bebidas Sul — NF 4812',         640.00, CURRENT_DATE - 3,
        'COMPRAS',        'MERCADORIA',       'ABERTA',  NULL::date, NULL::numeric, 'Parcela vencida aguardando conferência de boleto'),
    ('PAGAR',   'Aluguel do imóvel — competência atual',    950.00, CURRENT_DATE + 2,
        'OPERACIONAL',    'DESPESA_FIXA',     'ABERTA',  NULL::date, NULL::numeric, 'Vencimento próximo'),
    ('PAGAR',   'Energia elétrica — câmaras frias',         310.00, CURRENT_DATE + 5,
        'OPERACIONAL',    'DESPESA_FIXA',     'ABERTA',  NULL::date, NULL::numeric, 'Conta mensal da adega'),
    ('PAGAR',   'Honorários contábeis mensais',             380.00, CURRENT_DATE + 12,
        'ADMINISTRATIVO', 'DESPESA_FIXA',     'ABERTA',  NULL::date, NULL::numeric, 'Serviços contábeis e fiscais'),
    ('PAGAR',   'Embalagens e sacolas delivery',            145.00, CURRENT_DATE + 20,
        'VENDAS',         'DESPESA_VARIAVEL', 'ABERTA',  NULL::date, NULL::numeric, 'Reposição mensal de embalagens'),
    ('PAGAR',   'Importadora Vinhos — pedido programado',   820.00, CURRENT_DATE + 45,
        'COMPRAS',        'MERCADORIA',       'ABERTA',  NULL::date, NULL::numeric, 'Previsão acima de 30 dias'),
    -- Conta a PAGAR INATIVA (substitui antigo cancelamento)
    ('PAGAR',   'Compra inativada — fornecedor substituído',290.00, CURRENT_DATE + 8,
        'COMPRAS',        'MERCADORIA',       'INATIVA', NULL::date, NULL::numeric, 'Lançamento substituído após negociação'),
    -- Conta a RECEBER em aberto e baixada
    ('RECEBER', 'Evento corporativo — reserva salão',       520.00, CURRENT_DATE + 10,
        'VENDAS',         NULL::varchar,      'ABERTA',  NULL::date, NULL::numeric, 'Pagamento combinado para entrega do kit'),
    ('RECEBER', 'Degustação privada — sinal recebido',      340.00, (date_trunc('month', CURRENT_DATE))::date,
        'VENDAS',         NULL::varchar,      'PAGA',    (date_trunc('month', CURRENT_DATE))::date, 340.00, 'Recebido via PIX'),
    -- Contas a PAGAR baixadas no mês atual e nos 5 meses anteriores (fluxo de caixa 6 meses e DRE)
    ('PAGAR',   'Internet e sistema PDV — mês atual',       180.00, (date_trunc('month', CURRENT_DATE))::date,
        'ADMINISTRATIVO', 'DESPESA_FIXA',     'PAGA',    (date_trunc('month', CURRENT_DATE))::date, 180.00, 'Débito automático'),
    ('PAGAR',   'Taxas de maquininha e entregas — mês atual', 95.00, (date_trunc('month', CURRENT_DATE))::date,
        'VENDAS',         'DESPESA_VARIAVEL', 'PAGA',    (date_trunc('month', CURRENT_DATE))::date,  95.00, 'Conciliação mensal'),
    ('PAGAR',   'Aluguel — mês anterior',                   950.00, (date_trunc('month', CURRENT_DATE) - INTERVAL '1 month' + INTERVAL '4 days')::date,
        'OPERACIONAL',    'DESPESA_FIXA',     'PAGA',    (date_trunc('month', CURRENT_DATE) - INTERVAL '1 month' + INTERVAL '4 days')::date, 950.00, 'Pago em dia'),
    ('PAGAR',   'Reposição de estoque — mês anterior',      420.00, (date_trunc('month', CURRENT_DATE) - INTERVAL '1 month' + INTERVAL '9 days')::date,
        'COMPRAS',        'MERCADORIA',       'PAGA',    (date_trunc('month', CURRENT_DATE) - INTERVAL '1 month' + INTERVAL '9 days')::date, 420.00, 'Distribuidora'),
    ('PAGAR',   'Aluguel e energia — 2 meses atrás',        880.00, (date_trunc('month', CURRENT_DATE) - INTERVAL '2 months' + INTERVAL '4 days')::date,
        'OPERACIONAL',    'DESPESA_FIXA',     'PAGA',    (date_trunc('month', CURRENT_DATE) - INTERVAL '2 months' + INTERVAL '4 days')::date, 880.00, 'Pago em dia'),
    ('PAGAR',   'Embalagens — 3 meses atrás',               160.00, (date_trunc('month', CURRENT_DATE) - INTERVAL '3 months' + INTERVAL '10 days')::date,
        'VENDAS',         'DESPESA_VARIAVEL', 'PAGA',    (date_trunc('month', CURRENT_DATE) - INTERVAL '3 months' + INTERVAL '10 days')::date, 160.00, 'Pago'),
    ('PAGAR',   'Aluguel — 4 meses atrás',                  900.00, (date_trunc('month', CURRENT_DATE) - INTERVAL '4 months' + INTERVAL '4 days')::date,
        'OPERACIONAL',    'DESPESA_FIXA',     'PAGA',    (date_trunc('month', CURRENT_DATE) - INTERVAL '4 months' + INTERVAL '4 days')::date, 900.00, 'Pago'),
    ('PAGAR',   'Reposição — 5 meses atrás',                450.00, (date_trunc('month', CURRENT_DATE) - INTERVAL '5 months' + INTERVAL '8 days')::date,
        'COMPRAS',        'MERCADORIA',       'PAGA',    (date_trunc('month', CURRENT_DATE) - INTERVAL '5 months' + INTERVAL '8 days')::date, 450.00, 'Pago')
) AS cf(tipo, descricao, valor, vencimento, centro_custo, categoria, status, pago_em, valor_pago, observacao)
WHERE e.cnpj = '00000000000191'
  AND NOT EXISTS (
      SELECT 1 FROM conta_financeira c
      WHERE c.estabelecimento_id = e.id AND c.descricao = cf.descricao
  );
