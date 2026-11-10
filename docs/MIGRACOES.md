# Migrações do banco de dados

O projeto utiliza Flyway para registrar e executar alterações incrementais no PostgreSQL.

## Banco já existente

1. Faça um backup do banco.
2. Atualize o código da aplicação.
3. Configure `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` para o banco correto.
4. Inicie a aplicação uma vez.

O Flyway cria a tabela `flyway_schema_history`, registra a estrutura anterior como versão `0` e executa, uma única vez, as migrações em `src/main/resources/db/migration`.

As migrações atuais:

- criam ou atualizam `usuario_funcionalidade`, preservando permissões já atribuídas como `GRANT`;
- inserem as funcionalidades administrativas e concedem todas elas aos perfis chamados `Gerente`;
- impedem nomes de perfil duplicados no mesmo estabelecimento sem diferenciar maiúsculas de minúsculas.
- inserem as permissões do módulo de estoque e as concedem aos perfis chamados `Gerente`;
- impedem nomes de categoria duplicados no mesmo estabelecimento sem diferenciar maiúsculas de minúsculas.
- adequam Produto, Estoque e Movimentação ao módulo de estoque e criam a composição de drinks.
- removem NCM e CEST, classificações fiscais fora do escopo atual.
- criam `configuracao_caixa`, `sessao_caixa`, `movimentacao_caixa` e `auditoria_caixa` (V9).
- evoluem `pedido` e `pagamento_pedido` para o PDV com sessão, canal, provedor, order/payment externos, idempotência e parcelamento (V10).
- criam `reserva_estoque` e `evento_webhook_pagamento`, permitem baixa de estoque sem usuário humano e reforçam o limite de `quantidade_reservada` (V11).
- inserem as permissões `CAIXA_*` e as concedem aos perfis `Gerente` e `Atendente` (V12).
- adequam pedido e item de pedido ao PDV (canal `DELIVERY`, custo/categoria congelados e envio ao bar) e completam a auditoria com valor, motivo, autorizador e rótulo do pedido (V13).
- impedem que a exclusão de usuário apague o responsável pelo fechamento da sessão ou pela resolução de divergência (V14).
- liberam o terminal para uma nova abertura enquanto a sessão anterior aguarda aprovação de divergência, restringindo o índice único às sessões `ABERTA` (V15).
- reforçam as invariantes do caixa: uma única sessão `ABERTA` por operador e teto de 12 parcelas na configuração (V16).
- implementam a Fase 1 do caixa multi-terminal: criam `caixa`, `terminal_caixa` e `taxa_pagamento`, migram `sessao_caixa` para `caixa_id`, removem `terminal_id` da sessão e da configuração e adicionam terminal e snapshot de taxas em `pagamento_pedido` (V17).
- implementam a Fase 1.5 de pedidos, descontos híbridos, cancelamento e estorno: adicionam os campos de ciclo de vida do desconto (`tipo_desconto`, `valor_desconto_informado`, `status_desconto`, `desconto_solicitado`, `motivo_desconto`, `desconto_decidido_por_usuario_id`, `desconto_decidido_em`, `observacao_decisao_desconto`), cancelamento e estorno (`motivo_cancelamento`, `cancelado_por_usuario_id`, `motivo_estorno`, `estornado_por_usuario_id`, `estornado_em`) e o status `ESTORNADO` em `pedido`, índice único de idempotência `(pedido_id, chave_idempotencia)` em `pagamento_pedido` e concedem `CAIXA_CANCELAR` por padrão aos perfis `Atendente` e `Caixa` (V18).

Antes de aplicar a V3 em um banco existente, confira se não há nomes de perfis que diferem apenas por capitalização. A migration não escolhe nem remove dados automaticamente.

Antes de aplicar a V4 em um banco existente, confira se não há categorias do mesmo estabelecimento que diferem apenas por capitalização. A migration também não escolhe nem remove dados automaticamente.

A V5 transforma custos nulos em `0` e estoques mínimos iguais a `0` em `NULL`. A mudança preserva o comportamento operacional e permite distinguir a ausência de estoque mínimo configurado.

