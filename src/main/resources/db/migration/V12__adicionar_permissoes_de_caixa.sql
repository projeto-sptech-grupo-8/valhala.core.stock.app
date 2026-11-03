-- Permissões do módulo de caixa.
-- A migration é idempotente para instalações que já executaram o seed inicial.

INSERT INTO funcionalidade (codigo, nome, descricao)
VALUES
    ('CAIXA_OPERAR', 'Operar caixa', 'Criar e editar pedidos, registrar dinheiro e iniciar cobranças na Point'),
    ('CAIXA_FECHAR', 'Fechar caixa', 'Fechar a própria sessão de caixa após a conferência'),
    ('CAIXA_VISUALIZAR', 'Visualizar caixa', 'Consultar sessões, vendas, pagamentos e configuração do caixa'),
    ('CAIXA_CONFIGURAR', 'Configurar caixa', 'Configurar terminal, formas de pagamento, parcelamento e políticas comerciais'),
    ('CAIXA_CANCELAR', 'Cancelar no caixa', 'Cancelar pedidos e cobranças ainda não aprovadas'),
    ('CAIXA_ESTORNAR', 'Estornar pagamento', 'Solicitar e registrar a devolução de pagamento aprovado'),
    ('CAIXA_AUTORIZAR_DESCONTO', 'Autorizar desconto', 'Autorizar desconto acima do limite configurado'),
    ('CAIXA_RESOLVER_DIVERGENCIA', 'Resolver divergência', 'Resolver divergência de caixa e aprovar o fechamento')
ON CONFLICT (codigo) DO UPDATE
SET nome = EXCLUDED.nome,
    descricao = EXCLUDED.descricao;

-- O gerente recebe todas as permissões do caixa no seu estabelecimento.
INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT perfil.id, funcionalidade.id
FROM perfil
CROSS JOIN funcionalidade
WHERE UPPER(BTRIM(perfil.nome)) = 'GERENTE'
  AND funcionalidade.codigo IN (
      'CAIXA_OPERAR',
      'CAIXA_FECHAR',
      'CAIXA_VISUALIZAR',
      'CAIXA_CONFIGURAR',
      'CAIXA_CANCELAR',
      'CAIXA_ESTORNAR',
      'CAIXA_AUTORIZAR_DESCONTO',
      'CAIXA_RESOLVER_DIVERGENCIA'
  )
ON CONFLICT DO NOTHING;

-- O atendente opera o próprio caixa, mas não configura, estorna nem resolve
-- divergência. Ajustes finos continuam possíveis pela administração de perfis.
INSERT INTO perfil_funcionalidade (perfil_id, funcionalidade_id)
SELECT perfil.id, funcionalidade.id
FROM perfil
CROSS JOIN funcionalidade
WHERE UPPER(BTRIM(perfil.nome)) = 'ATENDENTE'
  AND funcionalidade.codigo IN (
      'CAIXA_OPERAR',
      'CAIXA_FECHAR',
      'CAIXA_VISUALIZAR'
  )
ON CONFLICT DO NOTHING;
