# Adega Meraki — Core Stock API

API REST em Java 21 e Spring Boot para autenticação JWT, usuários e controle de
estoque da Adega Meraki.

## Requisitos

- Java 21
- PostgreSQL
- PowerShell no Windows

## Preparar o banco de dados

1. Crie um banco chamado `valhalla` no PostgreSQL.
2. Conecte-se ao banco `valhalla` no DBeaver ou em outro cliente SQL.
3. Execute `src/main/resources/db/script.sql` completo.
4. Opcionalmente, execute `src/main/resources/db/seed-inicial.sql` para inserir
   os dados de demonstração. Ele cria `admin@valhalla.local` com a senha
   `Senha@123`; altere esses dados antes de qualquer uso real.
5. Inicie a aplicação uma vez para o Flyway registrar a versão do banco e aplicar
   as migrações pendentes.

Para bancos já existentes, leia [docs/MIGRACOES.md](docs/MIGRACOES.md), faça um
backup e inicie a versão atual da API. A migration V3 cria a unicidade de nome de
perfil sem diferenciar maiúsculas/minúsculas; resolva eventuais perfis duplicados
antes de aplicá-la.

## Configurar e iniciar a aplicação

Na raiz deste projeto, configure as variáveis no mesmo terminal PowerShell que
será usado para iniciar a API:

```powershell
$env:DB_PASSWORD = 'senha-do-postgres'
$env:JWT_SECRET = 'secret-base64-com-pelo-menos-32-bytes'
$env:FRONTEND_URL = 'http://localhost:5173'
```

Em produção, habilite cookies seguros e, se necessário, ajuste a duração do
refresh token (o padrão é sete dias):

```powershell
$env:COOKIE_SECURE = 'true'
$env:JWT_REFRESH_EXPIRATION = 'P7D'
```

Para gerar um novo secret compatível com versões antigas do PowerShell:

```powershell
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
```

Se o banco não estiver em `localhost:5432/valhalla`, configure também:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/valhalla'
$env:DB_USERNAME = 'postgres'
```

Depois, inicie a aplicação:

```powershell
.\mvnw.cmd spring-boot:run
```

## Consumo pelo frontend

A URL da API deve ser configurada no frontend, por exemplo:

```env
VITE_API_URL=http://localhost:8080
```

A autenticação usa exclusivamente os cookies `HttpOnly` `accessToken` e
`refreshToken`. O JavaScript não deve ler, salvar no `localStorage` ou enviar
esses tokens no header `Authorization`. Todas as requisições devem usar
`credentials: "include"`:

```javascript
const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

