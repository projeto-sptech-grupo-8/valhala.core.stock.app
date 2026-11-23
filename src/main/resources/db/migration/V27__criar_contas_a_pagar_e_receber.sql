-- Contas a pagar e a receber do estabelecimento (módulo financeiro).
--
-- categoria (só contas a pagar) decide onde a conta entra no resultado (DRE):
--   MERCADORIA       → compra de estoque: entra no fluxo de caixa, mas não no DRE (o custo já aparece no CMV);
--   DESPESA_FIXA     → despesas fixas do DRE (aluguel, salários, contador...);
--   DESPESA_VARIAVEL → despesas variáveis do DRE (taxas de cartão, embalagens, entregas...).
-- Contas a receber entram só no fluxo de caixa: a receita do DRE vem das vendas.
CREATE TABLE IF NOT EXISTS conta_financeira (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estabelecimento_id UUID NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    descricao VARCHAR(150) NOT NULL,
    valor NUMERIC(12,2) NOT NULL,
    vencimento DATE NOT NULL,
    centro_custo VARCHAR(20) NOT NULL,
    categoria VARCHAR(20),
    status VARCHAR(12) NOT NULL DEFAULT 'ABERTA',
    pago_em DATE,
    valor_pago NUMERIC(12,2),
    observacao TEXT,
    criado_por_usuario_id UUID NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_conta_financeira_tipo CHECK (tipo IN ('PAGAR', 'RECEBER')),
    CONSTRAINT ck_conta_financeira_valor CHECK (valor > 0),
    CONSTRAINT ck_conta_financeira_centro_custo
        CHECK (centro_custo IN ('COMPRAS', 'OPERACIONAL', 'VENDAS', 'ADMINISTRATIVO')),
    CONSTRAINT ck_conta_financeira_categoria
        CHECK (categoria IN ('MERCADORIA', 'DESPESA_FIXA', 'DESPESA_VARIAVEL')),
    CONSTRAINT ck_conta_financeira_categoria_so_pagar CHECK ((tipo = 'PAGAR') = (categoria IS NOT NULL)),
    CONSTRAINT ck_conta_financeira_status CHECK (status IN ('ABERTA', 'PAGA', 'CANCELADA')),
    CONSTRAINT ck_conta_financeira_baixa
        CHECK ((status = 'PAGA') = (pago_em IS NOT NULL AND valor_pago IS NOT NULL)),
    CONSTRAINT ck_conta_financeira_valor_pago CHECK (valor_pago IS NULL OR valor_pago > 0),

    CONSTRAINT fk_conta_financeira_estabelecimento
        FOREIGN KEY (estabelecimento_id) REFERENCES estabelecimento(id),
    CONSTRAINT fk_conta_financeira_criado_por
        FOREIGN KEY (criado_por_usuario_id) REFERENCES usuario(id)
);

CREATE INDEX IF NOT EXISTS idx_conta_estabelecimento_status_vencimento
    ON conta_financeira (estabelecimento_id, status, vencimento);

CREATE INDEX IF NOT EXISTS idx_conta_estabelecimento_pago_em
    ON conta_financeira (estabelecimento_id, pago_em)
    WHERE pago_em IS NOT NULL;

-- Fluxo de caixa: recebimentos das vendas por data de pagamento.
CREATE INDEX IF NOT EXISTS idx_pagamento_pedido_pago_em
    ON pagamento_pedido (pago_em);

INSERT INTO funcionalidade (codigo, nome, descricao)
VALUES
    ('VISUALIZAR_FINANCEIRO', 'Visualizar financeiro', 'Consultar saldo, fluxo de caixa, resultado e contas'),
    ('GERENCIAR_FINANCEIRO', 'Gerenciar financeiro', 'Cadastrar, editar, baixar e cancelar contas a pagar e receber')
ON CONFLICT (codigo) DO UPDATE
SET nome = EXCLUDED.nome,
    descricao = EXCLUDED.descricao;

INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT perfil.id, funcionalidade.id
FROM perfil
JOIN funcionalidade ON funcionalidade.codigo IN ('VISUALIZAR_FINANCEIRO', 'GERENCIAR_FINANCEIRO')
WHERE UPPER(BTRIM(perfil.nome)) IN ('GERENTE', 'FINANCEIRO')
ON CONFLICT DO NOTHING;
