# Evidências: chamadas reais à API do dashboard

Executado em 10/11/2026 16:23:22 contra a aplicação rodando em `localhost:8080`, banco de demonstração gerado com `DEMO_GERAR_DADOS=true`. Autenticação HTTP Basic (as senhas não aparecem neste relatório).

Resultado: **26 de 26** chamadas com o status esperado.

| Resultado | Cenário | Requisição | Usuário | Esperado | Obtido | Tempo (ms) | Mensagem |
|---|---|---|---|---|---|---|---|
| OK | Sem credenciais | `GET /resumo` | anônimo | 401 | 401 | 73 | Autenticação necessária. |
| OK | ATENDENTE não acessa o dashboard | `GET /resumo` | atendente01 | 403 | 403 | 73 | Acesso negado: seu perfil não permite esta operação. |
| OK | ADMIN consulta o resumo | `GET /resumo` | admin | 200 | 200 | 82 |  |
| OK | GERENTE consulta o resumo | `GET /resumo` | gerente01 | 200 | 200 | 86 |  |
| OK | Série diária | `GET /vendas/serie?agrupamento=DIA&dataInicio=2026-11-04&dataFim=2026-11-10` | gerente01 | 200 | 200 | 78 |  |
| OK | Série semanal (90 dias) | `GET /vendas/serie?agrupamento=SEMANA&dataInicio=2026-08-13&dataFim=2026-11-10` | gerente01 | 200 | 200 | 88 |  |
| OK | Série mensal | `GET /vendas/serie?agrupamento=MES&dataInicio=2026-08-13&dataFim=2026-11-10` | gerente01 | 200 | 200 | 86 |  |
| OK | Vendas por categoria | `GET /vendas/por-categoria` | gerente01 | 200 | 200 | 226 |  |
| OK | Vendas por hora | `GET /vendas/por-hora` | gerente01 | 200 | 200 | 80 |  |
| OK | Mapa de calor dia x hora | `GET /vendas/por-dia-hora` | gerente01 | 200 | 200 | 78 |  |
| OK | ATENDENTE no mapa de calor | `GET /vendas/por-dia-hora` | atendente01 | 403 | 403 | 77 | Acesso negado: seu perfil não permite esta operação. |
| OK | Situação dos pedidos | `GET /pedidos/situacao` | gerente01 | 200 | 200 | 79 |  |
| OK | Pedidos paginados | `GET /pedidos?pagina=0&tamanho=2` | gerente01 | 200 | 200 | 76 |  |
| OK | Ranking por quantidade | `GET /produtos/ranking?limite=3` | gerente01 | 200 | 200 | 227 |  |
| OK | Ranking por faturamento, só pizzas | `GET /produtos/ranking?ordenarPor=FATURAMENTO&categoria=Pizza&limite=3` | gerente01 | 200 | 200 | 225 |  |
| OK | Filtro por status e tipo | `GET /resumo?status=FINALIZADO&tipoEntrega=RETIRADA` | gerente01 | 200 | 200 | 79 |  |
| OK | Opções de filtro | `GET /filtros` | gerente01 | 200 | 200 | 75 |  |
| OK | Período invertido | `GET /resumo?dataInicio=2026-11-10&dataFim=2026-11-01` | gerente01 | 400 | 400 | 85 | dataInicio deve ser anterior ou igual a dataFim. |
| OK | Período maior que 366 dias | `GET /resumo?dataInicio=2025-01-01&dataFim=2026-11-10` | gerente01 | 400 | 400 | 78 | O período pode ter no máximo 366 dias (informado: 679). |
| OK | Data em formato inválido | `GET /resumo?dataInicio=10/11/2026` | gerente01 | 400 | 400 | 81 | Valor inválido para o parâmetro 'dataInicio'. |
| OK | Status inexistente | `GET /resumo?status=ENTREGUE` | gerente01 | 400 | 400 | 78 | Valor inválido para o parâmetro 'status'. |
| OK | Agrupamento inválido | `GET /vendas/serie?agrupamento=ANO` | gerente01 | 400 | 400 | 75 | Valor inválido para o parâmetro 'agrupamento'. |
| OK | Página muito grande | `GET /pedidos?tamanho=500` | gerente01 | 400 | 400 | 73 | tamanho deve estar entre 1 e 100. |
| OK | Limite do ranking fora da faixa | `GET /produtos/ranking?limite=0` | gerente01 | 400 | 400 | 73 | limite deve estar entre 1 e 50. |
| OK | Escrita não é permitida (POST) | `POST /resumo` | admin | 403 | 403 | 3 | Acesso negado: seu perfil não permite esta operação. |
| OK | Escrita não é permitida (DELETE) | `DELETE /pedidos` | admin | 403 | 403 | 2 | Acesso negado: seu perfil não permite esta operação. |
