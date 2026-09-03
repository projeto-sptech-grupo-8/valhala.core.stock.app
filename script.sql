-- Adega Meraki
-- Estrutura PostgreSQL baseada no DER do projeto.
-- Execute este arquivo conectado ao banco de dados da aplicação.

BEGIN;

-- Utilizada para gerar UUIDs, senha inicial aleatória e hash BCrypt.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ================================================================
-- CONTROLE DE ACESSO
-- ================================================================

CREATE TABLE IF NOT EXISTS perfil (
    id_perfil UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL,
    descricao TEXT,
    data_criacao TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_perfil_nome UNIQUE (nome)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_perfil_nome_lower
    ON perfil (LOWER(nome));

CREATE TABLE IF NOT EXISTS funcionalidades (
    id_funcionalidade UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL,
    descricao TEXT,
    codigo VARCHAR(45) NOT NULL,

    CONSTRAINT uq_funcionalidades_codigo UNIQUE (codigo)
);

CREATE TABLE IF NOT EXISTS perfil_funcionalidades (
    perfil_id UUID NOT NULL,
    funcionalidade_id UUID NOT NULL,

    CONSTRAINT pk_perfil_funcionalidades
        PRIMARY KEY (perfil_id, funcionalidade_id),

    CONSTRAINT fk_perfil_funcionalidades_perfil
        FOREIGN KEY (perfil_id)
        REFERENCES perfil (id_perfil)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_perfil_funcionalidades_funcionalidade
        FOREIGN KEY (funcionalidade_id)
        REFERENCES funcionalidades (id_funcionalidade)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_perfil_funcionalidades_funcionalidade_id
    ON perfil_funcionalidades (funcionalidade_id);

-- ================================================================
-- USUÁRIOS
-- ================================================================

CREATE TABLE IF NOT EXISTS usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    telefone VARCHAR(13),
    senha VARCHAR(255) NOT NULL,
    perfil_id UUID NOT NULL,
    status BOOLEAN NOT NULL DEFAULT TRUE,
    data_ultima_atualizacao TIMESTAMP WITHOUT TIME ZONE,
    data_criacao TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_usuario_perfil
        FOREIGN KEY (perfil_id)
        REFERENCES perfil (id_perfil)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

-- Atualiza bancos criados com uma versão anterior deste script.
ALTER TABLE usuario
    ADD COLUMN IF NOT EXISTS data_ultima_atualizacao TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE usuario
    ADD COLUMN IF NOT EXISTS telefone VARCHAR(13);

-- Garante unicidade do e-mail sem diferenciar maiúsculas/minúsculas.
CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_email_lower
    ON usuario (LOWER(email));

CREATE INDEX IF NOT EXISTS idx_usuario_perfil_id
    ON usuario (perfil_id);

CREATE INDEX IF NOT EXISTS idx_usuario_status
    ON usuario (status);

-- Mantém data_ultima_atualizacao sincronizada nas alterações de usuário.
CREATE OR REPLACE FUNCTION atualizar_data_usuario()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.data_ultima_atualizacao := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_usuario_data_ultima_atualizacao ON usuario;

CREATE TRIGGER trg_usuario_data_ultima_atualizacao
BEFORE UPDATE ON usuario
FOR EACH ROW
EXECUTE FUNCTION atualizar_data_usuario();

-- ================================================================
-- CATEGORIAS E PRODUTOS
-- ================================================================

CREATE TABLE IF NOT EXISTS categoria (
    id_categoria UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL,
    descricao TEXT,
    data_criacao TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_categoria_nome UNIQUE (nome)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_categoria_nome_lower
    ON categoria (LOWER(nome));

CREATE TABLE IF NOT EXISTS produtos (
    id_produto UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL,
    descricao TEXT,
    codigo_fiscal VARCHAR(45),
    preco_unitario NUMERIC(10, 2) NOT NULL DEFAULT 0,
    quantidade_estoque INTEGER NOT NULL DEFAULT 0,
    categoria_id UUID NOT NULL,
    status BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP WITHOUT TIME ZONE,
    estoque_minimo INTEGER NOT NULL DEFAULT 0,
    codigo_produto VARCHAR(15),
    codigo_barras VARCHAR(45),

    CONSTRAINT fk_produtos_categoria
        FOREIGN KEY (categoria_id)
        REFERENCES categoria (id_categoria)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT uq_produtos_codigo_produto UNIQUE (codigo_produto),
    CONSTRAINT uq_produtos_codigo_barras UNIQUE (codigo_barras),
    CONSTRAINT ck_produtos_preco_unitario CHECK (preco_unitario >= 0),
    CONSTRAINT ck_produtos_quantidade_estoque CHECK (quantidade_estoque >= 0),
    CONSTRAINT ck_produtos_estoque_minimo CHECK (estoque_minimo >= 0)
);

CREATE INDEX IF NOT EXISTS idx_produtos_categoria_id
    ON produtos (categoria_id);

CREATE INDEX IF NOT EXISTS idx_produtos_nome
    ON produtos (nome);

CREATE INDEX IF NOT EXISTS idx_produtos_status
    ON produtos (status);

-- Mantém data_atualizacao sincronizada nas alterações de produto.
CREATE OR REPLACE FUNCTION atualizar_data_produto()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.data_atualizacao := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_produtos_data_atualizacao ON produtos;

CREATE TRIGGER trg_produtos_data_atualizacao
BEFORE UPDATE ON produtos
FOR EACH ROW
EXECUTE FUNCTION atualizar_data_produto();

-- ================================================================
-- FORNECEDORES
-- ================================================================

CREATE TABLE IF NOT EXISTS fornecedor (
    id_fornecedor UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL,
    contato VARCHAR(255),
    status BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fornecedor_nome
    ON fornecedor (nome);

CREATE INDEX IF NOT EXISTS idx_fornecedor_status
    ON fornecedor (status);

CREATE TABLE IF NOT EXISTS fornecedor_produtos (
    fornecedor_id UUID NOT NULL,
    produto_id UUID NOT NULL,

    CONSTRAINT pk_fornecedor_produtos
        PRIMARY KEY (fornecedor_id, produto_id),

    CONSTRAINT fk_fornecedor_produtos_fornecedor
        FOREIGN KEY (fornecedor_id)
        REFERENCES fornecedor (id_fornecedor)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_fornecedor_produtos_produto
        FOREIGN KEY (produto_id)
        REFERENCES produtos (id_produto)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_fornecedor_produtos_produto_id
    ON fornecedor_produtos (produto_id);

-- ================================================================
-- MOVIMENTAÇÕES DE ESTOQUE
-- ================================================================

CREATE TABLE IF NOT EXISTS movimentacao (
    id_movimentacao UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_hora TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tipo VARCHAR(45) NOT NULL,
    quantidade INTEGER NOT NULL,
    motivo_descricao TEXT,
    produto_id UUID NOT NULL,
    usuario_id UUID NOT NULL,

    CONSTRAINT fk_movimentacao_produto
        FOREIGN KEY (produto_id)
        REFERENCES produtos (id_produto)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_movimentacao_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT ck_movimentacao_quantidade CHECK (quantidade > 0)
);

CREATE INDEX IF NOT EXISTS idx_movimentacao_produto_id
    ON movimentacao (produto_id);

CREATE INDEX IF NOT EXISTS idx_movimentacao_usuario_id
    ON movimentacao (usuario_id);

CREATE INDEX IF NOT EXISTS idx_movimentacao_data_hora
    ON movimentacao (data_hora);

CREATE INDEX IF NOT EXISTS idx_movimentacao_tipo
    ON movimentacao (tipo);

-- ================================================================
-- CARGA INICIAL DE CONTROLE DE ACESSO
-- ================================================================

INSERT INTO perfil (nome, descricao)
SELECT 'Gerente', 'Gerencia usuários, estoque e operações da adega'
WHERE NOT EXISTS (
    SELECT 1 FROM perfil WHERE LOWER(nome) = 'gerente'
);

INSERT INTO perfil (nome, descricao)
SELECT 'Atendente', 'Realiza atendimento e operações de estoque autorizadas'
WHERE NOT EXISTS (
    SELECT 1 FROM perfil WHERE LOWER(nome) = 'atendente'
);

INSERT INTO funcionalidades (nome, descricao, codigo)
VALUES
    ('Gerenciar usuários', 'Permite cadastrar e administrar usuários', 'GERENCIAR_USUARIOS'),
    ('Gerenciar estoque', 'Permite administrar produtos, categorias e fornecedores', 'GERENCIAR_ESTOQUE'),
    ('Movimentar estoque', 'Permite registrar entradas, saídas e ajustes', 'MOVIMENTAR_ESTOQUE'),
    ('Visualizar estoque', 'Permite consultar produtos e quantidades', 'VISUALIZAR_ESTOQUE')
ON CONFLICT (codigo) DO NOTHING;

-- O gerente recebe todas as funcionalidades cadastradas.
INSERT INTO perfil_funcionalidades (perfil_id, funcionalidade_id)
SELECT p.id_perfil, f.id_funcionalidade
FROM perfil p
CROSS JOIN funcionalidades f
WHERE LOWER(p.nome) = 'gerente'
ON CONFLICT (perfil_id, funcionalidade_id) DO NOTHING;

-- O atendente recebe somente consulta e movimentação de estoque.
INSERT INTO perfil_funcionalidades (perfil_id, funcionalidade_id)
SELECT p.id_perfil, f.id_funcionalidade
FROM perfil p
JOIN funcionalidades f
    ON f.codigo IN ('MOVIMENTAR_ESTOQUE', 'VISUALIZAR_ESTOQUE')
WHERE LOWER(p.nome) = 'atendente'
ON CONFLICT (perfil_id, funcionalidade_id) DO NOTHING;

-- ================================================================
-- PRIMEIRO GERENTE
-- ================================================================
-- Na primeira execução, uma senha aleatória é gerada e exibida na aba
-- Messages/Mensagens do cliente PostgreSQL. Guarde-a para realizar o login.
-- O bloco não altera a senha se o gerente inicial já existir.

DO $$
DECLARE
    v_email CONSTANT VARCHAR(255) := 'gerente@meraki.com';
    v_nome CONSTANT VARCHAR(100) := 'Gerente Inicial';
    v_senha_temporaria TEXT;
    v_usuario_id UUID;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM usuario
        WHERE LOWER(email) = LOWER(v_email)
    ) THEN
        RAISE NOTICE 'O gerente inicial % já existe; nenhuma senha foi alterada.', v_email;
        RETURN;
    END IF;

    v_senha_temporaria := ENCODE(gen_random_bytes(18), 'base64');

    INSERT INTO usuario (
        nome,
        email,
        senha,
        perfil_id,
        status
    )
    SELECT
        v_nome,
        LOWER(v_email),
        crypt(v_senha_temporaria, gen_salt('bf', 12)),
        p.id_perfil,
        TRUE
    FROM perfil p
    WHERE LOWER(p.nome) = 'gerente'
    RETURNING id INTO v_usuario_id;

    IF v_usuario_id IS NULL THEN
        RAISE EXCEPTION 'Não foi possível localizar o perfil Gerente.';
    END IF;

    RAISE NOTICE 'Gerente inicial criado com sucesso.';
    RAISE NOTICE 'E-mail: %', v_email;
    RAISE NOTICE 'Senha temporária: %', v_senha_temporaria;
    RAISE NOTICE 'Guarde a senha agora; ela não poderá ser recuperada depois.';
END;
$$;

COMMIT;

-- Consultas úteis após a instalação:
-- SELECT id_perfil, nome, descricao FROM perfil ORDER BY nome;
-- SELECT id, nome, email, telefone, status, perfil_id,
--        data_ultima_atualizacao, data_criacao
-- FROM usuario
-- ORDER BY data_criacao;
