# Configuração e variáveis de ambiente

Este documento descreve todas as variáveis aceitas pela API, os valores padrão, a
ordem de precedência e os erros de configuração mais comuns.

## Como a configuração é carregada

A aplicação usa a configuração externalizada do Spring Boot. Da menor para a maior
precedência:

1. `application.properties` empacotado no jar (padrões do projeto)
2. `application-{profile}.properties` empacotado no jar (ex.: `application-dev.properties`)
3. `.env` na raiz do projeto, importado por `spring.config.import=optional:file:.env[.properties]`
4. Variáveis de ambiente do processo
5. Argumentos de linha de comando (`--server.port=9090`)

Consequências práticas:

- **Variáveis de ambiente reais sempre vencem o `.env`.** Em containers, CI e
  produção, defina as variáveis no ambiente e não copie o `.env`.
- O `.env` é opcional: sem ele a aplicação inicia usando os padrões locais, desde
  que `DB_PASSWORD` e `JWT_SECRET` estejam definidos.
- O `.env` é lido no formato `.properties`, não no formato de shell:
  - não use `export`;
  - **não use aspas** (`JWT_SECRET="..."` é interpretado com as aspas);
  - não use espaços ao redor do `=`;
  - listas são separadas por vírgula.
- Para ativar um perfil dentro do `.env`, use o nome canônico
  `spring.profiles.active=dev`. Ao exportar como variável de ambiente do processo,
  use `SPRING_PROFILES_ACTIVE=dev`.
- Os testes usam `src/test/resources/application.properties`, que substitui o
  arquivo principal. Por isso o `.env` não é lido durante `mvnw test`.

## Variáveis do projeto

| Variável | Propriedade Spring | Obrigatória | Padrão | Descrição |
| --- | --- | --- | --- | --- |
| `DB_URL` | `spring.datasource.url` | não | `jdbc:postgresql://localhost:5432/valhalla` | URL JDBC do PostgreSQL. |
| `DB_USERNAME` | `spring.datasource.username` | não | `postgres` | Usuário do banco. |
| `DB_PASSWORD` | `spring.datasource.password` | **sim** | — | Senha do banco. A aplicação não inicia sem ela. |
| `JWT_SECRET` | `security.jwt.secret` | **sim** | — | Segredo HMAC em Base64 com pelo menos 32 bytes. Diferente por ambiente. |
| `JWT_EXPIRATION` | `security.jwt.expiration` | não | `PT1H` | Validade do access token (ISO-8601 ou `1h`). |
| `JWT_REFRESH_EXPIRATION` | `security.jwt.refresh-expiration` | não | `P7D` | Validade do refresh token. Deve ser maior que `JWT_EXPIRATION`. |
| `FRONTEND_URL` | `app.cors.allowed-origins` | não | `http://localhost:5173` | Origem(ns) do frontend. Aceita várias separadas por vírgula, sem barra final. |
| `COOKIE_SECURE` | `app.cookie.secure` | não | `true` | Exige HTTPS nos cookies de autenticação. O perfil `dev` força `false`. |

Propriedades do próprio Spring Boot também funcionam como variáveis de ambiente,
sem qualquer código adicional, por exemplo `SERVER_PORT`, `SPRING_PROFILES_ACTIVE`,
`SPRING_APPLICATION_JSON` e `SPRING_DATASOURCE_*`.

### Variáveis lidas apenas pelo Logback

Estas variáveis são lidas diretamente pelo `logback-spring.xml` (propriedades de
sistema ou ambiente), **não** por `application.properties`:

| Variável | Padrão | Descrição |
| --- | --- | --- |
| `LOG_PATH` | `logs` | Diretório dos arquivos de log, relativo ao diretório de execução. |
| `LOG_LEVEL_APP` | `INFO` | Nível de log do pacote `valhalla.core.stock.app`. |
| `LOG_HISTORY_DAYS` | `30` | Dias de retenção dos arquivos rotacionados. |
| `LOG_TOTAL_SIZE_CAP` | `1GB` | Tamanho total máximo dos logs. |
| `LOG_FILE_MAX_SIZE` | `25MB` | Tamanho máximo de cada arquivo antes da rotação. |

