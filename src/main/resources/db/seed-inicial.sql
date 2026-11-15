-- Carga inicial idempotente para PostgreSQL.
-- Altere os dados do estabelecimento e a senha antes de usar em produção.
BEGIN;

INSERT INTO estabelecimento (razao_social, nome_fantasia, cnpj, email_contato, ativo)
SELECT 'Valhalla Comercio LTDA', 'Valhalla', '00000000000191',
       'admin@valhalla.local', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM estabelecimento WHERE cnpj = '00000000000191'
);

-- Configuração inicial do caixa. O bloco tolera bancos antigos que ainda não
-- executaram as migrations V9+; nesse caso a configuração será criada depois.
DO $$
BEGIN
    IF to_regclass('public.configuracao_caixa') IS NOT NULL THEN
        INSERT INTO configuracao_caixa (estabelecimento_id)
        SELECT id FROM estabelecimento WHERE cnpj = '00000000000191'
        ON CONFLICT (estabelecimento_id) DO NOTHING;
    END IF;
END $$;

INSERT INTO perfil (estabelecimento_id, nome, descricao)
SELECT e.id, p.nome, p.descricao
FROM estabelecimento e
CROSS JOIN (VALUES
    ('Gerente', 'Administra usuários, catálogo, estoque e operações'),
    ('Atendente', 'Consulta e movimenta estoque conforme permitido')
) AS p(nome, descricao)
WHERE e.cnpj = '00000000000191'
ON CONFLICT (estabelecimento_id, nome) DO UPDATE
SET descricao = EXCLUDED.descricao, atualizado_em = CURRENT_TIMESTAMP;

INSERT INTO funcionalidade (codigo, nome, descricao)
VALUES
    ('USUARIOS_CRIAR', 'Criar usuários', 'Permite cadastrar usuários'),
    ('USUARIOS_VISUALIZAR', 'Visualizar usuários', 'Permite consultar usuários'),
    ('USUARIOS_EDITAR', 'Editar usuários', 'Permite editar usuários'),
    ('USUARIOS_EXCLUIR', 'Excluir usuários', 'Permite excluir usuários'),
    ('PERFIS_GERENCIAR', 'Gerenciar perfis', 'Permite criar e configurar perfis'),
    ('PERMISSOES_GERENCIAR', 'Gerenciar permissões', 'Permite definir exceções por usuário'),
    ('GERENCIAR_ESTOQUE', 'Gerenciar estoque', 'Administrar catálogo e estoque'),
    ('MOVIMENTAR_ESTOQUE', 'Movimentar estoque', 'Registrar entradas, saídas e ajustes'),
    ('VISUALIZAR_ESTOQUE', 'Visualizar estoque', 'Consultar produtos e quantidades'),
    ('CAIXA_OPERAR', 'Operar caixa', 'Criar e editar pedidos, registrar dinheiro e iniciar cobranças na Point'),
    ('CAIXA_FECHAR', 'Fechar caixa', 'Fechar a própria sessão de caixa após a conferência'),
    ('CAIXA_VISUALIZAR', 'Visualizar caixa', 'Consultar sessões, vendas, pagamentos e configuração do caixa'),
    ('CAIXA_CONFIGURAR', 'Configurar caixa', 'Configurar terminal, formas de pagamento, parcelamento e políticas comerciais'),
    ('CAIXA_CANCELAR', 'Cancelar no caixa', 'Cancelar pedidos e cobranças ainda não aprovadas'),
    ('CAIXA_ESTORNAR', 'Estornar pagamento', 'Solicitar e registrar a devolução de pagamento aprovado'),
    ('CAIXA_AUTORIZAR_DESCONTO', 'Autorizar desconto', 'Autorizar desconto acima do limite configurado'),
    ('CAIXA_RESOLVER_DIVERGENCIA', 'Resolver divergência', 'Resolver divergência de caixa e aprovar o fechamento')
ON CONFLICT (codigo) DO UPDATE
SET nome = EXCLUDED.nome, descricao = EXCLUDED.descricao;

-- O gerente recebe todas as funcionalidades.
INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT p.id, f.id
FROM perfil p
JOIN estabelecimento e ON e.id = p.estabelecimento_id
CROSS JOIN funcionalidade f
WHERE e.cnpj = '00000000000191' AND p.nome = 'Gerente'
ON CONFLICT DO NOTHING;

-- O atendente recebe consulta, movimentação e operação do próprio caixa.
INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT p.id, f.id
FROM perfil p
JOIN estabelecimento e ON e.id = p.estabelecimento_id
JOIN funcionalidade f ON f.codigo IN (
    'MOVIMENTAR_ESTOQUE',
    'VISUALIZAR_ESTOQUE',
    'CAIXA_OPERAR',
    'CAIXA_FECHAR',
    'CAIXA_VISUALIZAR'
)
WHERE e.cnpj = '00000000000191' AND p.nome = 'Atendente'
ON CONFLICT DO NOTHING;

-- Troque 'Senha@123' antes de executar em produção.
INSERT INTO usuario (estabelecimento_id, perfil_id, nome, email, senha_hash, status)
SELECT e.id, p.id, 'Administrador Inicial', 'admin@valhalla.local',
       crypt('Senha@123', gen_salt('bf', 12)), 'ATIVO'
FROM estabelecimento e
JOIN perfil p ON p.estabelecimento_id = e.id AND p.nome = 'Gerente'
WHERE e.cnpj = '00000000000191'
  AND NOT EXISTS (
      SELECT 1 FROM usuario WHERE LOWER(email) = 'admin@valhalla.local'
  );

COMMIT;
