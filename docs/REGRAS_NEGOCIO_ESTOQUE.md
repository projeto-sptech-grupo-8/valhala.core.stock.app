# Regras de Negócio — Módulo de Estoque

> Decisões do grupo para a sprint do módulo de Estoque.
>
> Escopo: Categorias, Produtos, Movimentações e Dashboard integrada. Financeiro,
> Caixa, Pedidos e NF-e não fazem parte desta entrega.

## 1. Produto

- Todo produto possui SKU gerado exclusivamente pelo backend. O formato ainda será definido.
- O SKU deve ser único dentro do estabelecimento.
- Categoria é obrigatória. O frontend carrega as opções cadastradas e envia `categoriaId`;
  o backend deve validar que a categoria existe e pertence ao estabelecimento atual.
- Um produto possui um tipo: `PADRAO` ou `DRINK`.
- Produto fracionado tem o saldo e as movimentações controlados em mililitros (`ML`). O volume
  da embalagem permite converter o recebimento de embalagens para essa unidade-base.
- Produto fracionado deve informar o volume da embalagem. Uma vodka de 1 L, por exemplo, mantém
  `1.000 ML` em estoque: uma venda de garrafa baixa `1.000 ML`, uma dose baixa a quantidade em
  ML e um drink baixa os ML definidos em sua receita. Não existe produto separado para a dose.
- Depois que o produto possui estoque ou movimentações, não se pode alterar `fracionado` nem o
  volume da embalagem, evitando reinterpretar saldos e histórico já registrados.
- Preço de venda deve ser maior que zero. Custo pode ser zero, mas preço e custo não podem
  ser negativos.
- A margem é calculada pelo sistema, com a fórmula:

  ```text
  margem (%) = (preço de venda - custo) / preço de venda × 100
  ```

  Exemplo: custo de R$ 90,00 e preço de venda de R$ 100,00 resultam em margem de 10%.
- Estoque mínimo é opcional. Nesta sprint ele pode ser informado manualmente; a classificação
  ABC ficará apenas preparada para uma implementação futura.
- O saldo não pode ser alterado diretamente na edição do produto. Saldo inicial, entradas,
  saídas e correções devem sempre gerar movimentação no histórico.

## 2. Categorias

- Categorias pertencem ao estabelecimento autenticado.
- Produtos só podem referenciar categorias do mesmo estabelecimento.
- Uma categoria com produtos vinculados não pode ser excluída.

## 3. Movimentações de estoque

Tipos mínimos previstos:

```text
ENTRADA_INICIAL
ENTRADA
SAIDA
AJUSTE_POSITIVO
AJUSTE_NEGATIVO
PERDA
SAIDA_DRINK
```

- Nenhuma movimentação pode deixar o saldo do produto negativo.
- A quantidade movimentada deve sempre usar a unidade-base do estoque: `ML` para produtos
  fracionados e a unidade de medida do produto para os demais itens.
- A criação inicial de um produto com saldo deve gerar `ENTRADA_INICIAL`.
- Toda movimentação deve registrar produto, tipo, quantidade, responsável, data/hora e
  observação ou motivo quando aplicável.
- Entradas podem registrar número de nota fiscal e lote; ambos são opcionais.
- Lote pertence à entrada/movimentação, e não ao cadastro fixo de Produto.
- Correções devem ser feitas por ajuste ou estorno, preservando o histórico.

### Permissões

- `VISUALIZAR_ESTOQUE`: consultar produtos, saldo e movimentações.
- `GERENCIAR_ESTOQUE`: gerir categorias e produtos.
- `MOVIMENTAR_ESTOQUE`: registrar entradas, saídas, ajustes, perdas e baixa de drinks.

## 4. Padrão Strategy

O módulo de movimentações utilizará o padrão Strategy. `MovimentacaoService` seleciona uma
estratégia conforme o tipo de operação:

```text
EstrategiaEntrada
EstrategiaSaida
EstrategiaAjustePositivo
EstrategiaAjusteNegativo
EstrategiaPerda
EstrategiaSaidaDrink
```

Cada estratégia aplica suas validações e altera o saldo de maneira auditável. Novos tipos de
movimentação devem ser adicionados como novas estratégias, sem concentrar regras em uma cadeia
de condicionais no serviço principal.

## 5. Drinks e composição

- Drink é um Produto do tipo `DRINK` e não mantém saldo próprio.
- Um drink possui uma receita formada por produtos do tipo `PADRAO`.
- Cada item da receita registra produto, quantidade e unidade de consumo: `ML` para ingrediente
  fracionado e `UN` para ingrediente não fracionado. A estrutura atual da composição será
  ampliada na implementação para aceitar as duas unidades.
- Um drink precisa ter pelo menos um ingrediente; ingredientes não podem se repetir e a
  quantidade deve ser maior que zero.