## Perfis

| Perfil | Efeito |
| --- | --- |
| `default` (nenhum perfil) | Somente migrações de `db/migration`; cookies exigem HTTPS. |
| `dev` | Aplica os dados de demonstração de `db/demo` na inicialização e define `app.cookie.secure=false`. |

> **Atenção:** o perfil `dev` executa `db/demo/dados_demonstracao.sql`, que cria
> usuários com senha conhecida (`Senha@123`). Nunca o ative em produção. No `.env`,
> ele vem comentado de propósito.

### Como os dados de demonstração são aplicados

O seed **não** é uma migração Flyway. Quem o executa é o bean `DemoDataSeeder`
(`@Profile("dev")`), via `ResourceDatabasePopulator`, depois que o Flyway aplica
`db/migration`. Motivos:

- dados de demonstração não são schema e não devem entrar em `flyway_schema_history`;
- quando o seed era uma migração repetível, qualquer execução **sem** `dev` falhava
  com `Detected applied migration not resolved locally: dados demonstracao`;
- o script é idempotente, então rodar a cada boot não duplica registros e edições
  no arquivo passam a valer sem `flyway repair`.

O seed usa `crypt()`/`gen_salt()`, então exige a extensão `pgcrypto` criada por
`db/script.sql`. Em um banco inicializado pelas migrações e sem esse script, o
perfil `dev` falhará ao semear.

### Bancos que já rodaram o perfil `dev` antes desta mudança

O registro antigo em `flyway_schema_history` não impede mais o boot:
`spring.flyway.ignore-migration-patterns=repeatable:missing` tolera apenas
migrações **repetíveis** ausentes do classpath. Migrações versionadas continuam
validadas de forma estrita. Se quiser limpar o histórico:

```sql
DELETE FROM flyway_schema_history WHERE description = 'dados demonstracao';
```

## Gerar um `JWT_SECRET`

PowerShell 7 (ou .NET moderno):

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Windows PowerShell 5.1:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

Linux/macOS:

```bash
openssl rand -base64 32
```

Use um valor diferente para cada ambiente e nunca versione o `.env`.

## Erros comuns

| Mensagem / sintoma | Causa | Correção |
| --- | --- | --- |
| `Could not resolve placeholder 'DB_PASSWORD'` | `DB_PASSWORD` não definido. | Defina no `.env` ou no ambiente. |
| `JWT_SECRET ... é obrigatório` | `.env` sem `JWT_SECRET` ou com valor vazio. | Gere um secret Base64 de 32 bytes. |
| `JWT_SECRET ... deve ser Base64 válido` | Aspas, espaços ou quebra de linha no valor. | Remova as aspas; mantenha o valor em uma única linha. |
| `JWT_SECRET ... deve representar pelo menos 32 bytes` | Secret curto. | Gere um novo com 32 bytes aleatórios. |
| `JWT_REFRESH_EXPIRATION deve ser maior que ...` | Refresh menor ou igual ao access token. | Ajuste as durações. |
| `FRONTEND_URL ... não deve conter caminho ... nem barra final` | Origem com `/` no final ou com path. | Use `https://app.exemplo.com`, sem `/`. |
| CORS bloqueado no navegador | Origem diferente da configurada (inclusive `localhost` × `127.0.0.1`). | Configure exatamente a origem exibida pelo navegador. |
| Login responde 200, mas a sessão não persiste em HTTP local | `COOKIE_SECURE=true` em ambiente sem HTTPS. | Use o perfil `dev` ou `COOKIE_SECURE=false` localmente. |
| Dados de demonstração não aparecem | Perfil `dev` não ativado. | Use `spring.profiles.active=dev` no `.env`. |
