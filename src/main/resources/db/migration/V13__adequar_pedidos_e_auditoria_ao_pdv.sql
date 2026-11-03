-- Adequações do schema ao PDV e à tela de auditoria do frontend.
--
-- A V9–V12 já foi aplicada em bancos existentes; por isso esta migration é
-- incremental e não altera os arquivos anteriores.
--
-- Lacunas encontradas na comparação com valhala.erp.web:
--   * o frontend oferece o canal Delivery, mas o CHECK de pedido só aceitava
--     BALCAO, MESA e RETIRADA;
--   * os relatórios de margem usam o custo e a categoria do momento da venda,
--     que precisam ser congelados em item_pedido;
--   * a auditoria do caixa exibe valor, motivo, autorizador e o rótulo do
--     pedido, que não existiam como colunas consultáveis.

-- ============================================================
-- CANAL DELIVERY
-- ============================================================

ALTER TABLE pedido DROP CONSTRAINT IF EXISTS ck_pedido_canal;

ALTER TABLE pedido ADD CONSTRAINT ck_pedido_canal
    CHECK (canal IN ('BALCAO', 'MESA', 'RETIRADA', 'DELIVERY')) NOT VALID;

-- Os dados antigos só podiam conter os três valores anteriores; a validação
-- também protege a troca do CHECK em instalações que já tenham pedidos.
ALTER TABLE pedido VALIDATE CONSTRAINT ck_pedido_canal;

COMMENT ON COLUMN pedido.canal IS
    'Canal do atendimento: BALCAO, MESA, RETIRADA ou DELIVERY';

-- ============================================================
-- ITEM DO PEDIDO: CUSTO, CATEGORIA E ENVIO AO BAR
-- ============================================================

ALTER TABLE item_pedido
    ADD COLUMN IF NOT EXISTS custo_unitario NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS categoria_id INT,
    ADD COLUMN IF NOT EXISTS categoria_nome VARCHAR(100),
    ADD COLUMN IF NOT EXISTS quantidade_enviada_bar NUMERIC(12,3) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS enviado_bar_em TIMESTAMP;

-- Instalações antigas recebem o custo e a categoria atuais como aproximação.
-- O valor correto passa a ser gravado pelo serviço de pedidos em cada venda.
UPDATE item_pedido i
SET custo_unitario = p.preco_custo
FROM produto p
WHERE i.produto_id = p.id
  AND i.custo_unitario IS NULL;

UPDATE item_pedido i
SET categoria_id = p.categoria_id,
    categoria_nome = c.nome
