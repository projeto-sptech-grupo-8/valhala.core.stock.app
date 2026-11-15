-- Estrutura de caixa: configuração por estabelecimento, sessões, movimentações
-- e trilha de auditoria. As tabelas comerciais de pedido e pagamento já existem
-- no script base e são evoluídas nas migrations seguintes.
--
-- Decisões de modelagem:
--   * UUID para configuração e sessão, que são expostas pela API;
--   * BIGINT para movimentações e auditoria, que são históricas e de alto volume;
--   * TIMESTAMP e NUMERIC(12,2), seguindo o restante do schema;
--   * status textuais com CHECK, sem ENUM nativo do PostgreSQL.

DO $$
BEGIN
    IF to_regclass('public.estabelecimento') IS NULL
        OR to_regclass('public.usuario') IS NULL
        OR to_regclass('public.pedido') IS NULL
        OR to_regclass('public.pagamento_pedido') IS NULL THEN
        RAISE EXCEPTION
            'A estrutura base não foi encontrada. Em um banco vazio, execute db/script.sql antes de iniciar a aplicação.';
    END IF;
END $$;

-- ============================================================
-- CONFIGURAÇÃO DO CAIXA
-- ============================================================

CREATE TABLE IF NOT EXISTS configuracao_caixa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estabelecimento_id UUID NOT NULL,
    terminal_id VARCHAR(100),
    terminal_nome VARCHAR(100),
    point_habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    dinheiro_habilitado BOOLEAN NOT NULL DEFAULT TRUE,
    debito_habilitado BOOLEAN NOT NULL DEFAULT TRUE,
    credito_habilitado BOOLEAN NOT NULL DEFAULT TRUE,
    pix_habilitado BOOLEAN NOT NULL DEFAULT TRUE,
    parcelamento_habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    parcelas_minimas INT NOT NULL DEFAULT 1,
    parcelas_maximas INT NOT NULL DEFAULT 5,
    valor_minimo_parcela NUMERIC(12,2),
    politica_juros VARCHAR(30) NOT NULL DEFAULT 'ABSORVIDA_PELA_LOJA',
    validade_cobranca_segundos INT NOT NULL DEFAULT 300,
    limite_desconto_percentual NUMERIC(5,2) NOT NULL DEFAULT 0,
    limite_desconto_valor NUMERIC(12,2) NOT NULL DEFAULT 0,
    imprimir_comprovante BOOLEAN NOT NULL DEFAULT TRUE,
    permitir_pagamento_manual BOOLEAN NOT NULL DEFAULT FALSE,
    atualizado_por_usuario_id UUID,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_configuracao_caixa_estabelecimento
        UNIQUE (estabelecimento_id),

    CONSTRAINT ck_configuracao_caixa_formas
        CHECK (dinheiro_habilitado OR debito_habilitado OR credito_habilitado OR pix_habilitado),

    CONSTRAINT ck_configuracao_caixa_point
        CHECK (NOT point_habilitado OR terminal_id IS NOT NULL),

    CONSTRAINT ck_configuracao_caixa_parcelamento
        CHECK (NOT parcelamento_habilitado OR credito_habilitado),

    CONSTRAINT ck_configuracao_caixa_parcelas
        CHECK (parcelas_minimas >= 1 AND parcelas_maximas >= parcelas_minimas),

    CONSTRAINT ck_configuracao_caixa_valor_minimo_parcela
        CHECK (valor_minimo_parcela IS NULL OR valor_minimo_parcela > 0),

    CONSTRAINT ck_configuracao_caixa_politica_juros
        CHECK (politica_juros IN ('ABSORVIDA_PELA_LOJA', 'REPASSADA_AO_CLIENTE')),

    CONSTRAINT ck_configuracao_caixa_validade_cobranca
        CHECK (validade_cobranca_segundos BETWEEN 60 AND 900),

    CONSTRAINT ck_configuracao_caixa_desconto_percentual
        CHECK (limite_desconto_percentual >= 0 AND limite_desconto_percentual <= 100),

    CONSTRAINT ck_configuracao_caixa_desconto_valor
        CHECK (limite_desconto_valor >= 0),

    CONSTRAINT fk_configuracao_caixa_estabelecimento
        FOREIGN KEY (estabelecimento_id)
        REFERENCES estabelecimento(id),

    CONSTRAINT fk_configuracao_caixa_atualizado_por
        FOREIGN KEY (atualizado_por_usuario_id)
        REFERENCES usuario(id)
        ON DELETE SET NULL
);

-- Toda instalação existente passa a ter uma configuração inicial.
-- Os valores padrão preservam o comportamento descrito no documento de regras.
INSERT INTO configuracao_caixa (estabelecimento_id)
SELECT id
FROM estabelecimento
ON CONFLICT (estabelecimento_id) DO NOTHING;