A V6 exige que toda saída causada por um drink preserve a receita aplicada naquele momento. Isso mantém o histórico íntegro caso a receita seja alterada no futuro.

A V7 remove NCM e CEST de Produto e o NCM de Item de Nota Fiscal. Esses dados não participam do módulo de estoque atual.

A V8 permite ingredientes em `ML` ou `UN` na composição de drinks. Produtos fracionados usam
`ML`; ingredientes unitários, como uma lata de energético, usam `UN`.

A V9 cria a estrutura de caixa e uma configuração padrão para cada estabelecimento existente.
A sessão aberta é única por estabelecimento/terminal; o terminal pode ser nulo enquanto a Point
não estiver configurada.

A V10 evolui as tabelas comerciais que já existiam no `script.sql`. O `pedido` passa a registrar
canal, identificador de atendimento e sessão de abertura. O `pagamento_pedido` passa a registrar
a sessão que recebeu o valor, o provedor, os identificadores externos, a chave de idempotência e
os valores de cobrança, recebimento e troco. A migration também cria `sequencia_documento` para
gerar números de pedido/orçamento sem corrida entre atendentes. As constraints de forma/status são
adicionadas como `NOT VALID` para não bloquear dados legados.

A V11 cria a reserva por produto, o log idempotente de webhooks e permite que a movimentação de
estoque seja registrada por `SISTEMA` ou `WEBHOOK` sem usuário humano. O agregado
`estoque.quantidade_reservada` passa a ser limitado por `quantidade_atual`.

A V12 insere as permissões `CAIXA_*`. O perfil `Gerente` recebe todas; o `Atendente` recebe
operação, fechamento e consulta do próprio caixa. Ajustes finos devem ser feitos pela
administração de perfis já existente.

A V13 fecha lacunas encontradas na comparação com o frontend:

- `pedido.canal` passa a aceitar `DELIVERY`, além de `BALCAO`, `MESA` e `RETIRADA`;
- `item_pedido` recebe `custo_unitario`, `categoria_id`, `categoria_nome`,
  `quantidade_enviada_bar` e `enviado_bar_em`, permitindo relatórios históricos de margem e
  controle do envio ao bar sem alterar a quantidade vendida;
- `auditoria_caixa` recebe `usuario_nome`, `valor`, `motivo`, `autorizado_por_usuario_id`,
  `autorizado_por_nome` e `pedido_identificador`, que são os campos exibidos pela tela de
  auditoria;
- são criados índices para vendas concluídas, canais, sessão de recebimento e filtros da
  auditoria.

O `custo_unitario` e a categoria são gravados pelo serviço de pedidos no momento da venda. A
migration preenche registros antigos com o custo e a categoria atuais apenas como aproximação.
O autorizador usa FK com `ON DELETE SET NULL` e mantém o nome em snapshot; o operador do evento
continua sem `SET NULL` para preservar a trilha.

A V14 corrige um conflito da V9: `fk_sessao_caixa_fechada_por` usava `ON DELETE SET NULL`, mas
`ck_sessao_caixa_fechamento` exige `fechada_por_usuario_id` quando a sessão está `FECHADA`.
Excluir esse usuário fazia a FK violar o check. A V14 remove o `SET NULL` dessa FK e também da
FK de resolução de divergência. A política passa a ser: **não apagar fisicamente usuários com
histórico**; o fluxo de usuários deve inativar o cadastro (`status = 'INATIVO'`).

A V15 corrige o bloqueio de troca de turno quando o fechamento anterior fica pendente de
aprovação gerencial. A V9 impedia uma nova sessão no mesmo terminal enquanto existisse sessão
`ABERTA` **ou** `EM_FECHAMENTO`. A partir da V15, o índice `uq_sessao_caixa_aberta` considera
somente `status = 'ABERTA'`: a sessão `EM_FECHAMENTO` continua visível e pendente, mas não
impede outro operador de abrir o caixa no mesmo terminal. Cada sessão mantém o próprio fundo de
troco e o próprio resumo de fechamento; o dinheiro da sessão anterior deve ser conferido e
retirado antes da nova abertura.

Antes de aplicar a V15, confira se não existem duas sessões `ABERTA` para o mesmo
estabelecimento/terminal. O novo índice não corrige duplicidades existentes:

