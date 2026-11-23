-- Tokens de uso único para a recuperação de senha por e-mail.
-- Somente o hash SHA-256 do token é persistido; o valor original existe apenas no link enviado.
CREATE TABLE IF NOT EXISTS token_recuperacao_senha (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expira_em TIMESTAMP NOT NULL,
    utilizado_em TIMESTAMP,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_token_recuperacao_senha_hash UNIQUE (token_hash),

    CONSTRAINT fk_token_recuperacao_senha_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_token_recuperacao_senha_usuario_criado_em
    ON token_recuperacao_senha (usuario_id, criado_em);