-- ============================================================
-- SESSÕES DE CAIXA
-- ============================================================

CREATE TABLE IF NOT EXISTS sessao_caixa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estabelecimento_id UUID NOT NULL,
    terminal_id VARCHAR(100),
    operador_usuario_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ABERTA',
    fundo_troco NUMERIC(12,2) NOT NULL DEFAULT 0,
    dinheiro_esperado NUMERIC(12,2),
    dinheiro_contado NUMERIC(12,2),
    diferenca NUMERIC(12,2),
    resumo_fechamento JSONB,
    observacao_abertura TEXT,
    observacao_fechamento TEXT,
    justificativa_divergencia TEXT,
    aberta_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fechada_em TIMESTAMP,
    fechada_por_usuario_id UUID,
    divergencia_resolvida_por_usuario_id UUID,
    divergencia_resolvida_em TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Permite que pedido e pagamento validem a sessão e o estabelecimento
    -- na mesma chave estrangeira composta.
    CONSTRAINT uq_sessao_caixa_estabelecimento
        UNIQUE (id, estabelecimento_id),

    CONSTRAINT ck_sessao_caixa_status
        CHECK (status IN ('ABERTA', 'EM_FECHAMENTO', 'FECHADA', 'CANCELADA')),

    CONSTRAINT ck_sessao_caixa_fundo_troco
        CHECK (fundo_troco >= 0),

    CONSTRAINT ck_sessao_caixa_dinheiro_contado
        CHECK (dinheiro_contado IS NULL OR dinheiro_contado >= 0),

    CONSTRAINT ck_sessao_caixa_fechamento
        CHECK (
            status <> 'FECHADA'
            OR (
                fechada_em IS NOT NULL
                AND fechada_por_usuario_id IS NOT NULL
                AND dinheiro_esperado IS NOT NULL
                AND dinheiro_contado IS NOT NULL
                AND diferenca IS NOT NULL
            )
        ),

    CONSTRAINT ck_sessao_caixa_em_fechamento
        CHECK (status <> 'EM_FECHAMENTO' OR dinheiro_contado IS NOT NULL),

    CONSTRAINT ck_sessao_caixa_diferenca
        CHECK (
            diferenca IS NULL
            OR (
                dinheiro_esperado IS NOT NULL
                AND dinheiro_contado IS NOT NULL
                AND diferenca = dinheiro_contado - dinheiro_esperado
            )
        ),

    CONSTRAINT ck_sessao_caixa_justificativa
        CHECK (
            diferenca IS NULL
            OR diferenca = 0
            OR justificativa_divergencia IS NOT NULL
        ),

    CONSTRAINT fk_sessao_caixa_estabelecimento
        FOREIGN KEY (estabelecimento_id)
        REFERENCES estabelecimento(id),

    CONSTRAINT fk_sessao_caixa_operador
        FOREIGN KEY (operador_usuario_id)
        REFERENCES usuario(id),

    CONSTRAINT fk_sessao_caixa_fechada_por
        FOREIGN KEY (fechada_por_usuario_id)
        REFERENCES usuario(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_sessao_caixa_divergencia_resolvida_por
        FOREIGN KEY (divergencia_resolvida_por_usuario_id)
        REFERENCES usuario(id)
        ON DELETE SET NULL
);

-- Uma sessão aberta (ou em fechamento) por terminal/gaveta.
-- COALESCE permite aplicar a regra também quando o terminal ainda não foi
-- configurado, caso em que a sessão é identificada como 'SEM_TERMINAL'.
CREATE UNIQUE INDEX IF NOT EXISTS uq_sessao_caixa_aberta
    ON sessao_caixa (estabelecimento_id, COALESCE(terminal_id, 'SEM_TERMINAL'))
    WHERE status IN ('ABERTA', 'EM_FECHAMENTO');

-- ============================================================
-- MOVIMENTAÇÕES DE CAIXA (SANGRIA E REFORÇO)
-- ============================================================

CREATE TABLE IF NOT EXISTS movimentacao_caixa (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    sessao_caixa_id UUID NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    valor NUMERIC(12,2) NOT NULL,
    motivo TEXT NOT NULL,
    usuario_id UUID NOT NULL,
    ocorrido_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_movimentacao_caixa_tipo
        CHECK (tipo IN ('SANGRIA', 'REFORCO')),

    CONSTRAINT ck_movimentacao_caixa_valor
        CHECK (valor > 0),

    CONSTRAINT ck_movimentacao_caixa_motivo
        CHECK (BTRIM(motivo) <> ''),

    CONSTRAINT fk_movimentacao_caixa_sessao
        FOREIGN KEY (sessao_caixa_id)
        REFERENCES sessao_caixa(id),

    CONSTRAINT fk_movimentacao_caixa_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id)
);

