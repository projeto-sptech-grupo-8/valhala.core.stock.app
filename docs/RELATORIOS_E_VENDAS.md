# Relatórios gerenciais e vendas

Este documento reúne a análise dos dados que a tela **Relatórios** do frontend consome, o que foi feito
no backend para que esses dados existam em produção e o que o frontend precisa mudar para usá-los.

**Fora do escopo:** inventário (contagem e ajuste de inventário) e sessão de caixa (abertura,
fechamento, sangria e reforço).

## 1. O que a tela consome

O front chama `GET /relatorios/{nome}?dias=7|30|90` e só adapta as respostas (`src/pages/dash/relatorios.js`).

| Aba | Endpoint | Campos usados |
|---|---|---|
| Curva ABC | `/relatorios/abc` | `produtoId, nome, categoria, quantidade, receita, posicao, percentual, acumulado, classe` |
| Margem | `/relatorios/margem-categorias` | `categoria, receita, cmv, lucro, margem, participacao` |
| Ruptura | `/relatorios/ruptura` | `produtoId, dias, ocorrencias, unidadesPerdidas, vendaPerdida, percentualDias` |
| Horários | `/relatorios/horarios` | `diaSemana` (0 = segunda), `hora, vendas, receita` |
| Canais | `/relatorios/canais` | `canal, vendas, faturamento, ticketMedio, participacao, itensPorVenda` |
| Perdas | `/relatorios/perdas` | `granularidade` (DIA/SEMANA/MES), `itens[periodo, motivo, valor]` |
| Todas | — | `totais.vendas`, `totais.faturamento` |

O formato das respostas **não mudou**. Mudaram a origem dos dados e os cálculos.

## 2. Problemas encontrados e correções

| Problema | Correção |
|---|---|
| As vendas não chegavam ao banco: o caixa só enviava as saídas de estoque e o pedido ficava no navegador. ABC, Margem, Horários, Canais e os totais ficavam vazios em produção. | Novo módulo `/vendas`: grava pedido, itens e pagamentos e baixa o estoque na mesma transação. |
| As perdas eram classificadas pelo texto livre do motivo. "Consumo interno" nunca aparecia, e ajustes negativos (inclusive estornos automáticos) contavam como perda. | `motivoPerda` com valores fixos, obrigatório em `PERDA`. O relatório agrupa por ele e considera só `PERDA`. |
| O CMV usava o custo atual do produto, então a margem do passado mudava junto com o custo. | `item_pedido.custo_unitario` guarda o custo no momento da venda. |
| ABC e Margem somavam os itens sem o desconto do pedido; os totais usavam o total com desconto. | A receita de cada item recebe sua parte do desconto do pedido, proporcional ao subtotal. |
| A venda perdida na Ruptura dividia o vendido por todos os dias do período, inclusive os dias zerados. | Média calculada só sobre os dias com estoque. |
| A Ruptura percorria todo o histórico de movimentações. | A consulta parte do saldo vigente no início do período. |
| Os relatórios exigiam `VISUALIZAR_ESTOQUE`, mas mostram faturamento e lucro. | Nova permissão `VISUALIZAR_RELATORIOS`. |
| Os horários eram gravados no fuso da JVM, enquanto o relatório recorta no fuso de São Paulo. | Movimentações e vendas gravadas em `America/Sao_Paulo`. |
| O módulo não tinha testes, e o SQL do PostgreSQL não roda no H2. | Testes contra PostgreSQL embarcado, com o schema e as migrações reais. |

## 3. API de vendas

### `POST /vendas` (permissão `REGISTRAR_VENDA`)

```json
{
  "canal": "MESA",
  "observacao": "Mesa 4",
  "desconto": 10.00,
  "itens": [
    { "produtoId": "…", "quantidade": 2 },
    { "produtoId": "<drink>", "quantidade": 1 }
  ],
  "pagamentos": [
    { "forma": "PIX", "valor": 60.00 },
    { "forma": "DINHEIRO", "valor": 50.00 }
  ]
}
```

- `canal`: `BALCAO`, `MESA` ou `DELIVERY`. `forma`: `PIX`, `CARTAO_CREDITO`, `CARTAO_DEBITO` ou `DINHEIRO`.
- O preço vem do cadastro do produto; o cliente não envia preço.
- `quantidade` está em unidades vendidas: garrafas, latas ou drinks. Para produto fracionado, o back
  converte para ML. Drink baixa os ingredientes da receita.
- A soma dos pagamentos deve cobrir o total. A sobra é o `troco`, que só pode sair de pagamento em
  dinheiro. O dinheiro é gravado já sem o troco.
- Se faltar saldo em qualquer item, a resposta é 400 com o nome do produto na mensagem, e nada é gravado.
- A resposta (201) traz `id`, `numero` (sequencial por estabelecimento), totais, `troco`, itens e pagamentos.

### `POST /vendas/{id}/cancelamento` (permissão `CANCELAR_VENDA`)

Recebe `{ "motivo": "Pedido lançado errado" }`. Marca a venda como `CANCELADO`, estorna os pagamentos e
devolve os itens aos lotes de onde saíram. Venda cancelada não entra nos relatórios.

### `GET /vendas/{id}` e `GET /vendas` (permissão `REGISTRAR_VENDA` ou `VISUALIZAR_RELATORIOS`)

A listagem é paginada, com os filtros `dataInicial`, `dataFinal`, `status` e `canal`.

## 4. Mudanças necessárias no frontend

1. **Finalizar venda** (`store.jsx`, `finalizeSale`): chamar `POST /vendas` uma vez, em vez de enviar uma
   movimentação por item e uma saída de drink por drink. Usar o `numero` e o `troco` da resposta.
   - O estorno local em caso de falha deixa de ser necessário: se faltar saldo, o back não grava nada.
2. **Perdas** (`api.js`, `fromMovimento`): enviar `tipo: "PERDA"` com `motivoPerda` para "Perda / quebra"
   (`QUEBRA`), "Vencimento" (`VENCIMENTO`) e "Consumo interno" (`CONSUMO_INTERNO`).
   - Hoje "Consumo interno" sai como `SAIDA` e não aparece no relatório.
   - Enquanto o front não enviar `motivoPerda`, o back deduz o motivo pelo texto, mas só em movimentações `PERDA`.
3. **Horários** (`relatorios.js`): o mapa de calor mostra apenas das 10h às 23h. O back devolve todas as
   horas com venda; vendas de madrugada hoje são descartadas na tela.
4. **Permissões**: a tela de relatórios deve depender de `VISUALIZAR_RELATORIOS`. Usuários sem ela recebem 403.
