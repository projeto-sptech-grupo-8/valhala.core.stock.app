-- Dados de demonstração para desenvolvimento (substitui o antigo mockServer do front).
-- Aplicado por DemoDataSeeder somente com o perfil `dev`; nunca em produção.
-- NÃO é uma migração Flyway: dados de demonstração não fazem parte do schema.
-- Script idempotente: pode rodar a cada inicialização sem duplicar registros.
-- Todos os usuários usam a senha 'Senha@123'.
-- Requer a extensão pgcrypto (criada por db/script.sql) para crypt()/gen_salt().
--
-- Fora do escopo do back hoje (não semeado): caixa, pedidos, notas fiscais e financeiro.

INSERT INTO estabelecimento (razao_social, nome_fantasia, cnpj, email_contato, ativo)
SELECT 'Valhalla Comercio LTDA', 'Valhalla', '00000000000191', 'admin@valhalla.local', TRUE
WHERE NOT EXISTS (SELECT 1 FROM estabelecimento WHERE cnpj = '00000000000191');

-- ─── Perfis ───
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
    ('Estoquista', 'VISUALIZAR_ESTOQUE'),
    ('Estoquista', 'MOVIMENTAR_ESTOQUE'),
    ('Estoquista', 'GERENCIAR_ESTOQUE'),
    ('Financeiro', 'VISUALIZAR_ESTOQUE'),
    ('Consulta',   'VISUALIZAR_ESTOQUE')
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

-- ─── Categorias ───
INSERT INTO categoria (estabelecimento_id, nome, descricao)
SELECT e.id, c.nome, ''
FROM estabelecimento e
CROSS JOIN (VALUES
    ('Cerveja'), ('Vinho'), ('Destilados'), ('Gin'), ('Combos'), ('Sem Álcool'), ('Drinks')
) AS c(nome)
WHERE e.cnpj = '00000000000191'
ON CONFLICT DO NOTHING;

-- ─── Produtos padrão (fracionado quando há volume de embalagem) ───
INSERT INTO produto (estabelecimento_id, categoria_id, sku, nome, tipo, unidade_medida,
                     preco_custo, preco_venda, fracionado, volume_embalagem_ml)
SELECT e.id, c.id, p.sku, p.nome, 'PADRAO', p.unidade, p.custo, p.preco, p.volume IS NOT NULL, p.volume
FROM estabelecimento e
CROSS JOIN (VALUES
    ('BEB-001', 'Heineken 600ml',              'Cerveja',    'un',  9.50,   14.90,  600),
    ('BEB-002', 'Stella Artois 350ml',         'Cerveja',    'un',  6.20,   9.90,   350),
    ('BEB-003', 'Budweiser 350ml',             'Cerveja',    'un',  5.80,   8.90,   350),
    ('BEB-004', 'Corona Extra 355ml',          'Cerveja',    'un',  8.40,   13.90,  355),
    ('BEB-005', 'Vinho Tinto Reserva 750ml',   'Vinho',      'un',  54.00,  89.00,  750),
    ('BEB-006', 'Vinho Branco Seco 750ml',     'Vinho',      'un',  44.00,  72.00,  750),
    ('BEB-007', 'Espumante Brut 750ml',        'Vinho',      'un',  62.00,  98.00,  750),
    ('BEB-008', 'Jack Daniel''s 1L',           'Destilados', 'un',  130.00, 189.00, 1000),
    ('BEB-009', 'Vodka Absolut 750ml',         'Destilados', 'un',  58.00,  84.00,  750),
    ('BEB-010', 'Gin Amazônico Premium 750ml', 'Gin',        'un',  78.00,  119.00, 750),
    ('BEB-011', 'Tanqueray London Dry 750ml',  'Gin',        'un',  90.00,  134.00, 750),
    ('BEB-012', 'Red Bull 250ml',              'Sem Álcool', 'un',  8.00,   12.90,  250),
    ('BEB-013', 'Água Mineral 500ml',          'Sem Álcool', 'un',  2.10,   4.50,   500),
    ('BEB-014', 'Combo Cerveja ×6',            'Combos',     'kit', 34.00,  49.90,  NULL)
) AS p(sku, nome, cat, unidade, custo, preco, volume)
JOIN categoria c ON c.estabelecimento_id = e.id AND LOWER(c.nome) = LOWER(p.cat)
WHERE e.cnpj = '00000000000191'
ON CONFLICT (estabelecimento_id, sku) DO NOTHING;