```sql
SELECT estabelecimento_id, COALESCE(terminal_id, 'SEM_TERMINAL'), COUNT(*)
FROM sessao_caixa
WHERE status = 'ABERTA'
GROUP BY estabelecimento_id, COALESCE(terminal_id, 'SEM_TERMINAL')
HAVING COUNT(*) > 1;
```

A V16 reforça duas invariantes. O índice `uq_sessao_caixa_aberta_operador` garante
uma única sessão `ABERTA` por operador no estabelecimento, mesmo que ele tente abrir
em outro terminal. Antes de aplicá-la, confira se não há duplicidade por operador:

```sql
SELECT estabelecimento_id, operador_usuario_id, COUNT(*)
FROM sessao_caixa
WHERE status = 'ABERTA'
GROUP BY estabelecimento_id, operador_usuario_id
HAVING COUNT(*) > 1;
```

A constraint `ck_configuracao_caixa_parcelas_maximas` limita a configuração a 12
parcelas. Como o `PUT` anterior não tinha teto, confira se alguma configuração
existente ultrapassa o limite antes de migrar:

```sql
SELECT id, estabelecimento_id, parcelas_maximas
FROM configuracao_caixa
WHERE parcelas_maximas > 12;
```

O índice `uq_pagamento_point_ativo_terminal` é global por `terminal_id`, sem escopo
de estabelecimento. O identificador pertence ao dispositivo físico, então o índice
também impede duas cobranças simultâneas no mesmo terminal entre tenants.

A V17 substitui o vínculo direto de sessão com terminal pelo modelo multi-terminal (`caixa`, `terminal_caixa` e `taxa_pagamento`), migra sessões e pagamentos existentes para o `Caixa Principal` (ou caixas por terminal legado), recria `uq_sessao_caixa_aberta` em `(caixa_id) WHERE status = 'ABERTA'` e remove `terminal_id` de `sessao_caixa` e `configuracao_caixa`.

Antes de aplicar a V10 em um banco que já possua pagamentos, confira se há valores de `forma`
ou `status` fora dos domínios documentados. As constraints são `NOT VALID` e não corrigem
dados automaticamente. A migration também cria um índice único em `referencia_externa`; se o
banco antigo tiver referências repetidas, remova ou corrija os duplicados antes de aplicá-la:

```sql
SELECT referencia_externa, COUNT(*)
FROM pagamento_pedido
WHERE referencia_externa IS NOT NULL
  AND BTRIM(referencia_externa) <> ''
GROUP BY referencia_externa
HAVING COUNT(*) > 1;
```

## Banco novo

Antes da primeira inicialização, execute `src/main/resources/db/script.sql`. Para criar os dados de demonstração, execute depois `src/main/resources/db/seed-inicial.sql`. Em seguida, inicie a aplicação: o Flyway registra a versão e valida a estrutura.

## Validação realizada

### V13 e V14 (1º de novembro de 2026)

Em PostgreSQL 18.3, foram validados dois caminhos:

1. **Banco novo:** `script.sql` atualizado, seguido de Flyway V1–V14. As 14 migrations foram aplicadas e a aplicação subiu com `spring.jpa.hibernate.ddl-auto=validate`.
2. **Banco existente:** banco na versão 12 recebeu V13 e V14 via Flyway. A aplicação subiu com `ddl-auto=validate`, e o histórico registrou as duas versões com sucesso.
3. **Paridade estrutural:** a comparação de tabelas, colunas, constraints, índices e sequências entre os dois caminhos não encontrou diferenças.
4. **Dados de exemplo:** foram aceitos pedido `DELIVERY` concluído, item com custo/categoria congelados e envio ao bar, pagamento dividido em dinheiro (com troco) e Pix, recebimento em sessão diferente da abertura e auditoria com autorizador.
5. **Casos negativos:** foram rejeitados canal inválido, troco inconsistente, dinheiro aprovado sem valor recebido, estorno acima do valor cobrado, quantidade enviada ao bar negativa, valor de auditoria negativo e evento de API sem operador.
6. **Exclusão de usuário:** o autorizador da auditoria pôde ser removido com preservação do nome; quem fechou a sessão e o operador com histórico tiveram a exclusão bloqueada.

