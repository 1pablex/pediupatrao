# API do Dashboard - Documentação dos endpoints

Base: `http://localhost:8080/api/v1/dashboard` · Versão `v1` · Somente leitura (`GET`)

Especificação formal: [openapi.yaml](openapi.yaml) (OpenAPI 3.1). Os exemplos abaixo são respostas reais da
aplicação com o banco de demonstração (`DEMO_GERAR_DADOS=true`), encurtadas onde indicado com `...`.

## Sumário

| Método | Endpoint | Para quê | RF |
|--------|----------|----------|----|
| GET | [`/resumo`](#get-resumo) | Indicadores gerais e comparativo com o período anterior | RF01 |
| GET | [`/vendas/serie`](#get-vendasserie) | Evolução das vendas por dia, semana ou mês | RF02 |
| GET | [`/vendas/por-categoria`](#get-vendaspor-categoria) | Faturamento por categoria de produto | RF02 |
| GET | [`/vendas/por-hora`](#get-vendaspor-hora) | Pedidos por hora do dia (horário de pico) | RF02 |
| GET | [`/vendas/por-dia-hora`](#get-vendaspor-dia-hora) | Mapa de calor: pedidos por dia da semana e hora | RF02 |
| GET | [`/pedidos/situacao`](#get-pedidossituacao) | Pedidos por status e tempo médio até a saída | RF03 |
| GET | [`/pedidos`](#get-pedidos) | Lista paginada dos pedidos do período | RF03 |
| GET | [`/produtos/ranking`](#get-produtosranking) | Produtos mais vendidos | RF04 |
| GET | [`/filtros`](#get-filtros) | Valores aceitos pelos filtros | RF05 |

---

## Autenticação e autorização

Perfis permitidos: **ADMIN** e **GERENTE**. Qualquer outro perfil recebe `403`.

| Forma | Quando usar |
|-------|-------------|
| Sessão (cookie `JSESSIONID`) | É assim que a página `/dashboard` consome a API, depois do login na tela. |
| HTTP Basic | Integração com outros sistemas: `curl -u gerente01:SENHA ...` |

A API nunca cria sessão. Requisições com o cabeçalho `X-Requested-With: XMLHttpRequest` recebem `401` sem o
desafio `WWW-Authenticate`, para o navegador não abrir a janela de login quando a sessão expira.

## Formato das respostas

Todas as respostas de sucesso (exceto `/filtros`) usam o mesmo envelope:

```json
{
  "filtros": { "dataInicio": "2026-10-12", "dataFim": "2026-11-10", "status": [], "tipoEntrega": null },
  "geradoEm": "2026-11-10T13:13:55.97-03:00",
  "dados": { }
}
```

| Campo | Descrição |
|-------|-----------|
| `filtros` | Filtros **efetivamente aplicados**, já com os valores padrão preenchidos. |
| `geradoEm` | Data/hora (com fuso) em que os dados foram calculados. |
| `dados` | Conteúdo específico de cada endpoint. |

Convenções: JSON em camelCase; datas em ISO 8601 (`yyyy-MM-dd`); valores em reais com 2 casas decimais;
percentuais de 0 a 100 com 2 casas decimais.

## Parâmetros comuns (filtros)

Aceitos por todos os endpoints, exceto `/filtros`.

| Parâmetro | Tipo | Padrão | Descrição |
|-----------|------|--------|-----------|
| `dataInicio` | data `yyyy-MM-dd` | `dataFim` - 29 dias | Primeiro dia do período (inclusive). |
| `dataFim` | data `yyyy-MM-dd` | hoje (ou `dataInicio` + 29 dias) | Último dia do período (inclusive). |
| `status` | lista de status | todos | Repetível (`status=FINALIZADO&status=CANCELADO`) ou separado por vírgula. Valores: `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO`. |
| `tipoEntrega` | texto | ambos | `ENTREGA` ou `RETIRADA`. |

Regras: o período considera a data de **entrada** do pedido; no máximo **366 dias**; `dataInicio` não pode ser
posterior a `dataFim`. Vendas são os pedidos não cancelados (detalhes em [requisitos.md](requisitos.md#4-regras-de-negócio-das-métricas)).

## Erros

Erros seguem o formato **ProblemDetail** (RFC 9457), `Content-Type: application/problem+json`.

| Status | Quando | Exemplo de `detail` |
|--------|--------|---------------------|
| `400` | Período invertido | `dataInicio deve ser anterior ou igual a dataFim.` |
| `400` | Período maior que 366 dias | `O período pode ter no máximo 366 dias (informado: 679).` |
| `400` | Data, status, tipo, agrupamento ou critério em formato inválido | `Valor inválido para o parâmetro 'status'.` |
| `400` | `pagina` < 0, `tamanho` fora de 1 a 100, `limite` fora de 1 a 50 | `tamanho deve estar entre 1 e 100.` |
| `401` | Sem autenticação ou credenciais inválidas | `Autenticação necessária.` |
| `403` | Perfil sem acesso (ATENDENTE) ou método diferente de GET | `Acesso negado: seu perfil não permite esta operação.` |
| `500` | Falha inesperada (ex.: banco indisponível) | Resposta padrão do Spring, sem detalhes internos. |

Erros de parâmetro trazem o campo `erros`, indicando qual parâmetro corrigir:

```json
{
  "type": "about:blank",
  "title": "Parâmetro inválido",
  "status": 400,
  "detail": "dataInicio deve ser anterior ou igual a dataFim.",
  "instance": "/api/v1/dashboard/resumo",
  "erros": { "dataInicio": "dataInicio deve ser anterior ou igual a dataFim." }
}
```

```json
{ "status": 401, "title": "Unauthorized", "detail": "Autenticação necessária." }
```

```json
{ "status": 403, "title": "Forbidden", "detail": "Acesso negado: seu perfil não permite esta operação." }
```

---

## GET /resumo

Indicadores gerais do período e comparativo com o período anterior de mesma duração (RF01).

Parâmetros: apenas os [comuns](#parâmetros-comuns-filtros).

```bash
curl -u gerente01:SENHA "http://localhost:8080/api/v1/dashboard/resumo"
```

Resposta `200`:

```json
{
  "filtros": { "dataInicio": "2026-10-12", "dataFim": "2026-11-10", "status": [], "tipoEntrega": null },
  "geradoEm": "2026-11-10T13:13:55.9786887-03:00",
  "dados": {
    "faturamento": 46769.77,
    "totalPedidos": 486,
    "pedidosValidos": 445,
    "pedidosCancelados": 41,
    "ticketMedio": 105.1,
    "taxaCancelamento": 8.44,
    "totalDescontos": 475.33,
    "itensVendidos": 1103,
    "comparativo": {
      "dataInicio": "2026-09-12",
      "dataFim": "2026-10-11",
      "faturamento": 45186.72,
      "pedidosValidos": 439,
      "ticketMedio": 102.93,
      "variacaoFaturamento": 3.5,
      "variacaoPedidos": 1.37,
      "variacaoTicketMedio": 2.11
    }
  }
}
```

| Campo | Descrição |
|-------|-----------|
| `faturamento` | Soma do valor total dos pedidos não cancelados. |
| `totalPedidos` / `pedidosValidos` / `pedidosCancelados` | Todos os pedidos, os não cancelados e os cancelados. |
| `ticketMedio` | `faturamento / pedidosValidos`. |
| `taxaCancelamento` | `% de pedidosCancelados sobre totalPedidos`. |
| `totalDescontos` | Descontos concedidos nos pedidos não cancelados. |
| `itensVendidos` | Soma das quantidades dos itens dos pedidos não cancelados. |
| `comparativo.variacao*` | Variação em %; `null` quando o período anterior não tem base (zero). |

Erros: `400`, `401`, `403`.

---

## GET /vendas/serie

Série temporal de vendas (RF02). Todo intervalo do período aparece, inclusive sem vendas. Cada ponto traz também
o intervalo de mesma posição no período anterior, usado na linha de comparação do gráfico.

| Parâmetro | Tipo | Padrão | Descrição |
|-----------|------|--------|-----------|
| `agrupamento` | `DIA`, `SEMANA`, `MES` | `DIA` | Granularidade. A semana começa na segunda-feira; `periodo` é o primeiro dia do intervalo. |

```bash
curl -u gerente01:SENHA "http://localhost:8080/api/v1/dashboard/vendas/serie?agrupamento=DIA&dataInicio=2026-11-04&dataFim=2026-11-10"
```

Resposta `200`:

```json
{
  "filtros": { "dataInicio": "2026-11-04", "dataFim": "2026-11-10", "status": [], "tipoEntrega": null },
  "geradoEm": "2026-11-10T16:22:19.1672249-03:00",
  "dados": {
    "agrupamento": "DIA",
    "pontos": [
      {
        "periodo": "2026-11-04", "rotulo": "04/11",
        "faturamento": 1379.48, "pedidos": 15, "cancelados": 1, "ticketMedio": 91.97, "descontos": 12.32,
        "periodoAnterior": "2026-10-28",
        "faturamentoAnterior": 1932.28, "pedidosAnterior": 15, "ticketMedioAnterior": 128.82
      },
      {
        "periodo": "2026-11-05", "rotulo": "05/11",
        "faturamento": 1445.76, "pedidos": 13, "cancelados": 0, "ticketMedio": 111.21, "descontos": 8.24,
        "periodoAnterior": "2026-10-29",
        "faturamentoAnterior": 1525.7, "pedidosAnterior": 15, "ticketMedioAnterior": 101.71
      },
      ...
    ]
  }
}
```

| Campo | Descrição |
|-------|-----------|
| `faturamento`, `pedidos`, `ticketMedio` | Só pedidos não cancelados do intervalo. |
| `cancelados` | Pedidos cancelados do intervalo. |
| `descontos` | Descontos concedidos nos pedidos não cancelados do intervalo. |
| `periodoAnterior` | Início do intervalo de mesma posição no período anterior (ex.: o 1º dia é comparado ao 1º dia do período anterior); `null` se não houver. |
| `faturamentoAnterior`, `pedidosAnterior`, `ticketMedioAnterior` | Valores desse intervalo anterior. |

Erros: `400` (inclusive `agrupamento` inválido), `401`, `403`.

---

## GET /vendas/por-categoria

Faturamento por categoria de produto, do maior para o menor (RF02).

Parâmetros: apenas os [comuns](#parâmetros-comuns-filtros).

```bash
curl -u gerente01:SENHA "http://localhost:8080/api/v1/dashboard/vendas/por-categoria"
```

Resposta `200`:

```json
{
  "filtros": { "dataInicio": "2026-10-12", "dataFim": "2026-11-10", "status": [], "tipoEntrega": null },
  "geradoEm": "2026-11-10T13:13:56.5661102-03:00",
  "dados": [
    { "categoria": "Pizza", "quantidade": 741, "faturamento": 39481.18, "participacao": 84.42 },
    { "categoria": "Lanche", "quantidade": 116, "faturamento": 2864.74, "participacao": 6.13 },
    { "categoria": "Porção", "quantidade": 85, "faturamento": 2510.1, "participacao": 5.37 },
    { "categoria": "Bebida", "quantidade": 84, "faturamento": 1164.8, "participacao": 2.49 },
    { "categoria": "Adicional", "quantidade": 77, "faturamento": 748.94, "participacao": 1.6 }
  ]
}
```

O faturamento de cada item já desconta, de forma proporcional, o desconto do pedido; por isso a soma das
categorias corresponde ao faturamento do `/resumo` (podendo diferir em centavos, pelo arredondamento de cada
categoria). Itens sem categoria aparecem como `Sem categoria`.

Erros: `400`, `401`, `403`.

---

## GET /vendas/por-hora

Pedidos não cancelados por hora do dia, sempre com as 24 horas (RF02).

```bash
curl -u gerente01:SENHA "http://localhost:8080/api/v1/dashboard/vendas/por-hora"
```

Resposta `200`:

```json
{
  "filtros": { "dataInicio": "2026-10-12", "dataFim": "2026-11-10", "status": [], "tipoEntrega": null },
  "geradoEm": "2026-11-10T13:13:56.7194385-03:00",
  "dados": [
    { "hora": 0, "pedidos": 0, "faturamento": 0.0 },
    ...
    { "hora": 11, "pedidos": 27, "faturamento": 2683.56 },
    ...
    { "hora": 21, "pedidos": 67, "faturamento": 6949.23 },
    ...
  ]
}
```

Erros: `400`, `401`, `403`.

---

## GET /vendas/por-dia-hora

Mapa de calor do movimento: pedidos não cancelados por **dia da semana e hora** da entrada (RF02). Sempre traz as
168 combinações (7 dias x 24 horas), de segunda a domingo, com zero onde não houve vendas.

Parâmetros: apenas os [comuns](#parâmetros-comuns-filtros).

```bash
curl -u gerente01:SENHA "http://localhost:8080/api/v1/dashboard/vendas/por-dia-hora"
```

Resposta `200`:

```json
{
  "filtros": { "dataInicio": "2026-10-12", "dataFim": "2026-11-10", "status": [], "tipoEntrega": null },
  "geradoEm": "2026-11-10T16:22:19.3009985-03:00",
  "dados": [
    { "diaSemana": 1, "dia": "Seg", "hora": 0, "pedidos": 0, "faturamento": 0.0 },
    ...
    { "diaSemana": 6, "dia": "Sáb", "hora": 21, "pedidos": 18, "faturamento": 1743.88 },
    ...
    { "diaSemana": 7, "dia": "Dom", "hora": 23, "pedidos": 8, "faturamento": 1067.39 }
  ]
}
```

| Campo | Descrição |
|-------|-----------|
| `diaSemana` | 1 = segunda-feira ... 7 = domingo (ISO 8601). |
| `dia` | Abreviação do dia em português. |
| `hora` | 0 a 23, no fuso da aplicação. |

Erros: `400`, `401`, `403`.

---

## GET /pedidos/situacao

Situação dos pedidos do período (RF03).

```bash
curl -u gerente01:SENHA "http://localhost:8080/api/v1/dashboard/pedidos/situacao"
```

Resposta `200`:

```json
{
  "filtros": { "dataInicio": "2026-10-12", "dataFim": "2026-11-10", "status": [], "tipoEntrega": null },
  "geradoEm": "2026-11-10T13:13:56.8086603-03:00",
  "dados": {
    "total": 486,
    "emAndamento": 3,
    "finalizados": 441,
    "cancelados": 41,
    "tempoMedioAteSaidaMinutos": 39.2,
    "porStatus": [
      { "status": "RECEBIDO", "descricao": "Recebido", "quantidade": 1, "valor": 158.7, "percentual": 0.21 },
      { "status": "EM_PREPARACAO", "descricao": "Em preparação", "quantidade": 2, "valor": 252.3, "percentual": 0.41 },
      { "status": "PRONTO", "descricao": "Pronto", "quantidade": 0, "valor": 0.0, "percentual": 0.0 },
      ...
    ]
  }
}
```

| Campo | Descrição |
|-------|-----------|
| `emAndamento` | Recebido + Em preparação + Pronto + Saiu para entrega. |
| `tempoMedioAteSaidaMinutos` | Média entre entrada e saída; `null` se nenhum pedido saiu no período. |
| `porStatus[].valor` | Soma do valor total dos pedidos naquele status. |

Erros: `400`, `401`, `403`.

---

## GET /pedidos

Lista paginada dos pedidos do período, do mais recente para o mais antigo (RF03).

| Parâmetro | Tipo | Padrão | Descrição |
|-----------|------|--------|-----------|
| `pagina` | inteiro >= 0 | `0` | Página, começando em zero. |
| `tamanho` | inteiro 1 a 100 | `10` | Pedidos por página. |

```bash
curl -u gerente01:SENHA "http://localhost:8080/api/v1/dashboard/pedidos?pagina=0&tamanho=2"
```

Resposta `200`:

```json
{
  "filtros": { "dataInicio": "2026-10-12", "dataFim": "2026-11-10", "status": [], "tipoEntrega": null },
  "geradoEm": "2026-11-10T13:13:56.8907525-03:00",
  "dados": {
    "conteudo": [
      {
        "id": "6af341d15837a41a0f49a180",
        "dataHoraEntrada": "2026-11-10T13:07:52.887",
        "nomeCliente": "Felipe Costa",
        "tipoEntrega": "RETIRADA",
        "status": "RECEBIDO",
        "statusDescricao": "Recebido",
        "valorTotal": 158.7,
        "quantidadeItens": 3,
        "responsavelEntrada": "gerente01",
        "dataHoraSaida": null
      },
      ...
    ],
    "pagina": 0,
    "tamanho": 2,
    "totalPaginas": 243,
    "totalElementos": 486
  }
}
```

A lista traz só o necessário para o acompanhamento (sem telefone ou endereço do cliente).

Erros: `400` (paginação inválida), `401`, `403`.

---

## GET /produtos/ranking

Produtos mais vendidos, apenas pedidos não cancelados (RF04).

| Parâmetro | Tipo | Padrão | Descrição |
|-----------|------|--------|-----------|
| `ordenarPor` | `QUANTIDADE` ou `FATURAMENTO` | `QUANTIDADE` | Critério do ranking (empate: ordem alfabética). |
| `limite` | inteiro 1 a 50 | `10` | Quantidade de produtos. |
| `categoria` | texto | todas | Restringe o ranking a uma categoria (ex.: `Pizza`). |

```bash
curl -u gerente01:SENHA "http://localhost:8080/api/v1/dashboard/produtos/ranking?limite=3"
```

Resposta `200`:

```json
{
  "filtros": { "dataInicio": "2026-10-12", "dataFim": "2026-11-10", "status": [], "tipoEntrega": null },
  "geradoEm": "2026-11-10T13:13:57.1169122-03:00",
  "dados": {
    "ordenarPor": "QUANTIDADE",
    "categoria": null,
    "limite": 3,
    "faturamentoTotal": 46769.77,
    "quantidadeTotal": 1103,
    "produtos": [
      { "posicao": 1, "produtoId": "6af341d05837a41a0f499c39", "nome": "Pizza Calabresa", "categoria": "Pizza",
        "quantidade": 388, "faturamento": 20284.5, "pedidos": 239, "participacao": 43.37 },
      { "posicao": 2, "produtoId": "6af341d05837a41a0f499c3b", "nome": "Pizza Quatro Queijos", "categoria": "Pizza",
        "quantidade": 193, "faturamento": 11267.57, "pedidos": 142, "participacao": 24.09 },
      { "posicao": 3, "produtoId": "6af341d05837a41a0f499c3a", "nome": "Pizza Margherita", "categoria": "Pizza",
        "quantidade": 160, "faturamento": 7929.11, "pedidos": 119, "participacao": 16.95 }
    ]
  }
}
```

| Campo | Descrição |
|-------|-----------|
| `faturamentoTotal` / `quantidadeTotal` | Totais de todos os itens do período (ou da categoria filtrada). |
| `produtos[].pedidos` | Em quantos pedidos o produto apareceu. |
| `produtos[].participacao` | % do faturamento do produto sobre `faturamentoTotal`. |

Erros: `400` (`limite` ou `ordenarPor` inválidos), `401`, `403`.

---

## GET /filtros

Valores aceitos pelos filtros e limites da API (RF05). Não usa o envelope e não recebe parâmetros.

```bash
curl -u gerente01:SENHA "http://localhost:8080/api/v1/dashboard/filtros"
```

Resposta `200`:

```json
{
  "status": [
    { "valor": "RECEBIDO", "descricao": "Recebido" },
    { "valor": "EM_PREPARACAO", "descricao": "Em preparação" },
    ...
  ],
  "tiposEntrega": [
    { "valor": "ENTREGA", "descricao": "Entrega" },
    { "valor": "RETIRADA", "descricao": "Retirada no balcão" }
  ],
  "categorias": ["Adicional", "Bebida", "Lanche", "Pizza", "Porção"],
  "agrupamentos": [
    { "valor": "DIA", "descricao": "Dia" },
    { "valor": "SEMANA", "descricao": "Semana" },
    { "valor": "MES", "descricao": "Mês" }
  ],
  "criteriosRanking": [
    { "valor": "QUANTIDADE", "descricao": "Quantidade vendida" },
    { "valor": "FATURAMENTO", "descricao": "Faturamento" }
  ],
  "dataInicioPadrao": "2026-10-12",
  "dataFimPadrao": "2026-11-10",
  "maximoDiasPeriodo": 366,
  "maximoTamanhoPagina": 100,
  "maximoLimiteRanking": 50
}
```

Erros: `401`, `403`.