export async function apiRequest(path, options = {}) {
  const headers = new Headers(options.headers);

  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers,
    credentials: "include"
  });

  if (response.status === 204) {
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

Durante o desenvolvimento, não misture `localhost` e `127.0.0.1`: cookies
criados para um host não são enviados ao outro. A origem do frontend também
deve ser exatamente a configurada em `FRONTEND_URL`.

### Fluxo de autenticação

| Método | Endpoint | Autenticação | Sucesso |
| --- | --- | --- | --- |
| `POST` | `/auth/login` | Público | `200 OK` e criação dos cookies |
| `POST` | `/auth/refresh` | Cookie `refreshToken` | `200 OK` e rotação dos cookies |
| `POST` | `/auth/logout` | Cookies opcionais | `204 No Content` e remoção dos cookies |

#### Login

```javascript
await apiRequest("/auth/login", {
  method: "POST",
  body: JSON.stringify({
    email: "gerente@meraki.com",
    password: "senha-do-gerente"
  })
});

const currentUser = await apiRequest("/usuario/me");
```

O login devolve somente uma mensagem e o nome do usuário. Os dados completos,
inclusive ID, perfil e status, devem ser carregados por `GET /usuario/me`:

```json
{
  "mensagem": "Autenticação realizada com sucesso",
  "usuario": "Nome do usuário"
}
```

#### Renovação e expiração da sessão

Ao receber `401 Unauthorized` em uma rota protegida, o frontend pode chamar
`POST /auth/refresh` uma única vez e repetir a requisição original se a
renovação funcionar. Se o refresh também retornar `401`, deve limpar seu estado
de usuário e redirecionar para o login. Esse mecanismo não deve tentar refresh
para erros das próprias rotas `/auth`, evitando repetição infinita.

Cada refresh token pode ser usado somente uma vez, pois o endpoint troca os
dois cookies. Um novo login também invalida a sessão anterior do mesmo usuário.

#### Logout

```javascript
await apiRequest("/auth/logout", { method: "POST" });
// Limpar o estado global do usuário e navegar para a tela de login.
```

O logout é idempotente: também retorna `204 No Content` quando os cookies estão
ausentes ou inválidos.

## Endpoints de usuário

Estas são todas as rotas de usuário atualmente implementadas:

| Método | Endpoint | O que faz | Quem pode usar | Identificação | Sucesso |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/usuario` | Cadastra um novo usuário | `USUARIOS_CRIAR` | Não se aplica | `201 Created` |
| `GET` | `/usuario` | Lista todos os usuários | `USUARIOS_VISUALIZAR` | Não se aplica | `200 OK` |
| `GET` | `/usuario/me` | Retorna o usuário autenticado | Usuário autenticado | Claim `userId` do token | `200 OK` |
| `GET` | `/usuario/{id}` | Busca um usuário pelo ID | Próprio usuário ou `USUARIOS_VISUALIZAR` | UUID no path | `200 OK` |
| `PATCH` | `/usuario/me` | Atualiza o usuário autenticado | Usuário autenticado | Claim `userId` do token | `200 OK` |
| `DELETE` | `/usuario/me` | Exclui o usuário autenticado | Usuário autenticado | Claim `userId` do token | `204 No Content` |
| `PATCH` | `/usuario/{id}` | Atualiza um usuário pelo ID | Próprio usuário ou `USUARIOS_EDITAR` | UUID no path | `200 OK` |
| `DELETE` | `/usuario/{id}` | Exclui um usuário pelo ID | Próprio usuário ou `USUARIOS_EXCLUIR` | UUID no path | `204 No Content` |

### Resposta de usuário

`POST`, `GET` e `PATCH` retornam o usuário neste formato:

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "nome": "Usuário Gerente",
  "email": "gerente@meraki.com",
  "telefone": "11955554444",
  "idEstabelecimento": "33333333-3333-4333-8333-333333333333",
  "idPerfil": 1,
  "nomePerfil": "Gerente",
  "ativo": true,
  "atualizadoEm": "2026-09-04T20:33:32.868968",
  "criadoEm": "2026-08-25T19:49:17.005005",
  "permissoes": ["USUARIOS_CRIAR", "USUARIOS_VISUALIZAR"]
}
```

### Criar usuário

`POST /usuario` exige a permissão `USUARIOS_CRIAR`.

```javascript
const createdUser = await apiRequest("/usuario", {
  method: "POST",
  body: JSON.stringify({
    nome: "Atendente Teste",
    email: "atendente.teste@meraki.com",
    telefone: "11999999999",
    senha: "senha-segura",
    nomePerfil: "Atendente"
  })
});
```

Regras do body:

- `nome`: obrigatório, máximo de 100 caracteres;
- `email`: obrigatório, formato válido e único;
- `telefone`: opcional, máximo de 13 caracteres;
- `senha`: obrigatório, entre 8 e 72 caracteres;
- `nomePerfil`: obrigatório e deve corresponder a um perfil existente, sem
  diferenciar maiúsculas de minúsculas.

A resposta é `201 Created`, contém o usuário criado e expõe
`Location: /usuario/{id}`. O ID também está disponível em `createdUser.id`.

### Listar usuários

```javascript
const users = await apiRequest("/usuario");
```

`GET /usuario` exige `USUARIOS_VISUALIZAR` e retorna `200 OK` com um array contendo
todos os usuários, ordenados por `nome` em ordem crescente. A senha e o hash da
senha nunca são retornados.

```json
[
  {
    "id": "11111111-1111-4111-8111-111111111111",
    "nome": "Atendente Teste",
    "email": "atendente.teste@meraki.com",
    "telefone": "11999999999",
    "idEstabelecimento": "33333333-3333-4333-8333-333333333333",
    "idPerfil": 2,
    "nomePerfil": "Atendente",
    "ativo": true,
    "atualizadoEm": "2026-09-04T20:33:32.868968",
    "criadoEm": "2026-09-04T20:30:00.000000",
    "permissoes": ["VISUALIZAR_ESTOQUE"]
  }
]
```

O frontend deve guardar o `id` de cada item apenas como identificador da linha
selecionada na tela administrativa. Esse ID será usado nas rotas administrativas
de consulta, atualização e exclusão.

### Obter um usuário por ID

```javascript
const selectedUser = await apiRequest(`/usuario/${userId}`);
```

`GET /usuario/{id}` permite consultar o próprio usuário ou exige
`USUARIOS_VISUALIZAR`. Retorna `200 OK` com o mesmo formato de resposta de
usuário. Se o UUID não existir, retorna `404 Not Found`
com a mensagem `Usuário não encontrado`.

O frontend pode usar essa rota ao abrir uma tela de detalhes ou antes de editar
um item obtido em `GET /usuario`. Para consultar a própria sessão, deve continuar
usando `GET /usuario/me`, sem ID.

### Obter o usuário autenticado

```javascript
const currentUser = await apiRequest("/usuario/me");
```

`GET /usuario/me` não recebe ID nem body. O backend extrai o `userId` do token e
retorna `200 OK`. Essa rota deve ser utilizada após o login e ao recarregar a
aplicação para preencher o estado global do usuário.

### Atualizar a própria conta

```javascript
const updatedUser = await apiRequest("/usuario/me", {
  method: "PATCH",
  body: JSON.stringify({
    nome: "Nome atualizado",
    email: "novo.email@meraki.com",
    telefone: "11999999999"
  })
});
```

`PATCH /usuario/me` não recebe ID. Usuários autenticados podem alterar `nome`,
`email`, `telefone` e `senha`. Somente quem possui `USUARIOS_EDITAR` pode enviar
também `nomePerfil` ou `ativo`.

Todos os campos são opcionais, mas pelo menos um deve ser enviado. Uma string
vazia em `telefone` remove o telefone. Durante a transição, os aliases em
inglês (`name`, `phone`, `password`, `profileName` e `active`) ainda são aceitos
nos corpos de requisição; as respostas usam somente os nomes em português.

Ao alterar a senha, o perfil ou o status, as sessões desse usuário são
revogadas. Quando a alteração for feita na própria conta, o frontend deve
considerar o usuário desconectado após o `200 OK` e encaminhá-lo para um novo
login.

### Excluir a própria conta

```javascript
await apiRequest("/usuario/me", { method: "DELETE" });
// Limpar o estado do usuário e navegar para a tela de login.
```

`DELETE /usuario/me` não recebe ID nem body. A exclusão retorna
`204 No Content` e revoga a sessão. Se o usuário possuir registros que impedem
a exclusão, a API retorna `409 Conflict`.

### Atualizar outro usuário

```javascript
const updatedUser = await apiRequest(`/usuario/${selectedUser.id}`, {
  method: "PATCH",
  body: JSON.stringify({
    profileName: "Gerente",
    active: true
  })
});
```

`PATCH /usuario/{id}` permite atualizar o próprio usuário ou exige
`USUARIOS_EDITAR` para outro usuário do mesmo estabelecimento. O `{id}` é o UUID
do usuário selecionado em `GET /usuario`, não o ID extraído do token.

### Excluir outro usuário

```javascript
await apiRequest(`/usuario/${selectedUser.id}`, { method: "DELETE" });
```

`DELETE /usuario/{id}` permite excluir a própria conta ou exige
`USUARIOS_EXCLUIR` para outro usuário do mesmo estabelecimento. Não recebe body
e retorna `204 No Content`. Usuário inexistente retorna `404 Not Found`; usuário com
registros vinculados retorna `409 Conflict`.

### Tratamento de erros no frontend

Os erros seguem o formato:

```json
{
  "timestamp": "2026-09-04T20:05:14.3074932",
  "status": 400,
  "message": "Dados de entrada inválidos",
  "fieldErrors": {
    "email": "E-mail inválido"
  }
}
```

Tratamento recomendado:

| Status | Significado | Ação do frontend |
| --- | --- | --- |
| `400` | Body inválido ou atualização vazia | Exibir `fieldErrors` nos campos ou `message` |
| `401` | Credenciais ou sessão inválida | Tentar refresh uma vez; se falhar, abrir login |
| `403` | Usuário autenticado sem permissão | Informar acesso negado e ocultar ação indevida |
| `404` | Usuário ou perfil não encontrado | Atualizar a tela e informar que o recurso não existe |
| `409` | E-mail duplicado ou exclusão bloqueada | Exibir a mensagem de conflito sem repetir automaticamente |

As sessões ativas são registradas em `sessao_usuario`. Login, renovação, logout,
alteração de senha, perfil, status ou permissões revogam os tokens anteriores.

## Autorizações

As permissões são isoladas por estabelecimento. O perfil `Gerente` possui todas
as permissões do sistema para o estabelecimento ao qual está vinculado. Os demais
perfis recebem as funcionalidades atribuídas ao perfil, complementadas por
sobrescritas individuais `GRANT` ou `REVOKE`.

| Código | Descrição |
| --- | --- |
| `USUARIOS_CRIAR` | Cadastrar usuários |
| `USUARIOS_VISUALIZAR` | Consultar usuários |
| `USUARIOS_EDITAR` | Alterar usuários |
| `USUARIOS_EXCLUIR` | Excluir usuários |
| `PERFIS_GERENCIAR` | Criar, editar e excluir perfis |
| `PERMISSOES_GERENCIAR` | Configurar permissões de perfil e de usuário |

O estabelecimento precisa manter ao menos um usuário ativo capaz de criar
usuários. A API rejeita operações que removeriam essa última capacidade.

### Endpoints de perfis e permissões

As rotas abaixo exigem uma sessão que possua a permissão indicada. Um gerente do
estabelecimento sempre a possui.

| Método | Endpoint | Permissão necessária | Descrição |
| --- | --- | --- | --- |
| `GET` | `/autorizacoes/funcionalidades` | `PERMISSOES_GERENCIAR` | Lista as funcionalidades disponíveis. |
| `GET` | `/autorizacoes/perfis` | `PERFIS_GERENCIAR` | Lista os perfis do estabelecimento. |
| `GET` | `/autorizacoes/perfis/{profileId}` | `PERFIS_GERENCIAR` | Consulta um perfil. |
| `POST` | `/autorizacoes/perfis` | `PERFIS_GERENCIAR` | Cria um perfil. |
| `PATCH` | `/autorizacoes/perfis/{profileId}` | `PERFIS_GERENCIAR` | Atualiza nome ou descrição. |
| `DELETE` | `/autorizacoes/perfis/{profileId}` | `PERFIS_GERENCIAR` | Exclui um perfil sem usuários vinculados. |
| `PUT` | `/autorizacoes/perfis/{profileId}/funcionalidades/codigos` | `PERFIS_GERENCIAR` | Substitui as funcionalidades de um perfil. |
| `PUT` | `/autorizacoes/usuarios/{userId}/sobrescritas-permissao` | `PERMISSOES_GERENCIAR` | Substitui permissões individuais. |
| `GET` | `/autorizacoes/usuarios/{userId}/permissoes` | `PERMISSOES_GERENCIAR` | Consulta permissões efetivas e suas origens. |

#### Criar ou atualizar perfil

```javascript
await apiRequest("/autorizacoes/perfis", {
  method: "POST",
  body: JSON.stringify({
    nome: "Caixa",
    descricao: "Atendimento no caixa",
    codigosFuncionalidades: ["VISUALIZAR_ESTOQUE"]
  })
});

await apiRequest(`/autorizacoes/perfis/${profileId}/funcionalidades/codigos`, {
  method: "PUT",
  body: JSON.stringify(["VISUALIZAR_ESTOQUE", "MOVIMENTAR_ESTOQUE"])
});
```

O nome do perfil é obrigatório, possui até 100 caracteres e é único por
estabelecimento sem diferenciar maiúsculas/minúsculas. `PATCH` exige ao menos
um dos campos `nome` ou `descricao`.

#### Sobrescritas de permissão por usuário

```javascript
await apiRequest(`/autorizacoes/usuarios/${userId}/sobrescritas-permissao`, {
  method: "PUT",
  body: JSON.stringify({
    sobrescritas: [
      { codigoFuncionalidade: "MOVIMENTAR_ESTOQUE", efeito: "GRANT" },
      { codigoFuncionalidade: "USUARIOS_EXCLUIR", efeito: "REVOKE" }
    ]
  })
});
```

Cada código pode aparecer uma única vez no array. A chamada substitui todas as
sobrescritas existentes do usuário e revoga sua sessão atual.

## Testes

```powershell
.\mvnw.cmd test
```

Os testes utilizam H2 em memória e não alteram o PostgreSQL local. Para executar
uma compilação limpa, use `.\mvnw.cmd clean test`.
