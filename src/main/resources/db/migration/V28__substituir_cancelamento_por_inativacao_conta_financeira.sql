-- Substitui o status CANCELADA por INATIVA nas contas financeiras (módulo financeiro).
ALTER TABLE conta_financeira
    DROP CONSTRAINT IF EXISTS ck_conta_financeira_status;

UPDATE conta_financeira
SET status = 'INATIVA'
WHERE status = 'CANCELADA';

ALTER TABLE conta_financeira
    ADD CONSTRAINT ck_conta_financeira_status
        CHECK (status IN ('ABERTA', 'PAGA', 'INATIVA'));

UPDATE funcionalidade
SET descricao = 'Cadastrar, editar, baixar e inativar contas a pagar e receber'
WHERE codigo = 'GERENCIAR_FINANCEIRO';