-- ============================================================
-- AUDITORIA DO CAIXA
-- ============================================================

CREATE TABLE IF NOT EXISTS auditoria_caixa (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    estabelecimento_id UUID NOT NULL,
    sessao_caixa_id UUID,
    usuario_id UUID,
    origem VARCHAR(20) NOT NULL DEFAULT 'API',
    tipo_evento VARCHAR(60) NOT NULL,
    entidade VARCHAR(40),
    entidade_id VARCHAR(64),
    pedido_id UUID,
    pagamento_pedido_id UUID,
    descricao TEXT,
    dados_anteriores JSONB,
    dados_novos JSONB,
    ocorrido_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_auditoria_caixa_origem
        CHECK (origem IN ('API', 'WEBHOOK', 'SISTEMA')),

    -- Ações humanas exigem usuário; ações automáticas identificam a origem.
    CONSTRAINT ck_auditoria_caixa_ator
        CHECK (usuario_id IS NOT NULL OR origem IN ('WEBHOOK', 'SISTEMA')),

    CONSTRAINT fk_auditoria_caixa_estabelecimento
        FOREIGN KEY (estabelecimento_id)
        REFERENCES estabelecimento(id),

    CONSTRAINT fk_auditoria_caixa_sessao
        FOREIGN KEY (sessao_caixa_id)
        REFERENCES sessao_caixa(id)
        ON DELETE SET NULL,

    -- Sem ON DELETE SET NULL: o ator humano deve permanecer identificado.
    -- A exclusão de usuário com auditoria é bloqueada pela FK.
    CONSTRAINT fk_auditoria_caixa_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id),

    CONSTRAINT fk_auditoria_caixa_pedido
        FOREIGN KEY (pedido_id)
        REFERENCES pedido(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_auditoria_caixa_pagamento
        FOREIGN KEY (pagamento_pedido_id)
        REFERENCES pagamento_pedido(id)
        ON DELETE SET NULL
);

-- ============================================================
-- ÍNDICES
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_sessao_caixa_estabelecimento_status
    ON sessao_caixa(estabelecimento_id, status);

CREATE INDEX IF NOT EXISTS idx_sessao_caixa_operador
    ON sessao_caixa(operador_usuario_id);

CREATE INDEX IF NOT EXISTS idx_sessao_caixa_aberta_em
    ON sessao_caixa(aberta_em DESC);

CREATE INDEX IF NOT EXISTS idx_movimentacao_caixa_sessao
    ON movimentacao_caixa(sessao_caixa_id, ocorrido_em DESC);

CREATE INDEX IF NOT EXISTS idx_movimentacao_caixa_usuario
    ON movimentacao_caixa(usuario_id);

CREATE INDEX IF NOT EXISTS idx_auditoria_caixa_estabelecimento
    ON auditoria_caixa(estabelecimento_id, ocorrido_em DESC);

CREATE INDEX IF NOT EXISTS idx_auditoria_caixa_sessao
    ON auditoria_caixa(sessao_caixa_id, ocorrido_em DESC);

CREATE INDEX IF NOT EXISTS idx_auditoria_caixa_pedido
    ON auditoria_caixa(pedido_id);

CREATE INDEX IF NOT EXISTS idx_auditoria_caixa_pagamento
    ON auditoria_caixa(pagamento_pedido_id);

CREATE INDEX IF NOT EXISTS idx_auditoria_caixa_entidade
    ON auditoria_caixa(entidade, entidade_id);

-- ============================================================
-- DOCUMENTAÇÃO DOS IDS
-- ============================================================

COMMENT ON COLUMN configuracao_caixa.id IS
    'UUID - configuração exposta pela API e isolada por estabelecimento';

COMMENT ON COLUMN sessao_caixa.id IS
    'UUID - sessão operacional exposta em APIs e relatórios';

COMMENT ON COLUMN movimentacao_caixa.id IS
    'BIGINT - histórico de alto volume e escopo interno à sessão';

COMMENT ON COLUMN auditoria_caixa.id IS
    'BIGINT - trilha de auditoria de alto volume';

COMMENT ON COLUMN configuracao_caixa.terminal_id IS
    'Identificador do terminal Mercado Pago Point (config.point.terminal_id)';

COMMENT ON COLUMN sessao_caixa.terminal_id IS
    'Terminal utilizado na sessão; nulo enquanto a Point não estiver configurada';

COMMENT ON COLUMN sessao_caixa.resumo_fechamento IS
    'Resumo congelado no fechamento: totais por forma, sangrias, reforços e conferência';

COMMENT ON COLUMN auditoria_caixa.entidade_id IS
    'Identificador textual da entidade auditada, inclusive IDs compostos ou externos';