-- ─── Estoque inicial (com a movimentação ENTRADA_INICIAL correspondente) ───
-- Só gera movimentação para estoques criados agora, preservando o histórico real em reexecuções.
WITH novos AS (
    INSERT INTO estoque (produto_id, quantidade_atual, estoque_minimo)
    SELECT pr.id,
           s.qtd * COALESCE(pr.volume_embalagem_ml, 1),
           s.minimo * COALESCE(pr.volume_embalagem_ml, 1)
    FROM (VALUES
        ('BEB-001', 48, 24), ('BEB-002', 8, 36), ('BEB-003', 36, 24), ('BEB-004', 24, 12),
        ('BEB-005', 12, 6),  ('BEB-006', 18, 6), ('BEB-007', 4, 6),   ('BEB-008', 3, 6),
        ('BEB-009', 7, 4),   ('BEB-010', 5, 4),  ('BEB-011', 3, 4),   ('BEB-012', 72, 48),
        ('BEB-013', 96, 48), ('BEB-014', 20, 10)
    ) AS s(sku, qtd, minimo)
    JOIN estabelecimento e ON e.cnpj = '00000000000191'
    JOIN produto pr ON pr.estabelecimento_id = e.id AND pr.sku = s.sku
    ON CONFLICT (produto_id) DO NOTHING
    RETURNING id, quantidade_atual
)
INSERT INTO movimentacao_estoque (estoque_id, usuario_id, tipo, quantidade, saldo_anterior, saldo_posterior, motivo)
SELECT n.id, u.id, 'ENTRADA_INICIAL', n.quantidade_atual, 0, n.quantidade_atual, 'Carga de demonstração'
FROM novos n
JOIN usuario u ON LOWER(u.email) = 'dev@meraki.com';

-- ─── Lotes e validades de demonstração (FEFO) ───
-- Se o banco já tinha apenas o lote genérico SEM-LOTE gerado automaticamente (sem movimentações posteriores),
-- substitui pelos lotes de demonstração para popular a tela de Validades e Lotes.
DELETE FROM movimentacao_estoque_lote ml
USING lote_estoque l, estoque es, produto pr, estabelecimento e
WHERE ml.lote_id = l.id
  AND l.estoque_id = es.id
  AND es.produto_id = pr.id
  AND pr.estabelecimento_id = e.id
  AND e.cnpj = '00000000000191'
  AND l.codigo = 'SEM-LOTE'
  AND pr.sku IN ('BEB-001', 'BEB-002', 'BEB-003', 'BEB-004', 'BEB-012', 'BEB-013', 'BEB-014')
  AND NOT EXISTS (SELECT 1 FROM lote_estoque l2 WHERE l2.estoque_id = es.id AND l2.codigo <> 'SEM-LOTE')
  AND NOT EXISTS (SELECT 1 FROM movimentacao_estoque m WHERE m.estoque_id = es.id AND m.tipo <> 'ENTRADA_INICIAL');

DELETE FROM lote_estoque l
USING estoque es, produto pr, estabelecimento e
WHERE l.estoque_id = es.id
  AND es.produto_id = pr.id
  AND pr.estabelecimento_id = e.id
  AND e.cnpj = '00000000000191'
  AND l.codigo = 'SEM-LOTE'
  AND pr.sku IN ('BEB-001', 'BEB-002', 'BEB-003', 'BEB-004', 'BEB-012', 'BEB-013', 'BEB-014')
  AND NOT EXISTS (SELECT 1 FROM lote_estoque l2 WHERE l2.estoque_id = es.id AND l2.codigo <> 'SEM-LOTE')
  AND NOT EXISTS (SELECT 1 FROM movimentacao_estoque m WHERE m.estoque_id = es.id AND m.tipo <> 'ENTRADA_INICIAL');

-- Cobre lotes vencidos, a vencer em até 7 dias, entre 8 e 30 dias, no prazo (> 30 dias),
-- sem validade (SEM-LOTE) e lotes já zerados para filtro de histórico.
-- A soma dos lotes de cada produto bate exatamente com estoque.quantidade_atual (na unidade-base).
INSERT INTO lote_estoque (estoque_id, codigo, validade, quantidade_atual)
SELECT es.id,
       lt.codigo,
       CURRENT_DATE + lt.dias_validade::int,
       lt.qtd * COALESCE(pr.volume_embalagem_ml, 1)
