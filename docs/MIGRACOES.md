# Migrações do banco de dados

O projeto utiliza Flyway para registrar e executar alterações incrementais no PostgreSQL.

## Banco já existente

1. Faça um backup do banco.
2. Atualize o código da aplicação.
3. Configure `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` para o banco correto.
4. Inicie a aplicação uma vez.

O Flyway cria a tabela `flyway_schema_history`, registra a estrutura anterior como versão `0` e executa, uma única vez, as migrações em `src/main/resources/db/migration`.

As migrações atuais criam ou atualizam `usuario_funcionalidade`, preservam permissões já atribuídas como `GRANT`, e inserem as funcionalidades administrativas. Perfis chamados `Gerente` recebem todas as funcionalidades existentes.

## Banco novo

Antes da primeira inicialização, execute `src/main/resources/db/script.sql`. Para criar os dados de demonstração, execute depois `src/main/resources/db/seed-inicial.sql`. Em seguida, inicie a aplicação: o Flyway registra a versão e valida a estrutura.

## Próximas alterações

Não edite uma migração já executada em ambientes compartilhados. Crie um novo arquivo SQL com numeração crescente:

```text
V3__descricao_da_mudanca.sql
```

Exemplo: `V3__adicionar_auditoria_de_permissoes.sql`.
