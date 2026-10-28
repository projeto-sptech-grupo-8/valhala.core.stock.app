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
INSERT INTO produto (estabelecimento_id, categoria_id, sku, codigo_barras, nome, tipo, unidade_medida,
                     preco_custo, preco_venda, fracionado, volume_embalagem_ml)
SELECT e.id, c.id, p.sku, p.ean, p.nome, 'PADRAO', p.unidade, p.custo, p.preco, p.volume IS NOT NULL, p.volume
FROM estabelecimento e
CROSS JOIN (VALUES
    ('BEB-001', '7891000000014', 'Heineken 600ml',              'Cerveja',    'un',  9.50,   14.90,  600),
    ('BEB-002', '7891000000021', 'Stella Artois 350ml',         'Cerveja',    'un',  6.20,   9.90,   350),
    ('BEB-003', '7891000000038', 'Budweiser 350ml',             'Cerveja',    'un',  5.80,   8.90,   350),
    ('BEB-004', '7891000000045', 'Corona Extra 355ml',          'Cerveja',    'un',  8.40,   13.90,  355),
    ('BEB-005', '7891000000052', 'Vinho Tinto Reserva 750ml',   'Vinho',      'un',  54.00,  89.00,  750),
    ('BEB-006', '7891000000069', 'Vinho Branco Seco 750ml',     'Vinho',      'un',  44.00,  72.00,  750),
    ('BEB-007', '7891000000076', 'Espumante Brut 750ml',        'Vinho',      'un',  62.00,  98.00,  750),
    ('BEB-008', '7891000000083', 'Jack Daniel''s 1L',           'Destilados', 'un',  130.00, 189.00, 1000),
    ('BEB-009', '7891000000090', 'Vodka Absolut 750ml',         'Destilados', 'un',  58.00,  84.00,  750),
    ('BEB-010', '7891000000106', 'Gin Amazônico Premium 750ml', 'Gin',        'un',  78.00,  119.00, 750),
    ('BEB-011', '7891000000113', 'Tanqueray London Dry 750ml',  'Gin',        'un',  90.00,  134.00, 750),
    ('BEB-012', '7891000000120', 'Red Bull 250ml',              'Sem Álcool', 'un',  8.00,   12.90,  250),
    ('BEB-013', '7891000000137', 'Água Mineral 500ml',          'Sem Álcool', 'un',  2.10,   4.50,   500),
    ('BEB-014', '7891000000144', 'Combo Cerveja ×6',            'Combos',     'kit', 34.00,  49.90,  NULL)
) AS p(sku, ean, nome, cat, unidade, custo, preco, volume)
JOIN categoria c ON c.estabelecimento_id = e.id AND LOWER(c.nome) = LOWER(p.cat)
WHERE e.cnpj = '00000000000191'
ON CONFLICT (estabelecimento_id, sku) DO NOTHING;

-- ─── Estoque inicial (com a movimentação ENTRADA_INICIAL correspondente) ───
-- Só gera movimentação para estoques criados agora, preservando o histórico real em reexecuções.
WITH novos AS (
    INSERT INTO estoque (produto_id, quantidade_atual, estoque_minimo)
    SELECT pr.id, s.qtd, s.minimo
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
