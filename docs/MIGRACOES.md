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

Antes de aplicar a V3 em um banco existente, confira se não há nomes de perfis que diferem apenas por capitalização. A migration não escolhe nem remove dados automaticamente.

Antes de aplicar a V4 em um banco existente, confira se não há categorias do mesmo estabelecimento que diferem apenas por capitalização. A migration também não escolhe nem remove dados automaticamente.

A V5 transforma custos nulos em `0` e estoques mínimos iguais a `0` em `NULL`. A mudança preserva o comportamento operacional e permite distinguir a ausência de estoque mínimo configurado.

A V6 exige que toda saída causada por um drink preserve a receita aplicada naquele momento. Isso mantém o histórico íntegro caso a receita seja alterada no futuro.

A V7 remove NCM e CEST de Produto e o NCM de Item de Nota Fiscal. Esses dados não participam do módulo de estoque atual.

A V8 permite ingredientes em `ML` ou `UN` na composição de drinks. Produtos fracionados usam
`ML`; ingredientes unitários, como uma lata de energético, usam `UN`.

A V11 cria `token_recuperacao_senha`, usada na recuperação de senha por e-mail. Apenas o hash
SHA-256 de cada token é armazenado, e os tokens são removidos junto com o usuário.

A V12 prepara o registro de vendas pela API e corrige a base dos relatórios:

- adiciona `item_pedido.custo_unitario` (custo no momento da venda). Itens já existentes recebem o custo atual
  do produto;
- adiciona `pedido.motivo_cancelamento` e restringe os valores de canal, status e forma de pagamento. As regras
  são criadas como `NOT VALID`: valem para novos registros e não bloqueiam a migração por dados antigos;
- adiciona `movimentacao_estoque.motivo_perda`, obrigatório em `PERDA`. As perdas existentes são classificadas
  pelo texto do motivo (vencido/vencimento, consumo interno ou quebra);
- cria as permissões `REGISTRAR_VENDA`, `CANCELAR_VENDA` e `VISUALIZAR_RELATORIOS`. Perfis `Gerente` recebem
  as três; `Atendente` recebe `REGISTRAR_VENDA`; perfis `Financeiro`, se existirem, recebem `VISUALIZAR_RELATORIOS`.

Os relatórios deixam de exigir `VISUALIZAR_ESTOQUE` e passam a exigir `VISUALIZAR_RELATORIOS`. Perfis que
precisam vê-los e não se chamam Gerente ou Financeiro devem recebê-la manualmente.

## Banco novo

Antes da primeira inicialização, execute `src/main/resources/db/script.sql`. Para criar os dados de demonstração, execute depois `src/main/resources/db/seed-inicial.sql`. Em seguida, inicie a aplicação: o Flyway registra a versão e valida a estrutura.

## Próximas alterações

Não edite uma migração já executada em ambientes compartilhados. Crie um novo arquivo SQL com numeração crescente:

```text
V6__descricao_da_mudanca.sql
```

Exemplo: `V6__adicionar_auditoria_de_permissoes.sql`.
