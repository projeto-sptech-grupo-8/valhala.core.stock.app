-- O módulo de estoque exige permissões próprias, inclusive para instalações
-- que já existiam antes do seed inicial.
INSERT INTO funcionalidade (codigo, nome, descricao)
VALUES
    ('GERENCIAR_ESTOQUE', 'Gerenciar estoque', 'Administrar categorias e produtos'),
    ('MOVIMENTAR_ESTOQUE', 'Movimentar estoque', 'Registrar entradas, saídas e ajustes'),
    ('VISUALIZAR_ESTOQUE', 'Visualizar estoque', 'Consultar produtos, categorias e quantidades')
ON CONFLICT (codigo) DO UPDATE
SET nome = EXCLUDED.nome,
    descricao = EXCLUDED.descricao;

-- Perfis Gerente existentes recebem as novas permissões do módulo.
INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT perfil.id, funcionalidade.id
FROM perfil
CROSS JOIN funcionalidade
WHERE UPPER(BTRIM(perfil.nome)) = 'GERENTE'
  AND funcionalidade.codigo IN (
      'GERENCIAR_ESTOQUE',
      'MOVIMENTAR_ESTOQUE',
      'VISUALIZAR_ESTOQUE'
  )
ON CONFLICT DO NOTHING;

-- A API trata nomes de categoria sem diferenciar maiúsculas e minúsculas;
-- o banco deve aplicar a mesma regra para evitar duplicidade concorrente.
CREATE UNIQUE INDEX IF NOT EXISTS uq_categoria_nome_estabelecimento_lower
    ON categoria (estabelecimento_id, LOWER(nome));
