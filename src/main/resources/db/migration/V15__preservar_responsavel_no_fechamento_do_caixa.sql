-- Corrige um conflito entre as FKs de fechamento da sessão e o CHECK de
-- consistência da sessão fechada.
--
-- A V9 criou fk_sessao_caixa_fechada_por com ON DELETE SET NULL, mas
-- ck_sessao_caixa_fechamento exige fechada_por_usuario_id preenchido quando o
-- status é FECHADA. Excluir o usuário que fechou a sessão fazia o UPDATE
-- disparado pela FK violar o CHECK, em vez de concluir a exclusão.
--
-- A mesma lógica vale para quem resolveu a divergência: é responsabilidade
-- histórica e não deve desaparecer. A política passa a ser bloquear a exclusão
-- física de usuários com histórico; o fluxo de usuários deve inativar o
-- cadastro (status INATIVO) em vez de apagar a linha.

ALTER TABLE sessao_caixa
    DROP CONSTRAINT IF EXISTS fk_sessao_caixa_fechada_por;

ALTER TABLE sessao_caixa
    ADD CONSTRAINT fk_sessao_caixa_fechada_por
        FOREIGN KEY (fechada_por_usuario_id)
        REFERENCES usuario(id);

ALTER TABLE sessao_caixa
    DROP CONSTRAINT IF EXISTS fk_sessao_caixa_divergencia_resolvida_por;

ALTER TABLE sessao_caixa
    ADD CONSTRAINT fk_sessao_caixa_divergencia_resolvida_por
        FOREIGN KEY (divergencia_resolvida_por_usuario_id)
        REFERENCES usuario(id);

COMMENT ON COLUMN sessao_caixa.fechada_por_usuario_id IS
    'Usuário que fechou a sessão; a exclusão física é bloqueada para preservar o histórico';

COMMENT ON COLUMN sessao_caixa.divergencia_resolvida_por_usuario_id IS
    'Usuário que resolveu a divergência de caixa; a exclusão física é bloqueada para preservar o histórico';
