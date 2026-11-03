-- Reforça duas invariantes que estavam apenas no serviço:
--   * uma única sessão ABERTA por operador no estabelecimento;
--   * teto absoluto de 12 parcelas na configuração do caixa.
--
-- A V15 garante a unicidade por terminal. A regra de uma sessão por operador
-- já era verificada no SessaoCaixaService, mas duas aberturas concorrentes em
-- terminais diferentes podiam passar pela checagem. O índice parcial abaixo
-- fecha essa janela no banco.
--
-- O teto de parcelas também passa a ser validado no banco, além do DTO. O
-- limite de 12 é o teto operacional adotado para a conta Mercado Pago; o
-- padrão continua sendo 5.

-- ============================================================
-- UMA SESSÃO ABERTA POR OPERADOR
-- ============================================================

-- A migration não encerra sessões automaticamente. Se houver duplicidade, o
-- operador deve encerrar a sessão excedente antes da aplicação.
DO $$
DECLARE
    operadores_duplicados INTEGER;
BEGIN
    SELECT COUNT(*) INTO operadores_duplicados
    FROM (
        SELECT estabelecimento_id, operador_usuario_id
        FROM sessao_caixa
        WHERE status = 'ABERTA'
        GROUP BY estabelecimento_id, operador_usuario_id
        HAVING COUNT(*) > 1
    ) duplicados;

    IF operadores_duplicados > 0 THEN
        RAISE EXCEPTION
            'Existem % operador(es) com mais de uma sessão ABERTA. Encerre as sessões excedentes antes de aplicar a V16.',
            operadores_duplicados;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_sessao_caixa_aberta_operador
    ON sessao_caixa (estabelecimento_id, operador_usuario_id)
    WHERE status = 'ABERTA';

COMMENT ON INDEX uq_sessao_caixa_aberta_operador IS
    'Garante uma única sessão ABERTA por operador no estabelecimento. Complementa uq_sessao_caixa_aberta, que limita uma por terminal.';

-- ============================================================
-- TETO DE PARCELAS
-- ============================================================

-- Em banco novo, o script.sql já cria a constraint; nesse caso a V16 apenas
-- confirma a existência. Em banco evoluído, a constraint é criada NOT VALID
-- e validada logo em seguida.
DO $$
DECLARE
    configuracoes_invalidas INTEGER;
BEGIN
    SELECT COUNT(*) INTO configuracoes_invalidas
    FROM configuracao_caixa
    WHERE parcelas_maximas > 12;

    IF configuracoes_invalidas > 0 THEN
        RAISE EXCEPTION
            'Existem % configuração(ões) com parcelas_maximas maior que 12. Ajuste a configuração para o teto de 12 antes de aplicar a V16.',
            configuracoes_invalidas;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_configuracao_caixa_parcelas_maximas'
          AND conrelid = 'configuracao_caixa'::regclass
    ) THEN
        ALTER TABLE configuracao_caixa
            ADD CONSTRAINT ck_configuracao_caixa_parcelas_maximas
            CHECK (parcelas_maximas <= 12) NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_configuracao_caixa_parcelas_maximas'
          AND conrelid = 'configuracao_caixa'::regclass
          AND NOT convalidated
    ) THEN
        ALTER TABLE configuracao_caixa
            VALIDATE CONSTRAINT ck_configuracao_caixa_parcelas_maximas;
    END IF;
END $$;

-- ============================================================
-- ESCOPO DO ÍNDICE DE COBRANÇA NA POINT
-- ============================================================

-- O índice é global por terminal de propósito: terminal_id identifica o
-- dispositivo físico, não o estabelecimento. Isso impede duas cobranças
-- simultâneas no mesmo terminal mesmo entre tenants.
COMMENT ON INDEX uq_pagamento_point_ativo_terminal IS
    'Uma cobrança eletrônica ativa por terminal. O escopo é global porque terminal_id identifica o dispositivo físico, não o estabelecimento.';