FROM produto p
JOIN categoria c ON c.id = p.categoria_id
WHERE i.produto_id = p.id
  AND i.categoria_id IS NULL
  AND i.categoria_nome IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'item_pedido'::regclass
          AND conname = 'chk_item_pedido_custo'
    ) THEN
        ALTER TABLE item_pedido ADD CONSTRAINT chk_item_pedido_custo
            CHECK (custo_unitario IS NULL OR custo_unitario >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'item_pedido'::regclass
          AND conname = 'chk_item_pedido_enviado_bar'
    ) THEN
        ALTER TABLE item_pedido ADD CONSTRAINT chk_item_pedido_enviado_bar
            CHECK (quantidade_enviada_bar >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'item_pedido'::regclass
          AND conname = 'fk_item_pedido_categoria'
    ) THEN
        ALTER TABLE item_pedido ADD CONSTRAINT fk_item_pedido_categoria
            FOREIGN KEY (categoria_id)
            REFERENCES categoria(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_item_pedido_produto
    ON item_pedido(produto_id);

CREATE INDEX IF NOT EXISTS idx_item_pedido_categoria
    ON item_pedido(categoria_id);

COMMENT ON COLUMN item_pedido.custo_unitario IS
    'Custo unitário congelado no momento da venda; nulo apenas em registros legados';

COMMENT ON COLUMN item_pedido.categoria_id IS
    'Categoria do produto no momento da venda, preservada para relatórios históricos';

COMMENT ON COLUMN item_pedido.categoria_nome IS
    'Snapshot do nome da categoria; mantém o relatório estável se a categoria for renomeada';

COMMENT ON COLUMN item_pedido.quantidade_enviada_bar IS
    'Quantidade do item já enviada à comanda do bar; não altera a quantidade vendida';

COMMENT ON COLUMN item_pedido.enviado_bar_em IS
    'Último envio deste item à comanda do bar';

-- ============================================================
-- AUDITORIA DO CAIXA: CAMPOS EXIGIDOS PELA TELA
-- ============================================================

ALTER TABLE auditoria_caixa
    ADD COLUMN IF NOT EXISTS usuario_nome VARCHAR(150),
    ADD COLUMN IF NOT EXISTS valor NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS motivo TEXT,
    ADD COLUMN IF NOT EXISTS autorizado_por_usuario_id UUID,
    ADD COLUMN IF NOT EXISTS autorizado_por_nome VARCHAR(150),
    ADD COLUMN IF NOT EXISTS pedido_identificador VARCHAR(60);

-- Preserva o nome do ator e o rótulo do pedido para que a trilha continue
-- legível mesmo se o usuário for renomeado ou o pedido for removido.
UPDATE auditoria_caixa a
SET usuario_nome = u.nome
FROM usuario u
WHERE a.usuario_id = u.id
  AND a.usuario_nome IS NULL;

UPDATE auditoria_caixa a
SET pedido_identificador = p.identificador_atendimento
FROM pedido p
WHERE a.pedido_id = p.id
  AND a.pedido_identificador IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'auditoria_caixa'::regclass
          AND conname = 'ck_auditoria_caixa_valor'
    ) THEN
        ALTER TABLE auditoria_caixa ADD CONSTRAINT ck_auditoria_caixa_valor
            CHECK (valor IS NULL OR valor >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'auditoria_caixa'::regclass
          AND conname = 'fk_auditoria_caixa_autorizador'
    ) THEN
        ALTER TABLE auditoria_caixa ADD CONSTRAINT fk_auditoria_caixa_autorizador
            FOREIGN KEY (autorizado_por_usuario_id)
            REFERENCES usuario(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_auditoria_caixa_tipo
    ON auditoria_caixa(estabelecimento_id, tipo_evento, ocorrido_em DESC);

CREATE INDEX IF NOT EXISTS idx_auditoria_caixa_operador
    ON auditoria_caixa(estabelecimento_id, usuario_id, ocorrido_em DESC);

CREATE INDEX IF NOT EXISTS idx_auditoria_caixa_autorizador
    ON auditoria_caixa(autorizado_por_usuario_id);

COMMENT ON COLUMN auditoria_caixa.usuario_nome IS
    'Snapshot do nome do operador no momento do evento';

COMMENT ON COLUMN auditoria_caixa.valor IS
    'Valor financeiro do evento (desconto, cancelamento ou item removido); nulo para eventos não financeiros';

COMMENT ON COLUMN auditoria_caixa.motivo IS
    'Motivo informado pelo operador ou pelo autorizador';

COMMENT ON COLUMN auditoria_caixa.autorizado_por_usuario_id IS
    'Gerente que autorizou a ação; nulo quando não houve autorização';

COMMENT ON COLUMN auditoria_caixa.autorizado_por_nome IS
    'Snapshot do nome do autorizador';

COMMENT ON COLUMN auditoria_caixa.pedido_identificador IS
    'Snapshot do rótulo do pedido, como "Mesa 3" ou "Balcão 2"';

COMMENT ON COLUMN auditoria_caixa.descricao IS
    'Detalhe textual do evento; na tela de auditoria corresponde ao campo detalhe';

COMMENT ON COLUMN auditoria_caixa.tipo_evento IS
    'Código do evento: DESCONTO_CONCEDIDO, DESCONTO_ACIMA_DO_LIMITE, PEDIDO_CANCELADO, ITEM_REMOVIDO, SANGRIA, REFORCO, ABERTURA_CAIXA, FECHAMENTO_CAIXA, DIVERGENCIA_CAIXA, PAGAMENTO_CANCELADO, PAGAMENTO_ESTORNADO, CONFIGURACAO_CAIXA_ALTERADA';

-- ============================================================
-- ÍNDICES DE APOIO AO PDV
-- ============================================================

-- Vendas concluídas do dia e relatórios por período.
CREATE INDEX IF NOT EXISTS idx_pedido_concluido_em
    ON pedido(estabelecimento_id, concluido_em DESC)
    WHERE status = 'CONCLUIDO';

-- Relatório de canais por período.
CREATE INDEX IF NOT EXISTS idx_pedido_canal_concluido
    ON pedido(estabelecimento_id, canal, concluido_em DESC)
    WHERE status = 'CONCLUIDO';

-- Vendas da sessão: o recebimento é localizado pela sessão do pagamento.
CREATE INDEX IF NOT EXISTS idx_pagamento_sessao_status
    ON pagamento_pedido(sessao_caixa_id, status);
