-- Permite a troca de turno quando o fechamento anterior ficou pendente de
-- aprovação gerencial.
--
-- A V9 bloqueava uma nova abertura no mesmo terminal enquanto existisse uma
-- sessão ABERTA ou EM_FECHAMENTO. Isso impedia outro operador de abrir o caixa
-- durante a troca de turno caso a sessão anterior tivesse divergência e ainda
-- não tivesse sido aprovada.
--
-- A regra passa a ser: somente uma sessão ABERTA por terminal. A sessão
-- EM_FECHAMENTO permanece visível e pendente de resolução, mas não bloqueia o
-- terminal. O dinheiro da sessão anterior deve ser conferido e retirado antes
-- da abertura da nova sessão; cada sessão tem seu próprio fundo de troco.

DROP INDEX IF EXISTS uq_sessao_caixa_aberta;

CREATE UNIQUE INDEX uq_sessao_caixa_aberta
    ON sessao_caixa (estabelecimento_id, COALESCE(terminal_id, 'SEM_TERMINAL'))
    WHERE status = 'ABERTA';

COMMENT ON INDEX uq_sessao_caixa_aberta IS
    'Garante uma única sessão ABERTA por estabelecimento e terminal. Sessões EM_FECHAMENTO aguardam aprovação sem bloquear o terminal.';
