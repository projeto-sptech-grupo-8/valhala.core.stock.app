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

## Endpoints disponíveis

### Login

`POST /auth/login` — público.

```json
{
  "email": "gerente@meraki.com",
  "password": "senha-do-gerente"
}
```

Em caso de sucesso, os tokens são enviados exclusivamente nos cookies
`HttpOnly` `accessToken` e `refreshToken`. O corpo contém apenas a mensagem e o
nome do usuário. O frontend deve enviar as requisições com credenciais, por
exemplo usando `credentials: "include"` no `fetch`.

### Renovar autenticação

`POST /auth/refresh` — público, mas exige o cookie `refreshToken`. O endpoint
valida e troca o par de tokens, devolvendo os novos valores nos cookies.

### Criar usuário

`POST /usuario` — exige o cookie `accessToken` de um usuário com perfil
`Gerente`.

Body:

```json
{
  "name": "Atendente Teste",
  "email": "atendente.teste@meraki.com",
  "phone": "11999999999",
  "password": "senha-segura",
  "profileName": "Atendente"
}
```

O backend localiza o perfil pelo nome, sem diferenciar letras maiúsculas e
minúsculas, e grava o respectivo UUID no usuário.

### Atualizar usuário

`PATCH /usuario/{id}` — exige o cookie `accessToken`. O usuário pode atualizar a
própria conta e a gerente pode atualizar qualquer conta.

Dados pessoais disponíveis para o proprietário:

```json
{
  "name": "Nome atualizado",
  "email": "novo.email@meraki.com",
  "phone": "11999999999",
  "password": "nova-senha-segura"
}
```

Somente a gerente pode enviar também:

```json
{
  "profileName": "Gerente",
  "active": true
}
```

Todos os campos são opcionais, mas a requisição deve informar pelo menos um.
Os nomes antigos `nome`, `telefone`, `senha`, `perfil` e `status` também são
aceitos para compatibilidade.

### Excluir usuário

`DELETE /usuario/{id}` — exige o cookie `accessToken`. O usuário pode excluir a
própria conta e a gerente pode excluir qualquer conta. A resposta de sucesso é
`204 No Content`; usuários com registros vinculados retornam `409 Conflict`.

## Testes

```powershell
.\mvnw.cmd test
```

Os testes utilizam um banco H2 em memória e não alteram o PostgreSQL local.