### V15 e backend do caixa (3 de novembro de 2026)

Ainda em PostgreSQL 18.3, a V15 foi validada nos dois caminhos:

1. **Banco existente:** o banco local `valhalla` foi migrado da versão 14 para a 15 pelo Flyway. A aplicação iniciou com `ddl-auto=validate` e o histórico registrou a V15 com sucesso.
2. **Banco novo:** o banco `valhalla_schema_test_v15_20261103` foi criado com o `script.sql` atualizado, seguido de Flyway V1–V15. A aplicação iniciou com `ddl-auto=validate`.
3. **Comportamento do índice:** em transação revertida, foram aceitas uma sessão `EM_FECHAMENTO` e uma `ABERTA` no mesmo estabelecimento/terminal; duas sessões `ABERTA` no mesmo terminal foram rejeitadas por `uq_sessao_caixa_aberta`. A transação foi revertida e nenhum registro de teste permaneceu.
4. **Testes automatizados:** 12 cenários de integração do caixa em H2, 4 testes unitários da calculadora de fechamento e o teste de exportação do OpenAPI foram aprovados. A compilação Maven também foi aprovada.

O `script.sql` foi mantido em paridade com a V15. O H2 não cria o índice único parcial; por isso, a duplicidade de sessão também é validada no serviço e o comportamento do índice é verificado no PostgreSQL.

### V16 (3 de novembro de 2026)

A V16 foi validada nos dois caminhos, com backup do banco local antes da migração:

1. **Banco existente:** o banco local `valhalla` foi migrado da versão 15 para a 16 pelo Flyway. A aplicação iniciou com `ddl-auto=validate` e o histórico registrou a V16 com sucesso.
2. **Banco existente em cópia:** o banco `valhalla_schema_test_v16_upgrade_20261103`, criado a partir do banco de teste V15, recebeu a V16 pelo Flyway. A aplicação iniciou com `ddl-auto=validate`.
3. **Banco novo:** o banco `valhalla_schema_test_v16_20261103` foi criado com o `script.sql` atualizado, seguido de Flyway V1–V16. A V16 confirmou que o índice e a constraint já existiam, sem recriá-los, e a aplicação iniciou com `ddl-auto=validate`.
4. **Comportamento das invariantes:** em transação revertida, foram aceitas sessões `ABERTA` de operadores distintos em terminais distintos e uma `EM_FECHAMENTO` do mesmo operador/terminal. Foram rejeitadas duas `ABERTA` do mesmo operador em terminais distintos por `uq_sessao_caixa_aberta_operador`, duas `ABERTA` no mesmo terminal por `uq_sessao_caixa_aberta`, `parcelas_maximas = 13` por `ck_configuracao_caixa_parcelas_maximas` e aceito `parcelas_maximas = 12`. As pré-checagens da migration também foram exercitadas e produziram as mensagens esperadas. Nenhum registro de teste permaneceu.
5. **Testes automatizados:** 133 testes da suíte completa, incluindo 15 cenários de integração do caixa em H2, 4 testes unitários da calculadora de fechamento e o teste de exportação do OpenAPI. A compilação Maven também foi aprovada.

O `script.sql` foi mantido em paridade com a V16. O H2 não cria índices únicos parciais nem constraints de tabela adicionadas por migration; por isso, o comportamento do índice por operador e do teto de parcelas é verificado no PostgreSQL.

Antes de aplicar a V13 em um banco existente, confira se há valores de `pedido.canal` fora de `BALCAO`, `MESA` e `RETIRADA`. A V13 adiciona `DELIVERY` e valida a constraint; os três valores anteriores continuam válidos.

Antes de aplicar a V14, confira se as sessões `FECHADAS` têm `fechada_por_usuario_id` preenchido e referenciando um usuário existente. O `CHECK` já exige o preenchimento; a nova FK valida a referência.

## Próximas alterações

Não edite uma migração já executada em ambientes compartilhados. Crie um novo arquivo SQL com numeração crescente:

```text
V17__descricao_da_mudanca.sql
```

Exemplo: `V17__adicionar_auditoria_de_permissoes.sql`.
