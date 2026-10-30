# Adega Meraki — Core Stock API

API REST multiestabelecimento para autenticação, administração de usuários e
permissões, controle de estoque e dashboard da Adega Meraki.

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![Maven Wrapper](https://img.shields.io/badge/Maven-Wrapper-C71A36?logo=apachemaven&logoColor=white)
![OpenAPI 3.1](https://img.shields.io/badge/OpenAPI-3.1-6BA539?logo=openapiinitiative&logoColor=white)
![Status](https://img.shields.io/badge/status-em%20desenvolvimento-yellow)

> Backend consumido pelo frontend da Adega Meraki. Este repositório não inclui
> interface gráfica.

## Índice

- [Visão geral](#visão-geral) · [Stack](#stack) · [Arquitetura](#arquitetura)
- [Começando](#começando) · [Configuração](#configuração) · [Documentação da API](#documentação-da-api)
- [Autenticação e CSRF](#autenticação-e-csrf) · [Endpoints](#endpoints) · [Permissões](#permissões)
- [Paginação e erros](#paginação-e-erros) · [Testes](#testes) · [Solução de problemas](#solução-de-problemas)
- [Documentação complementar](#documentação-complementar) · [Contribuição](#contribuição) · [Licença](#licença)

## Visão geral

O **Core Stock API** centraliza o acesso e as operações do estabelecimento:

- **Autenticação** com JWT em cookies `HttpOnly`, rotação de refresh token,
  proteção CSRF e revogação de sessões.
- **Usuários** com cadastro, consulta, atualização e exclusão, incluindo rotas
  para a própria conta.
- **Perfis e permissões** por estabelecimento, com sobrescritas individuais
  `GRANT` e `REVOKE`.
- **Estoque** com categorias, produtos, entradas, saídas, ajustes, perdas e
  histórico auditável.
- **Drinks** com receita rastreável e baixa automática dos ingredientes em uma
  única transação.
- **Dashboard** com indicadores de estoque, alertas, série mensal e últimas
  movimentações.
- **Isolamento por estabelecimento**: o vínculo vem do token, nunca do corpo da
  requisição. Acesso a dados de outro estabelecimento é bloqueado.

Fora do escopo atual: financeiro, caixa, pedidos e NF-e. A dashboard sinaliza
esses módulos como `INDISPONIVEL`.

## Stack

| Camada | Tecnologia |
| --- | --- |
| Runtime | Java 21 |
| Framework | Spring Boot 4.1.1 — Web MVC, Data JPA, Security, OAuth2 Resource Server e Validation |
| Banco de dados | PostgreSQL; H2 em memória nos testes |
| Migrações | Flyway |
| Autenticação | JWT HS256 em cookies `HttpOnly` + token CSRF |
| Documentação | springdoc-openapi 3.0.2 e contrato OpenAPI 3.1 versionado |
| Build | Maven Wrapper (`mvnw` / `mvnw.cmd`) |
| Logs | SLF4J/Logback com arquivos em JSON (ECS) |

## Arquitetura

Monólito modular organizado por domínio. Cada módulo concentra controller, DTOs,
serviço, repositório e entidades; a autorização fica na camada de serviço com
`@PreAuthorize`.

```text
src/main/java/valhalla/core/stock/app/
├── modules/
│   ├── auth/              # login, refresh, logout, CSRF e JWT
│   ├── users/             # cadastro e administração de usuários
│   ├── accesscontrol/     # perfis, funcionalidades e sobrescritas
│   ├── establishments/    # estabelecimentos
│   ├── estoque/           # categorias, produtos, movimentações e receitas
│   └── dashboard/         # indicadores consolidados
└── shared/                # configuração, erros, paginação, logging e seed de dev

src/main/resources/
├── db/                    # script consolidado, seeds e migrações Flyway
├── application.properties
├── application-dev.properties
└── logback-spring.xml

docs/                      # configuração, migrações, regras e contrato OpenAPI
```

Fluxo de uma requisição autenticada:

```text
Cookie accessToken → SecurityFilterChain (JWT + sessão ativa)
                   → CSRF filter (X-XSRF-TOKEN em escritas)
                   → Controller → Service (@PreAuthorize) → Repository → PostgreSQL
```

Decisões relevantes:

- **Strategy + Command** para movimentações: cada tipo (`ENTRADA`, `SAIDA`,
  `AJUSTE_POSITIVO`, `AJUSTE_NEGATIVO`, `PERDA`, `ENTRADA_INICIAL`,
  `SAIDA_DRINK`) tem sua própria estratégia de validação e cálculo de saldo.
- **Uma sessão ativa por usuário**, registrada em `sessao_usuario`; login,
  refresh, logout e alterações sensíveis revogam tokens anteriores.
- **Flyway como fonte da verdade** do schema; o Hibernate usa `ddl-auto=validate`.
- **Paginação no banco** com `Specification` e `Pageable`, sem carregar o
  estoque inteiro em memória.

## Começando

### Pré-requisitos

- **JDK 21** configurado em `JAVA_HOME`.
- **PostgreSQL** acessível, com permissão para criar a extensão `pgcrypto`.
- **Git**.
- Maven não é necessário: o projeto inclui o Maven Wrapper.
- Windows (PowerShell), Linux ou macOS.

### 1. Clone e prepare o banco

```bash
git clone https://github.com/projeto-sptech-grupo-8/valhala.core.stock.app.git
cd valhala.core.stock.app

psql -U postgres -c "CREATE DATABASE valhalla;"
psql -U postgres -d valhalla -f src/main/resources/db/script.sql
```

Se `psql` não estiver no `PATH`, execute os scripts pelo DBeaver ou outro
cliente SQL. O `script.sql` cria a extensão `pgcrypto`, usada pelo seed de
demonstração.

Opcionalmente, carregue a carga inicial mínima:

```bash
psql -U postgres -d valhalla -f src/main/resources/db/seed-inicial.sql
```

Ela cria o estabelecimento de exemplo, os perfis `Gerente` e `Atendente` e o
usuário `admin@valhalla.local` com a senha `Senha@123`. **Altere esses dados
antes de qualquer uso real.**

Na primeira inicialização, o Flyway registra a estrutura existente como versão
`0` e aplica as migrações pendentes. Para bancos já existentes, faça backup e
siga [docs/MIGRACOES.md](docs/MIGRACOES.md). As migrações `V3` e `V4` exigem que
não existam perfis ou categorias duplicados apenas por diferença de
maiúsculas/minúsculas.

### 2. Configure as variáveis de ambiente

```powershell
# Windows
Copy-Item .env.example .env
```

```bash
# Linux/macOS
cp .env.example .env
```

Edite o `.env` e defina ao menos `DB_PASSWORD` e `JWT_SECRET`. Para gerar um
secret seguro:

```powershell
# PowerShell 7+
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

```bash
# Linux/macOS
openssl rand -base64 32
```

> O `.env` usa o formato `.properties`: **não use aspas** nem espaços ao redor
> do `=`. Variáveis de ambiente do processo sempre têm precedência sobre o
> arquivo. Nunca versione o `.env`.

### 3. Inicie a aplicação

```powershell
# Windows
.\mvnw.cmd spring-boot:run
```

```bash
# Linux/macOS
./mvnw spring-boot:run
```

| Recurso | URL |
| --- | --- |
| API | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Contrato versionado | [docs/openapi.yaml](docs/openapi.yaml) |

Para gerar o artefato de produção:

```powershell
.\mvnw.cmd clean package
java -jar target/core.stock.app-0.0.1-SNAPSHOT.jar
```

### Dados de demonstração

Descomente no `.env` para carregar o cenário completo de desenvolvimento:

```properties
spring.profiles.active=dev
```

O perfil `dev` aplica `db/demo/dados_demonstracao.sql` por meio do
`DemoDataSeeder`, depois que o Flyway termina. Todos os usuários de demonstração
usam a senha `Senha@123`, incluindo `dev@meraki.com` (perfil Gerente). O perfil
também força `COOKIE_SECURE=false` para permitir login em HTTP local.

> **Nunca use o perfil `dev` em produção.** Ele cria usuários com senha
> conhecida. O seed depende da extensão `pgcrypto` criada por `script.sql`.

## Configuração

| Variável | Obrigatória | Padrão | Descrição |
| --- | --- | --- | --- |
| `DB_URL` | não | `jdbc:postgresql://localhost:5432/valhalla` | URL JDBC do PostgreSQL. |
| `DB_USERNAME` | não | `postgres` | Usuário do banco. |
| `DB_PASSWORD` | **sim** | — | Senha do banco. |
| `JWT_SECRET` | **sim** | — | Segredo HMAC em Base64 com pelo menos 32 bytes (256 bits). |
| `JWT_EXPIRATION` | não | `PT1H` | Validade do access token (ISO-8601). |
| `JWT_REFRESH_EXPIRATION` | não | `P7D` | Validade do refresh token; deve ser maior que `JWT_EXPIRATION`. |
| `FRONTEND_URL` | não | `http://localhost:5173` | Origem(ns) permitida(s) no CORS, separadas por vírgula, sem barra final. |
| `COOKIE_SECURE` | não | `true` | Exige HTTPS nos cookies. Use `false` apenas em desenvolvimento local. |

As variáveis `LOG_PATH`, `LOG_LEVEL_APP`, `LOG_HISTORY_DAYS`,
`LOG_TOTAL_SIZE_CAP` e `LOG_FILE_MAX_SIZE` são lidas diretamente pelo Logback e
são opcionais. A referência completa, incluindo precedência e erros comuns, está
em [docs/CONFIGURACAO.md](docs/CONFIGURACAO.md).

## Documentação da API

O contrato OpenAPI é gerado pela própria aplicação e versionado em
[docs/openapi.yaml](docs/openapi.yaml). Use o Swagger UI para explorar e testar
as rotas com os cookies da sessão.

Após qualquer alteração de controller ou DTO, atualize o contrato:

```powershell
.\mvnw.cmd test "-Dtest=OpenApiExportIntegrationTest" "-Dopenapi.export.path=docs/openapi.yaml"
```

Detalhes do processo em [docs/OPENAPI.md](docs/OPENAPI.md).

## Autenticação e CSRF

### Cookies

| Cookie | `HttpOnly` | `Path` | Conteúdo |
| --- | --- | --- | --- |
| `accessToken` | sim | `/` | JWT de acesso (`purpose=access`). |
| `refreshToken` | sim | `/auth` | JWT de renovação (`purpose=refresh`). |
| `XSRF-TOKEN` | não | `/` | Token CSRF; aceito como fallback apenas para a origem configurada. |

O JavaScript **não deve** ler ou armazenar `accessToken` e `refreshToken`, nem
enviá-los no header `Authorization`. Todas as chamadas precisam usar
`credentials: "include"`.

Os cookies usam `SameSite=Strict`; por isso, frontend e API devem compartilhar o
mesmo site (por exemplo, subdomínios do mesmo domínio).

### Proteção CSRF

Requisições autenticadas que alteram estado (`POST`, `PUT`, `PATCH` e `DELETE`)
exigem o header `X-XSRF-TOKEN`, com exceção de `POST /auth/login`.

1. Após o login ou o refresh, chame `GET /auth/csrf`.
2. Guarde o campo `token` apenas em memória.
3. Envie-o em `X-XSRF-TOKEN` nas escritas.
4. Repita a consulta após cada refresh, pois o token anterior é invalidado.

O cookie `XSRF-TOKEN` funciona como fallback somente quando a requisição parte
de uma origem listada em `FRONTEND_URL`. Para o frontend, prefira o header
obtido em `GET /auth/csrf`.

Sem um token válido, a API responde `403 Forbidden` com a mensagem
`Token CSRF ausente ou inválido`. Os endpoints de refresh e logout também são
protegidos.

### Endpoints de autenticação

| Método | Endpoint | Autenticação | CSRF | Sucesso |
| --- | --- | --- | --- | --- |
| `POST` | `/auth/login` | Público | não | `200 OK` e criação dos cookies |
| `POST` | `/auth/refresh` | Cookie `refreshToken` | sim | `200 OK` e rotação dos cookies |
| `GET` | `/auth/csrf` | Cookie `accessToken` | não | `200 OK` com `{ "token": "..." }` |
| `POST` | `/auth/logout` | Cookies opcionais | sim* | `204 No Content` e remoção dos cookies |

\* O logout é idempotente: sem sessão válida, retorna `204` sem exigir CSRF.

O login e o refresh retornam somente uma confirmação e o nome do usuário. Os
dados completos devem ser carregados por `GET /usuario/me`:

```json
{
  "mensagem": "Autenticação realizada com sucesso",
  "usuario": "Dev Gerente"
}
```

O access token contém os claims `userId`, `establishmentId`, `sessionJti`,
`roles`, `permissions` e `purpose`. O refresh token contém `userId`,
`establishmentId` e `purpose=refresh`.

### Cliente frontend

Exemplo de cliente `fetch` que obtém o CSRF, renova a sessão uma única vez e
propaga os erros da API:

```javascript
const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

let csrfToken = null;

export async function renovarCsrf() {
  const response = await fetch(`${API_URL}/auth/csrf`, {
    credentials: "include"
  });

  if (!response.ok) {
    csrfToken = null;
    return null;
  }

  csrfToken = (await response.json()).token;
  return csrfToken;
}

async function tokenParaEscrita() {
  return csrfToken ?? (await renovarCsrf().catch(() => null));
}

export async function apiRequest(path, options = {}, permitirRefresh = true) {
  const method = (options.method ?? "GET").toUpperCase();
  const exigeCsrf =
    !["GET", "HEAD", "OPTIONS"].includes(method) && path !== "/auth/login";

  const headers = new Headers(options.headers);
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  if (exigeCsrf) {
    const token = await tokenParaEscrita();
    if (token) headers.set("X-XSRF-TOKEN", token);
  }

  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    method,
    headers,
    credentials: "include"
  });

  if (response.status === 401 && permitirRefresh && !path.startsWith("/auth/")) {
    const refreshToken = await tokenParaEscrita();
    const refresh = await fetch(`${API_URL}/auth/refresh`, {
      method: "POST",
      credentials: "include",
      headers: refreshToken ? { "X-XSRF-TOKEN": refreshToken } : {}
    });

    if (refresh.ok) {
      csrfToken = null; // o refresh invalida o token anterior
      await renovarCsrf();
      return apiRequest(path, options, false);
    }
  }

  if (response.status === 204) {
    if (path === "/auth/logout") csrfToken = null;
    return null;
  }

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(data?.message ?? "Erro ao acessar a API");
    error.status = response.status;
    error.fieldErrors = data?.fieldErrors ?? {};
    throw error;
  }

  return data;
}
```

Uso:

```javascript
await apiRequest("/auth/login", {
  method: "POST",
  body: JSON.stringify({ email: "dev@meraki.com", senha: "Senha@123" })
});

await renovarCsrf();
const usuarioAtual = await apiRequest("/usuario/me");
```

### Regras de sessão

- Cada refresh token é de uso único; o endpoint troca access e refresh tokens.
- Um novo login invalida a sessão anterior do mesmo usuário.
- Ao receber `401` em rota protegida, tente `POST /auth/refresh` uma única vez e
  repita a requisição original. Se o refresh falhar, limpe o estado e abra o
  login. Não tente refresh para erros das próprias rotas `/auth`.
- Alterações de senha, perfil, status ou permissões revogam sessões. O frontend
  deve considerar o usuário desconectado nesses casos.

## Endpoints

Todas as rotas abaixo, exceto login/refresh, exigem sessão autenticada.
Escritas exigem CSRF. Os corpos, validações e exemplos completos estão no
Swagger UI e em [docs/openapi.yaml](docs/openapi.yaml).

### Usuários

| Método | Endpoint | Quem pode usar | Sucesso |
| --- | --- | --- | --- |
| `POST` | `/usuario` | `USUARIOS_CRIAR` | `201 Created` + `Location` |
| `GET` | `/usuario` | `USUARIOS_VISUALIZAR` | `200 OK` |
| `GET` | `/usuario/me` | Usuário autenticado | `200 OK` |
| `GET` | `/usuario/{id}` | Próprio usuário ou `USUARIOS_VISUALIZAR` | `200 OK` |
| `PATCH` | `/usuario/me` | Usuário autenticado | `200 OK` |
| `DELETE` | `/usuario/me` | Usuário autenticado | `204 No Content` |
| `PATCH` | `/usuario/{id}` | Próprio usuário ou `USUARIOS_EDITAR` | `200 OK` |
| `DELETE` | `/usuario/{id}` | Próprio usuário ou `USUARIOS_EXCLUIR` | `204 No Content` |

Regras principais:

- `nome` (até 100), `email` (válido e único), `senha` (8 a 72) e `nomePerfil`
  são obrigatórios na criação; `telefone` é opcional (até 13).
- `PATCH` exige ao menos um campo. Apenas quem possui `USUARIOS_EDITAR` pode
  alterar `nomePerfil` ou `ativo`.
- String vazia em `telefone` remove o telefone.
- A exclusão retorna `409` quando há vínculos ou quando removeria o último
  usuário apto a criar usuários.
- Aliases em inglês (`name`, `phone`, `password`, `profileName`, `active`) são
  aceitos nos corpos; as respostas usam apenas os nomes em português.

### Perfis e permissões

| Método | Endpoint | Permissão |
| --- | --- | --- |
| `GET` | `/autorizacoes/funcionalidades` | `PERMISSOES_GERENCIAR` |
| `GET` | `/autorizacoes/perfis` | `PERFIS_GERENCIAR` |
| `GET` | `/autorizacoes/perfis/{profileId}` | `PERFIS_GERENCIAR` |
| `POST` | `/autorizacoes/perfis` | `PERFIS_GERENCIAR` |
| `PATCH` | `/autorizacoes/perfis/{profileId}` | `PERFIS_GERENCIAR` |
| `DELETE` | `/autorizacoes/perfis/{profileId}` | `PERFIS_GERENCIAR` |
| `PUT` | `/autorizacoes/perfis/{profileId}/funcionalidades/codigos` | `PERFIS_GERENCIAR` |
| `PUT` | `/autorizacoes/usuarios/{userId}/sobrescritas-permissao` | `PERMISSOES_GERENCIAR` |
| `GET` | `/autorizacoes/usuarios/{userId}/permissoes` | `PERMISSOES_GERENCIAR` |

O nome do perfil é único por estabelecimento sem diferenciar maiúsculas de
minúsculas. A substituição de funcionalidades ou de sobrescritas revoga as
sessões dos usuários afetados e não pode remover a última capacidade de criar
usuários (`409`). Perfis com usuários vinculados não podem ser excluídos.

### Estoque

| Método | Endpoint | Permissão | Sucesso |
| --- | --- | --- | --- |
| `GET` | `/categorias` | `VISUALIZAR_ESTOQUE` | `200 OK` paginado |
| `POST` | `/categorias` | `GERENCIAR_ESTOQUE` | `201 Created` |
| `GET` | `/categorias/{id}` | `VISUALIZAR_ESTOQUE` | `200 OK` |
| `PATCH` | `/categorias/{id}` | `GERENCIAR_ESTOQUE` | `200 OK` |
| `DELETE` | `/categorias/{id}` | `GERENCIAR_ESTOQUE` | `204 No Content` |
| `GET` | `/produtos` | `VISUALIZAR_ESTOQUE` | `200 OK` paginado |
| `POST` | `/produtos` | `GERENCIAR_ESTOQUE` | `201 Created` |
| `GET` | `/produtos/{id}` | `VISUALIZAR_ESTOQUE` | `200 OK` |
| `PATCH` | `/produtos/{id}` | `GERENCIAR_ESTOQUE` | `200 OK` |
| `DELETE` | `/produtos/{id}` | `GERENCIAR_ESTOQUE` | `204 No Content` |
| `GET` | `/movimentacoes-estoque` | `VISUALIZAR_ESTOQUE` | `200 OK` paginado |
| `POST` | `/movimentacoes-estoque` | `MOVIMENTAR_ESTOQUE` | `201 Created` |
| `POST` | `/movimentacoes-estoque/saida-drink` | `MOVIMENTAR_ESTOQUE` | `201 Created` |
| `GET` | `/drinks/{idDrink}/receita` | `VISUALIZAR_ESTOQUE` | `200 OK` |
| `PUT` | `/drinks/{idDrink}/receita` | `GERENCIAR_ESTOQUE` | `200 OK` |

Regras principais:

- O `sku` é gerado exclusivamente pelo backend e é único no estabelecimento.
- A categoria é obrigatória e precisa pertencer ao estabelecimento da sessão.
  Categorias com produtos vinculados não podem ser excluídas (`409`).
- `tipo` aceita `PADRAO` (padrão) ou `DRINK`; drinks não possuem saldo próprio.
- Produto fracionado exige `volumeEmbalagemMl` e tem saldo controlado em `ML`.
  Depois que há saldo ou movimentações, não é permitido alterar `fracionado`
  nem o volume da embalagem.
- O saldo não pode ser alterado diretamente pelo `PATCH`; toda alteração deve
  gerar movimentação. Produtos com movimentações ou vínculos não podem ser
  excluídos (`409`).
- Tipos de movimentação: `ENTRADA_INICIAL`, `ENTRADA`, `SAIDA`,
  `AJUSTE_POSITIVO`, `AJUSTE_NEGATIVO`, `PERDA` e `SAIDA_DRINK`. Apenas
  `ENTRADA`, `SAIDA`, `AJUSTE_POSITIVO`, `AJUSTE_NEGATIVO` e `PERDA` são aceitos
  diretamente; `ENTRADA_INICIAL` é gerada na criação do produto.
- Nenhuma movimentação pode deixar o saldo negativo. A quantidade usa a
  unidade-base do estoque: `ML` para fracionados e a unidade cadastrada para os
  demais.
- A receita deve ter ao menos um ingrediente, sem repetição, usando produtos
  `PADRAO` ativos e unidades `ML` ou `UN`. A `SAIDA_DRINK` valida todos os
  ingredientes antes de alterar qualquer saldo e executa a baixa em uma única
  transação, preservando um snapshot da receita aplicada.

A margem do produto é calculada pela API:
`(precoVenda - precoCusto) / precoVenda × 100`. As regras completas estão em
[docs/REGRAS_NEGOCIO_ESTOQUE.md](docs/REGRAS_NEGOCIO_ESTOQUE.md).

### Dashboard

| Método | Endpoint | Permissão | Sucesso |
| --- | --- | --- | --- |
| `GET` | `/dashboard/resumo?meses=6` | `VISUALIZAR_ESTOQUE` | `200 OK` |

`meses` aceita de `1` a `24` e define o tamanho da série mensal, incluindo o mês
atual. A resposta contém `referencia`, `estoque` (totais e alertas),
`movimentacoes` (série mensal e últimas movimentações), `financeiro` e `caixa`
— os dois últimos como `INDISPONIVEL`.

## Permissões

As permissões são isoladas por estabelecimento. A permissão efetiva é a união
das funcionalidades do perfil com as sobrescritas individuais `GRANT` e
`REVOKE`. No seed inicial, o perfil `Gerente` recebe todas as funcionalidades
existentes.

| Código | Descrição |
| --- | --- |
| `USUARIOS_CRIAR` | Cadastrar usuários. |
| `USUARIOS_VISUALIZAR` | Consultar usuários. |
| `USUARIOS_EDITAR` | Alterar usuários. |
| `USUARIOS_EXCLUIR` | Excluir usuários. |
| `PERFIS_GERENCIAR` | Criar, editar e excluir perfis. |
| `PERMISSOES_GERENCIAR` | Configurar permissões de perfil e de usuário. |
| `GERENCIAR_ESTOQUE` | Administrar categorias e produtos. |
| `MOVIMENTAR_ESTOQUE` | Registrar entradas, saídas, ajustes, perdas e baixas de drink. |
| `VISUALIZAR_ESTOQUE` | Consultar categorias, produtos, saldos e movimentações. |

O estabelecimento precisa manter ao menos um usuário ativo capaz de criar
usuários. A API rejeita operações que removeriam essa última capacidade com
`409 Conflict`.

## Paginação e erros

### Paginação

Categorias, produtos e movimentações são filtrados, ordenados e paginados no
banco:

| Parâmetro | Regra |
| --- | --- |
| `pagina` | Inicia em `0`. Padrão: `0`. |
| `tamanho` | Itens por página. Padrão: `20`; máximo: `100`. |
| `ordenarPor` | Campo documentado no Swagger para o endpoint. |
| `direcao` | `ASC` ou `DESC`, conforme o padrão do endpoint. |

A resposta segue o formato `itens`, `pagina`, `tamanho`, `totalItens`,
`totalPaginas`, `primeira` e `ultima`.

| Recurso | Filtros | Ordenação padrão |
| --- | --- | --- |
| `GET /categorias` | `busca`, `ativo` | `nome` ASC |
| `GET /produtos` | `busca`, `categoriaId`, `tipo`, `ativo` | `nome` ASC |
| `GET /movimentacoes-estoque` | `produtoId`, `tipo`, `usuarioId`, `dataInicial`, `dataFinal` | `ocorridoEm` DESC |

Em movimentações, `dataInicial` e `dataFinal` usam `yyyy-MM-dd` e a data final é
inclusiva. Filtros omitidos não restringem a consulta.

### Erros

```json
{
  "timestamp": "2026-10-29T10:05:14.307493",
  "status": 400,
  "message": "Dados de entrada inválidos",
  "fieldErrors": { "email": "E-mail inválido" }
}
```

| Status | Significado | Ação recomendada no frontend |
| --- | --- | --- |
| `400` | Body ou parâmetros inválidos, regra de negócio violada | Exibir `fieldErrors` ou `message`. |
| `401` | Credenciais ou sessão inválidas | Tentar refresh uma vez; se falhar, abrir login. |
| `403` | Sem permissão, acesso cruzado ou CSRF inválido | Informar acesso negado; renovar o CSRF quando aplicável. |
| `404` | Recurso não encontrado | Atualizar a tela e informar que o recurso não existe. |
| `409` | Duplicidade, vínculo existente ou último administrador | Exibir a mensagem sem repetir automaticamente. |

## Testes

```powershell
# Windows
.\mvnw.cmd test
```

```bash
# Linux/macOS
./mvnw test
```

Os testes usam H2 em memória, com Flyway desabilitado, e não alteram o
PostgreSQL local. A suíte cobre autenticação, permissões, usuários, categorias,
produtos, movimentações, receitas, dashboard e o contrato OpenAPI.

## Solução de problemas

| Sintoma | Causa provável | Correção |
| --- | --- | --- |
| `Could not resolve placeholder 'DB_PASSWORD'` | Variável não definida. | Defina no `.env` ou no ambiente. |
| `JWT_SECRET ... é obrigatório` / `Base64 válido` | Secret ausente, curto ou com aspas/espaços. | Gere um Base64 de 32 bytes em uma única linha. |
| Login responde `200`, mas a sessão não persiste em HTTP | `COOKIE_SECURE=true` sem HTTPS. | Use o perfil `dev` ou `COOKIE_SECURE=false` localmente. |
| `403 Token CSRF ausente ou inválido` | Escrita sem `X-XSRF-TOKEN` ou token expirado. | Chame `GET /auth/csrf` e reenvie o header. |
| CORS bloqueado no navegador | Origem diferente da configurada. | Use exatamente a origem de `FRONTEND_URL`; não misture `localhost` com `127.0.0.1`. |
| Dados de demonstração ausentes | Perfil `dev` não ativado. | Descomente `spring.profiles.active=dev` no `.env`. |

A lista completa de erros de configuração está em
[docs/CONFIGURACAO.md](docs/CONFIGURACAO.md).

## Documentação complementar

| Documento | Conteúdo |
| --- | --- |
| [docs/CONFIGURACAO.md](docs/CONFIGURACAO.md) | Variáveis, precedência, perfis e erros comuns. |
| [docs/MIGRACOES.md](docs/MIGRACOES.md) | Flyway, bancos existentes e cuidados por versão. |
| [docs/REGRAS_NEGOCIO_ESTOQUE.md](docs/REGRAS_NEGOCIO_ESTOQUE.md) | Regras de produtos, movimentações, drinks e dashboard. |
| [docs/OPENAPI.md](docs/OPENAPI.md) | Geração e atualização do contrato OpenAPI. |
| [docs/openapi.yaml](docs/openapi.yaml) | Contrato OpenAPI versionado. |

## Contribuição

1. Crie uma branch a partir de `main` com prefixo `feat/`, `fix/`, `docs/` ou
   `refactor/`.
2. Mantenha os commits no padrão Conventional Commits (`feat:`, `fix:`,
   `docs:`).
3. Execute a suíte de testes antes de abrir o pull request.
4. Ao alterar controllers ou DTOs, atualize `docs/openapi.yaml`.
5. Descreva no pull request o comportamento alterado e como validá-lo.

## Licença

Este repositório não possui um arquivo `LICENSE`. O uso é restrito ao contexto
do projeto Adega Meraki; consulte os mantenedores antes de reutilizar o código.
