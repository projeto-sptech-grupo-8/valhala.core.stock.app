-- Catálogo mínimo de funcionalidades da administração de usuários.
INSERT INTO funcionalidade (codigo, nome, descricao)
VALUES
    ('USUARIOS_CRIAR', 'Criar usuários', 'Permite cadastrar usuários'),
    ('USUARIOS_VISUALIZAR', 'Visualizar usuários', 'Permite consultar usuários'),
    ('USUARIOS_EDITAR', 'Editar usuários', 'Permite editar usuários'),
    ('USUARIOS_EXCLUIR', 'Excluir usuários', 'Permite excluir usuários'),
    ('PERFIS_GERENCIAR', 'Gerenciar perfis', 'Permite criar e configurar perfis'),
    ('PERMISSOES_GERENCIAR', 'Gerenciar permissões', 'Permite definir exceções por usuário')
ON CONFLICT (codigo) DO UPDATE
SET nome = EXCLUDED.nome,
    descricao = EXCLUDED.descricao;

-- Perfis chamados Gerente recebem o acesso administrativo completo.
INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT perfil.id, funcionalidade.id
FROM perfil
CROSS JOIN funcionalidade
WHERE UPPER(BTRIM(perfil.nome)) = 'GERENTE'
ON CONFLICT DO NOTHING;
