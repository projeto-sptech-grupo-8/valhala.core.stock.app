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
3. Execute o arquivo `script.sql` completo.
4. Confira as mensagens da execução: na primeira instalação, o script informa o
   e-mail e a senha temporária do gerente inicial.

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
  "message": "Autenticação realizada com sucesso",
  "user": "Nome do usuário"
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
| `POST` | `/usuario` | Cadastra um novo usuário | Gerente | Não se aplica | `201 Created` |
| `GET` | `/usuario` | Lista todos os usuários | Gerente | Não se aplica | `200 OK` |
| `GET` | `/usuario/me` | Retorna o usuário autenticado | Usuário autenticado | Claim `userId` do token | `200 OK` |
| `GET` | `/usuario/{id}` | Busca um usuário pelo ID | Gerente | UUID no path | `200 OK` |
| `PATCH` | `/usuario/me` | Atualiza o usuário autenticado | Usuário autenticado | Claim `userId` do token | `200 OK` |
| `DELETE` | `/usuario/me` | Exclui o usuário autenticado | Usuário autenticado | Claim `userId` do token | `204 No Content` |
| `PATCH` | `/usuario/{id}` | Atualiza um usuário pelo ID | Gerente | UUID no path | `200 OK` |
| `DELETE` | `/usuario/{id}` | Exclui um usuário pelo ID | Gerente | UUID no path | `204 No Content` |

### Resposta de usuário

`POST`, `GET` e `PATCH` retornam o usuário neste formato:

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "name": "Usuário Gerente",
  "email": "gerente@meraki.com",
  "phone": "11955554444",
  "profileId": "22222222-2222-4222-8222-222222222222",
  "profileName": "Gerente",
  "active": true,
  "updatedAt": "2026-09-04T20:33:32.868968",
  "createdAt": "2026-08-25T19:49:17.005005"
}
```

### Criar usuário

`POST /usuario` exige uma sessão com perfil `Gerente`.

```javascript
const createdUser = await apiRequest("/usuario", {
  method: "POST",
  body: JSON.stringify({
    name: "Atendente Teste",
    email: "atendente.teste@meraki.com",
    phone: "11999999999",
    password: "senha-segura",
    profileName: "Atendente"
  })
});
```

Regras do body:

- `name`: obrigatório, máximo de 100 caracteres;
- `email`: obrigatório, formato válido e único;
- `phone`: opcional, máximo de 13 caracteres;
- `password`: obrigatório, entre 8 e 72 caracteres;
- `profileName`: obrigatório e deve corresponder a um perfil existente, sem
  diferenciar maiúsculas de minúsculas.

A resposta é `201 Created`, contém o usuário criado e expõe
`Location: /usuario/{id}`. O ID também está disponível em `createdUser.id`.

### Listar usuários como gerente

```javascript
const users = await apiRequest("/usuario");
```

`GET /usuario` exige perfil `Gerente` e retorna `200 OK` com um array contendo
todos os usuários, ordenados por `name` em ordem crescente. A senha e o hash da
senha nunca são retornados.

```json
[
  {
    "id": "11111111-1111-4111-8111-111111111111",
    "name": "Atendente Teste",
    "email": "atendente.teste@meraki.com",
    "phone": "11999999999",
    "profileId": "22222222-2222-4222-8222-222222222222",
    "profileName": "Atendente",
    "active": true,
    "updatedAt": "2026-09-04T20:33:32.868968",
    "createdAt": "2026-09-04T20:30:00.000000"
  }
]
```

O frontend deve guardar o `id` de cada item apenas como identificador da linha
selecionada na tela administrativa. Esse ID será usado nas rotas administrativas
de consulta, atualização e exclusão.

### Obter um usuário por ID como gerente

```javascript
const selectedUser = await apiRequest(`/usuario/${userId}`);
```

`GET /usuario/{id}` exige perfil `Gerente` e retorna `200 OK` com o mesmo
formato de resposta de usuário. Se o UUID não existir, retorna `404 Not Found`
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
    name: "Nome atualizado",
    email: "novo.email@meraki.com",
    phone: "11999999999"
  })
});
```

`PATCH /usuario/me` não recebe ID. Usuários autenticados podem alterar `name`,
`email`, `phone` e `password`. Apenas o perfil `Gerente` pode enviar também
`profileName` ou `active`.

Todos os campos são opcionais, mas pelo menos um deve ser enviado. Uma string
vazia em `phone` remove o telefone. Os aliases `nome`, `telefone`, `senha`,
`perfil`, `perfilNome` e `status` também são aceitos, embora o frontend deva
preferir os nomes em inglês documentados acima.

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

### Atualizar outro usuário como gerente

```javascript
const updatedUser = await apiRequest(`/usuario/${selectedUser.id}`, {
  method: "PATCH",
  body: JSON.stringify({
    profileName: "Gerente",
    active: true
  })
});
```

`PATCH /usuario/{id}` exige perfil `Gerente`. O `{id}` é o UUID do usuário que o
gerente selecionou em `GET /usuario`, não o ID extraído do token do gerente.

### Excluir outro usuário como gerente

```javascript
await apiRequest(`/usuario/${selectedUser.id}`, { method: "DELETE" });
```

`DELETE /usuario/{id}` exige perfil `Gerente`, não recebe body e retorna
`204 No Content`. Usuário inexistente retorna `404 Not Found`; usuário com
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

Os tokens atuais são controlados em memória pelos JTIs, sem alterar as tabelas
do banco de dados. Como esse estado não é persistido, reiniciar a aplicação
encerra todas as sessões, e instâncias diferentes não compartilham sessões
entre si.

## Testes

```powershell
.\mvnw.cmd test
```

Os testes utilizam um banco H2 em memória e não alteram o PostgreSQL local.
