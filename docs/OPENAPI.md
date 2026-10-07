# OpenAPI

O contrato OpenAPI versionado fica em `docs/openapi.yaml` e é gerado diretamente pela aplicação,
incluindo endpoints, DTOs, respostas, cookies de autenticação e requisito de CSRF.

Para atualizá-lo após uma alteração de contrato, execute:

```powershell
.\mvnw.cmd test "-Dtest=OpenApiExportIntegrationTest" "-Dopenapi.export.path=docs/openapi.yaml"
```

O teste sobe o contexto de integração já usado pela aplicação, solicita `/v3/api-docs.yaml` e
atualiza `docs/openapi.yaml`. Ele valida a presença das rotas de estoque e da segurança CSRF antes
de gravar o arquivo; portanto, a exportação falha se o contrato não estiver disponível.