- Um drink não pode ser ingrediente de outro drink nesta sprint.
- Ingredientes devem estar ativos.

Exemplo de receita:

```text
Vodka Energy
- Vodka Absolut: 50 ML
- Red Bull: 1 UN
```

Ao registrar uma saída de drink, o backend deve:

1. Carregar e validar todos os ingredientes da receita.
2. Confirmar saldo suficiente de todos eles antes de qualquer alteração.
3. Criar uma saída para cada ingrediente dentro de uma única transação.
4. Reverter integralmente a operação caso algum ingrediente não tenha saldo.
5. Registrar a origem como `SAIDA_DRINK` e manter uma cópia da receita aplicada para auditoria.

Exemplo: uma entrada de uma garrafa de vodka de 750 ml adiciona `750 ML` ao estoque. Uma venda
do drink que use 50 ml reduz esse saldo para `700 ML`. Caixa e Financeiro poderão acionar essa
mesma operação no futuro, mas não fazem parte do escopo atual.

## 6. Giro, cobertura e classificação ABC

- Giro mensal é a quantidade de saídas dos últimos 30 dias.
- Venda média diária = giro mensal / 30.
- Cobertura em dias = saldo atual / venda média diária.
- Se não houver saídas nos últimos 30 dias, o giro é zero e a cobertura é apresentada como
  não calculável.
- Classificação ABC não será calculada nesta sprint. O modelo pode manter um campo opcional
  `classificacaoAbc` para evolução futura, sem usá-lo para calcular estoque mínimo agora.

## 7. Status e filtros

O status é calculado e exibido pelo frontend, não armazenado pelo backend:

| Condição | Status |
| --- | --- |
| Saldo igual a zero | `ZERADO` |
| Estoque mínimo informado e saldo menor que o mínimo | `CRITICO` |
| Estoque mínimo informado e saldo menor que mínimo × 1,5 | `BAIXO` |
| Demais situações | `ESTAVEL` |

- Produto sem estoque mínimo é `ESTAVEL`, exceto quando estiver zerado.
- A API retorna `quantidadeEstoque` e `estoqueMinimo`; o frontend calcula o status de exibição.
- Filtros de produto devem ser aplicados pelo backend, incluindo nome, SKU, categoria, tipo e
  ativo.

## 8. Contrato de listagens da API

Categorias, produtos e movimentações são filtrados, ordenados e paginados pelo banco de dados.
Assim, o frontend pode combinar os parâmetros conforme cada tela, sem carregar todo o estoque em
memória.

| Parâmetro | Regra global |
| --- | --- |
| `pagina` | Inicia em `0`. Padrão: `0`. |
| `tamanho` | Itens por página. Padrão: `20`; máximo: `100`. |
| `ordenarPor` | Deve ser um dos campos documentados no Swagger para aquele endpoint. |
| `direcao` | `ASC` ou `DESC`; cada endpoint possui um padrão documentado. |

A resposta segue o formato `itens`, `pagina`, `tamanho`, `totalItens`, `totalPaginas`,
`primeira` e `ultima`. Filtros opcionais omitidos não restringem a consulta. Em categorias e
produtos, por exemplo, omitir `ativo` retorna ativos e inativos.

Filtros disponíveis:

- `GET /categorias`: `busca` e `ativo`; ordenação por `nome`, `criadoEm` ou `atualizadoEm`.
- `GET /produtos`: `busca`, `categoriaId`, `tipo` e `ativo`; ordenação por `nome`, `sku`,
  `precoVenda`, `precoCusto`, `criadoEm` ou `atualizadoEm`.
- `GET /movimentacoes-estoque`: `produtoId`, `tipo`, `usuarioId`, `dataInicial` e `dataFinal`;
  a data final é inclusiva e a ordenação aceita `ocorridoEm`, `tipo`, `quantidade` ou
  `saldoPosterior`.

## 9. Diretrizes para a integração do frontend

- O seletor de categoria deve consumir as categorias cadastradas na API.
- O SKU deve ser apresentado como gerado pelo backend, sem edição manual no cadastro.
- Estoque mínimo deve ser opcional na tela.
- Lote e nota fiscal devem estar na tela de entrada/movimentação, não no formulário de produto.
- O frontend não altera saldo diretamente: usa os endpoints de movimentação.
- Dashboard e telas de produto/movimentação devem consumir dados reais da API, sem mocks.
- Após login ou refresh, o frontend deve chamar `GET /auth/csrf` usando `credentials: 'include'`.
  Em toda requisição autenticada que altere dados (`POST`, `PUT`, `PATCH` ou `DELETE`), deve enviar
  o `token` retornado no header `X-XSRF-TOKEN`. Os tokens de acesso e renovação permanecem somente
  em cookies `HttpOnly`; o token CSRF é validado pelo backend e hoje fica em memória. A interface
  foi preparada para uma implementação futura em Redis.
