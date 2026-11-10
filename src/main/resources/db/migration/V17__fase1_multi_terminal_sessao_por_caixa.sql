DO $$
BEGIN
    IF to_regclass('public.estabelecimento') IS NULL
        OR to_regclass('public.sessao_caixa') IS NULL
        OR to_regclass('public.configuracao_caixa') IS NULL
        OR to_regclass('public.pagamento_pedido') IS NULL THEN
        RAISE EXCEPTION
            'A estrutura do caixa não foi encontrada. Aplique as migrations anteriores antes da V17.';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS caixa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estabelecimento_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_caixa_estabelecimento
        UNIQUE (id, estabelecimento_id),

    CONSTRAINT ck_caixa_nome
        CHECK (BTRIM(nome) <> ''),

    CONSTRAINT fk_caixa_estabelecimento
        FOREIGN KEY (estabelecimento_id)
        REFERENCES estabelecimento(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_caixa_nome_estabelecimento_lower
    ON caixa (estabelecimento_id, LOWER(BTRIM(nome)));

CREATE INDEX IF NOT EXISTS idx_caixa_estabelecimento_ativo
    ON caixa (estabelecimento_id, ativo);

CREATE TABLE IF NOT EXISTS terminal_caixa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    caixa_id UUID NOT NULL,
    terminal_id VARCHAR(100) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_terminal_caixa_terminal
        UNIQUE (terminal_id),

    CONSTRAINT ck_terminal_caixa_terminal_id
        CHECK (BTRIM(terminal_id) <> ''),

    CONSTRAINT ck_terminal_caixa_nome
        CHECK (BTRIM(nome) <> ''),

    CONSTRAINT fk_terminal_caixa_caixa
        FOREIGN KEY (caixa_id)
        REFERENCES caixa(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_terminal_caixa_nome_caixa_lower
    ON terminal_caixa (caixa_id, LOWER(BTRIM(nome)));

CREATE INDEX IF NOT EXISTS idx_terminal_caixa_caixa
    ON terminal_caixa (caixa_id);

COMMENT ON COLUMN terminal_caixa.terminal_id IS
    'Identificador do dispositivo no Mercado Pago (terminal_id da Orders API); único globalmente porque identifica o aparelho físico';

CREATE TABLE IF NOT EXISTS taxa_pagamento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estabelecimento_id UUID NOT NULL,
    forma VARCHAR(30) NOT NULL,
    parcelas INT NOT NULL DEFAULT 1,
    taxa_percentual NUMERIC(7,4) NOT NULL DEFAULT 0,
    taxa_fixa NUMERIC(12,2) NOT NULL DEFAULT 0,
    repassar_ao_cliente BOOLEAN,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_taxa_pagamento_forma_parcelas
        UNIQUE (estabelecimento_id, forma, parcelas),

    CONSTRAINT ck_taxa_pagamento_forma
        CHECK (forma IN ('DEBITO', 'CREDITO', 'PIX')),

    CONSTRAINT ck_taxa_pagamento_parcelas
        CHECK (parcelas BETWEEN 1 AND 12),

    CONSTRAINT ck_taxa_pagamento_forma_parcelas
        CHECK (forma = 'CREDITO' OR parcelas = 1),

    CONSTRAINT ck_taxa_pagamento_percentual
        CHECK (taxa_percentual >= 0 AND taxa_percentual <= 100),

    CONSTRAINT ck_taxa_pagamento_fixa
        CHECK (taxa_fixa >= 0),

    CONSTRAINT fk_taxa_pagamento_estabelecimento
        FOREIGN KEY (estabelecimento_id)
        REFERENCES estabelecimento(id)
);

COMMENT ON TABLE taxa_pagamento IS
    'Taxa estimada do adquirente por forma e número de parcelas. O valor efetivo cobrado vem do webhook ou do relatório do Mercado Pago.';

COMMENT ON COLUMN taxa_pagamento.taxa_percentual IS
    'Percentual total sobre o valor da venda para a forma e o parcelamento (não é percentual por parcela)';

COMMENT ON COLUMN taxa_pagamento.repassar_ao_cliente IS
    'Nulo segue configuracao_caixa.politica_juros; TRUE ou FALSE sobrescreve a política para esta forma e parcelamento';

INSERT INTO taxa_pagamento (estabelecimento_id, forma, parcelas, taxa_percentual, taxa_fixa, ativo)
SELECT estabelecimento.id, referencia.forma, 1, referencia.taxa_percentual, 0, FALSE
FROM estabelecimento
CROSS JOIN (
    VALUES
        ('DEBITO', 1.9900),
        ('CREDITO', 4.9800),
        ('PIX', 0.4900)
) AS referencia(forma, taxa_percentual)
ON CONFLICT (estabelecimento_id, forma, parcelas) DO NOTHING;

ALTER TABLE sessao_caixa
    ADD COLUMN IF NOT EXISTS caixa_id UUID;

INSERT INTO caixa (estabelecimento_id, nome)
SELECT estabelecimento.id, 'Caixa Principal'
FROM estabelecimento
WHERE NOT EXISTS (
    SELECT 1 FROM caixa WHERE caixa.estabelecimento_id = estabelecimento.id
);

INSERT INTO terminal_caixa (caixa_id, terminal_id, nome)
SELECT caixa.id,
       BTRIM(configuracao.terminal_id),
       COALESCE(NULLIF(BTRIM(configuracao.terminal_nome), ''), BTRIM(configuracao.terminal_id))
FROM configuracao_caixa configuracao
JOIN caixa
    ON caixa.estabelecimento_id = configuracao.estabelecimento_id
   AND caixa.nome = 'Caixa Principal'
WHERE configuracao.terminal_id IS NOT NULL
  AND BTRIM(configuracao.terminal_id) <> ''
ON CONFLICT (terminal_id) DO NOTHING;

DO $$
DECLARE
    legado RECORD;
    caixa_novo_id UUID;
BEGIN
    FOR legado IN
        SELECT DISTINCT sessao.estabelecimento_id, BTRIM(sessao.terminal_id) AS terminal_id
        FROM sessao_caixa sessao
        WHERE sessao.terminal_id IS NOT NULL
          AND BTRIM(sessao.terminal_id) <> ''
          AND NOT EXISTS (
              SELECT 1
              FROM terminal_caixa terminal
              JOIN caixa ON caixa.id = terminal.caixa_id
              WHERE caixa.estabelecimento_id = sessao.estabelecimento_id
                AND terminal.terminal_id = BTRIM(sessao.terminal_id)
          )
    LOOP
        INSERT INTO caixa (estabelecimento_id, nome)
        VALUES (legado.estabelecimento_id, LEFT('Caixa ' || legado.terminal_id, 100))
        RETURNING id INTO caixa_novo_id;

        INSERT INTO terminal_caixa (caixa_id, terminal_id, nome)
        VALUES (caixa_novo_id, legado.terminal_id, LEFT(legado.terminal_id, 100))
        ON CONFLICT (terminal_id) DO NOTHING;
    END LOOP;
END $$;

UPDATE sessao_caixa sessao
SET caixa_id = COALESCE(
    (
        SELECT terminal.caixa_id
        FROM terminal_caixa terminal
        JOIN caixa ON caixa.id = terminal.caixa_id
        WHERE caixa.estabelecimento_id = sessao.estabelecimento_id
          AND terminal.terminal_id = BTRIM(sessao.terminal_id)
        LIMIT 1
    ),
    (
        SELECT caixa.id
        FROM caixa
        WHERE caixa.estabelecimento_id = sessao.estabelecimento_id
          AND caixa.nome = 'Caixa Principal'
        LIMIT 1
    )
)
WHERE sessao.caixa_id IS NULL;

ALTER TABLE pagamento_pedido
    ADD COLUMN IF NOT EXISTS terminal_caixa_id UUID,
    ADD COLUMN IF NOT EXISTS taxa_percentual_aplicada NUMERIC(7,4),
    ADD COLUMN IF NOT EXISTS taxa_fixa_aplicada NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS valor_taxa_estimada NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS valor_liquido_estimado NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS valor_juros NUMERIC(12,2);

UPDATE pagamento_pedido pagamento
SET terminal_caixa_id = terminal.id
FROM terminal_caixa terminal
WHERE pagamento.terminal_caixa_id IS NULL
  AND pagamento.terminal_id IS NOT NULL
  AND terminal.terminal_id = BTRIM(pagamento.terminal_id);


DO $$
DECLARE
    caixas_duplicados INTEGER;
BEGIN
    SELECT COUNT(*) INTO caixas_duplicados
    FROM (
        SELECT caixa_id
        FROM sessao_caixa
        WHERE status = 'ABERTA'
        GROUP BY caixa_id
        HAVING COUNT(*) > 1
    ) duplicados;

    IF caixas_duplicados > 0 THEN
        RAISE EXCEPTION
            'Existem % caixa(s) com mais de uma sessão ABERTA após o backfill. Feche as sessões excedentes antes de aplicar a V17.',
            caixas_duplicados;
    END IF;
END $$;

ALTER TABLE sessao_caixa
    ALTER COLUMN caixa_id SET NOT NULL;

ALTER TABLE sessao_caixa
    ADD CONSTRAINT fk_sessao_caixa_caixa
    FOREIGN KEY (caixa_id, estabelecimento_id)
    REFERENCES caixa(id, estabelecimento_id);

DROP INDEX IF EXISTS uq_sessao_caixa_aberta;

CREATE UNIQUE INDEX uq_sessao_caixa_aberta
    ON sessao_caixa (caixa_id)
    WHERE status = 'ABERTA';

COMMENT ON INDEX uq_sessao_caixa_aberta IS
    'Garante uma única sessão ABERTA por caixa. Sessões EM_FECHAMENTO aguardam aprovação sem bloquear o caixa. A unicidade por operador continua em uq_sessao_caixa_aberta_operador.';

CREATE INDEX IF NOT EXISTS idx_sessao_caixa_caixa
    ON sessao_caixa (caixa_id);

ALTER TABLE sessao_caixa
    DROP COLUMN terminal_id;

COMMENT ON COLUMN sessao_caixa.caixa_id IS
    'Caixa (posto físico) da sessão; a maquininha usada em cada cobrança fica em pagamento_pedido.terminal_caixa_id';


ALTER TABLE configuracao_caixa
    DROP CONSTRAINT IF EXISTS ck_configuracao_caixa_point;

ALTER TABLE configuracao_caixa
    DROP COLUMN terminal_id,
    DROP COLUMN terminal_nome;

ALTER TABLE pagamento_pedido
    ADD CONSTRAINT fk_pagamento_terminal_caixa
    FOREIGN KEY (terminal_caixa_id)
    REFERENCES terminal_caixa(id);

ALTER TABLE pagamento_pedido
    ADD CONSTRAINT ck_pagamento_taxa_estimada
    CHECK (
        (taxa_percentual_aplicada IS NULL
            OR (taxa_percentual_aplicada >= 0 AND taxa_percentual_aplicada <= 100))
        AND (taxa_fixa_aplicada IS NULL OR taxa_fixa_aplicada >= 0)
        AND (valor_taxa_estimada IS NULL OR valor_taxa_estimada >= 0)
        AND (valor_juros IS NULL OR valor_juros >= 0)
    );

CREATE INDEX IF NOT EXISTS idx_pagamento_terminal_caixa
    ON pagamento_pedido (terminal_caixa_id)
    WHERE terminal_caixa_id IS NOT NULL;

COMMENT ON COLUMN pagamento_pedido.terminal_caixa_id IS
    'Terminal cadastrado que processou a cobrança; terminal_id mantém o snapshot do identificador no Mercado Pago';

COMMENT ON COLUMN pagamento_pedido.valor_taxa_estimada IS
    'Taxa estimada na criação da cobrança; o valor efetivo vem do webhook ou do relatório do Mercado Pago';

COMMENT ON COLUMN pagamento_pedido.valor_juros IS
    'Parte do valor_cobrado que corresponde aos juros repassados ao cliente';
