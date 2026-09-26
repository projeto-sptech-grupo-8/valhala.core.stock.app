-- A busca de perfil na API é case-insensitive; o banco deve preservar a mesma regra.
CREATE UNIQUE INDEX IF NOT EXISTS uq_perfil_nome_estabelecimento_lower
    ON perfil (estabelecimento_id, LOWER(nome));