FROM (VALUES
    -- BEB-001: Heineken 600ml (total = 48 un = 28.800 ml)
    ('BEB-001', 'L2607Z', -15,  0),
    ('BEB-001', 'L2609A',  12, 18),
    ('BEB-001', 'L2610B',  75, 30),
    -- BEB-002: Stella Artois 350ml (total = 8 un = 2.800 ml)
    ('BEB-002', 'ST0815',   3,  3),
    ('BEB-002', 'ST0926',  40,  5),
    -- BEB-003: Budweiser 350ml (total = 36 un = 12.600 ml)
    ('BEB-003', 'BD0812',  -3,  6),
    ('BEB-003', 'BD0905',  25, 12),
    ('BEB-003', 'BD0920', 110, 18),
    -- BEB-004: Corona Extra 355ml (total = 24 un = 8.520 ml)
    ('BEB-004', 'CR0801',   5,  8),
    ('BEB-004', 'CR0915',  90, 16),
    -- BEB-005 a BEB-011: Vinhos, Destilados e Gins (sem validade definida)
    ('BEB-005', 'SEM-LOTE', NULL, 12),
    ('BEB-006', 'SEM-LOTE', NULL, 18),
    ('BEB-007', 'SEM-LOTE', NULL,  4),
    ('BEB-008', 'SEM-LOTE', NULL,  3),
    ('BEB-009', 'SEM-LOTE', NULL,  7),
    ('BEB-010', 'SEM-LOTE', NULL,  5),
    ('BEB-011', 'SEM-LOTE', NULL,  3),
    -- BEB-012: Red Bull 250ml (total = 72 un = 18.000 ml)
    ('BEB-012', 'RB0710',  -8,  4),
    ('BEB-012', 'RB0830',  20, 20),
    ('BEB-012', 'RB0920', 160, 48),
    -- BEB-013: Água Mineral 500ml (total = 96 un = 48.000 ml)
    ('BEB-013', 'AG0805',   6, 16),
    ('BEB-013', 'AG0901', 200, 80),
    -- BEB-014: Combo Cerveja ×6 (total = 20 kits)
    ('BEB-014', 'CB0801', -20,  0),
    ('BEB-014', 'CB0911',  28, 20)
) AS lt(sku, codigo, dias_validade, qtd)
JOIN estabelecimento e ON e.cnpj = '00000000000191'
JOIN produto pr ON pr.estabelecimento_id = e.id AND pr.sku = lt.sku
JOIN estoque es ON es.produto_id = pr.id
WHERE NOT EXISTS (SELECT 1 FROM lote_estoque l WHERE l.estoque_id = es.id)
ON CONFLICT (estoque_id, codigo) DO NOTHING;

-- Vincula os lotes iniciais (com saldo > 0) à movimentação ENTRADA_INICIAL correspondente.
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

-- ─── Drinks (sem estoque próprio; consomem os ingredientes) ───
INSERT INTO produto (estabelecimento_id, categoria_id, sku, nome, tipo, unidade_medida, preco_custo, preco_venda)
SELECT e.id, c.id, d.sku, d.nome, 'DRINK', d.unidade, 0, d.preco
FROM estabelecimento e
CROSS JOIN (VALUES
    ('DRK-001', 'Copo Jack Rocks',      'copo', 28.00),
    ('DRK-002', 'Gin Tônica Premium',   'copo', 32.00),
    ('DRK-003', 'Caipirinha de Vodka',  'copo', 24.00),
    ('DRK-004', 'Taça Vinho da Casa',   'taça', 22.00)
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
    ('DRK-004', 'BEB-005', 150)
) AS r(drink, ingrediente, quantidade) ON TRUE
JOIN produto d ON d.estabelecimento_id = e.id AND d.sku = r.drink
JOIN produto i ON i.estabelecimento_id = e.id AND i.sku = r.ingrediente
WHERE e.cnpj = '00000000000191'
ON CONFLICT (drink_id, produto_id) DO NOTHING;

-- ─── Taxas de referência ativas para simulação em ambiente dev ───
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
