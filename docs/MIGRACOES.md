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

Antes de aplicar a V3 em um banco existente, confira se não há nomes de perfis que diferem apenas por capitalização. A migration não escolhe nem remove dados automaticamente.

Antes de aplicar a V4 em um banco existente, confira se não há categorias do mesmo estabelecimento que diferem apenas por capitalização. A migration também não escolhe nem remove dados automaticamente.

## Banco novo

Antes da primeira inicialização, execute `src/main/resources/db/script.sql`. Para criar os dados de demonstração, execute depois `src/main/resources/db/seed-inicial.sql`. Em seguida, inicie a aplicação: o Flyway registra a versão e valida a estrutura.

## Próximas alterações

Não edite uma migração já executada em ambientes compartilhados. Crie um novo arquivo SQL com numeração crescente:

```text
V5__descricao_da_mudanca.sql
```

Exemplo: `V5__adicionar_auditoria_de_permissoes.sql`.
