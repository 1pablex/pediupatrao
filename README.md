# Pediu Patrão — Controle de acesso, gestão de pedidos e auditoria

Sistema de pedidos de pizzaria desenvolvido em **Java 21, Spring Boot 3.4, Spring Security, MongoDB e Thymeleaf**.
Esta versão evolui o projeto base com perfis de acesso, fluxo controlado de pedidos, descontos e cancelamentos
exclusivos do gerente e auditoria persistente das alterações de pedidos e clientes.

---

## Como executar

### Pré-requisitos

- Java 21
- MongoDB rodando localmente na porta `27017` (ex.: MongoDB Community Server ou Docker:
  `docker run -d -p 27017:27017 mongo`)

### Configuração

As credenciais **não ficam no código**. Copie o modelo e defina as senhas dos usuários iniciais:

```bash
cp .env.example .env
```

```properties
MONGODB_URI=mongodb://localhost:27017
MONGODB_DATABASE=pediupatrao
SEED_SENHA_ADMIN=<senha do admin>
SEED_SENHA_GERENTE=<senha do gerente01>
SEED_SENHA_ATENDENTE=<senha do atendente01>
```

O arquivo `.env` está no `.gitignore`. As variáveis também podem ser definidas diretamente no ambiente.

### Rodando

```bash
./mvnw spring-boot:run        # Linux/macOS
mvnw.cmd spring-boot:run      # Windows
```

Acesse **http://localhost:8080**. Na primeira execução são criados:

| Usuário       | Perfil    | Senha                          |
|---------------|-----------|--------------------------------|
| `admin`       | ADMIN     | valor de `SEED_SENHA_ADMIN`    |
| `gerente01`   | GERENTE   | valor de `SEED_SENHA_GERENTE`  |
| `atendente01` | ATENDENTE | valor de `SEED_SENHA_ATENDENTE`|

Também é criado um cardápio inicial (pizzas, lanche, porção, bebida e adicional).

### Testes

```bash
./mvnw test
```

Os testes não precisam do MongoDB (repositórios são simulados com Mockito).

---

## Requisitos implementados

### RF01 — Controle de usuários e permissões

| Perfil    | Pode                                                                                          |
|-----------|-----------------------------------------------------------------------------------------------|
| ADMIN     | Administrar usuários e perfis, consultar pedidos, consultar a auditoria                       |
| GERENTE   | Registrar pedidos e controlar o andamento, **aplicar descontos**, **cancelar pedidos**, consultar clientes, gerenciar produtos, consultar a auditoria |
| ATENDENTE | Cadastrar, alterar e excluir clientes, registrar pedidos e acompanhar o andamento             |

- O perfil fica gravado no usuário (`enum Perfil`) e vira a role do Spring Security (`ROLE_ADMIN`, `ROLE_GERENTE`, `ROLE_ATENDENTE`) em `UsuarioDetailsService`.
- **Autorização em duas camadas no backend:**
  1. regras por URL em `SecurityConfig` (telas e API);
  2. `@PreAuthorize` em cada método dos serviços, que bloqueia a operação mesmo se alguém chamar o serviço por outro caminho.
- Somente o GERENTE aplica desconto e cancela pedido; o ADMIN também recebe **403**.
- **Proteção contra autoelevação:** a gestão de usuários é exclusiva do ADMIN (tela e API); o ADMIN não pode alterar o próprio perfil nem o próprio username, não pode excluir a si mesmo e o sistema nunca fica sem um ADMIN. O cadastro aceita apenas `username`, `password` e `perfil` (DTO), e a API nunca devolve a senha ou o hash.
- **CSRF:** ativo em todas as telas (o Thymeleaf injeta o token em todo formulário com `th:action`; logout é `POST`). A API REST (`/api/**`) usa HTTP Basic **sem sessão/cookie**, portanto não é alvo de CSRF, e por isso o CSRF é desativado apenas nela.
- Tentativas de acesso negado e falhas de login são registradas em log (sem senhas). Após **5 falhas de login** o usuário fica bloqueado por 5 minutos.
- A lista de clientes passou a ser renderizada no servidor (antes era montada com `innerHTML`, permitindo XSS).

### RF02 — Evolução do cadastro de pedidos

O `Pedido` agora tem: itens (`ItemPedido` com produto, nome, preço unitário, quantidade e subtotal), subtotal,
percentual e valor de desconto, **valor total**, tipo (entrega/retirada), responsável e data/hora de entrada,
dados de saída, finalização, desconto, cancelamento e histórico de status.

- Na criação, o cliente envia **apenas cliente, tipo, observação e a lista de produto + quantidade** (`PedidoForm`).
- Preços vêm do cadastro de produtos e o total é calculado em `PedidoService.recalcularValores`.
  Campos como `valorTotal`, `status` ou `precoUnitario` enviados na requisição são ignorados.
- Validações: cliente existente, pelo menos um item, produto existente e ativo, quantidade entre 1 e 50.
- O preço é copiado para o item: alterar o cardápio depois não muda pedidos já registrados.
- Valores monetários usam `BigDecimal`. `@Version` evita que duas alterações simultâneas se sobrescrevam.

### RF03 — Entrada, saída e transições

```
RECEBIDO → EM PREPARAÇÃO → PRONTO → SAIU PARA ENTREGA (entrega) → FINALIZADO
                                  ↘ RETIRADO (retirada)        ↗
CANCELADO: somente GERENTE, apenas antes da saída
```

- As transições permitidas estão em `StatusPedido.proximosPermitidos`; qualquer outra (ex.: finalizar um pedido em preparação) retorna **422** com a próxima etapa válida.
- RECEBIDO registra data/hora e responsável pela entrada; SAIU PARA ENTREGA / RETIRADO registra **obrigatoriamente** data/hora e responsável pela saída; FINALIZADO registra a finalização.
- Pedido cancelado não aceita nenhuma alteração. CANCELADO não pode ser alcançado pela troca de status, só pela operação de cancelamento.

### RF04 — Cancelamentos e descontos (exclusivos do GERENTE)

- **Cancelamento:** exige justificativa (10 a 500 caracteres), só antes da saída, preserva itens e valores e impede alterações posteriores.
- **Desconto:** percentual de 0% a **20%** sobre o subtotal, no máximo 2 casas decimais, somente antes da saída. Um novo desconto substitui o anterior (não acumula).
- Ambos são auditados com usuário, data/hora e valores anteriores e novos.

### RF05 — Auditoria

- Coleção `auditoria` no MongoDB (`RegistroAuditoria`), com: usuário autenticado e perfil, data/hora, operação, entidade e id, **campos alterados com valor anterior e novo** e justificativa.
- Auditados: inclusão de pedido, mudanças de status (incluindo saída), desconto, cancelamento, inclusão/alteração/exclusão de clientes (com destaque para endereço e telefone). Também são auditadas as alterações de produtos e de usuários.
- Usuário e perfil vêm sempre do contexto do Spring Security, nunca da requisição.
- **Integridade:** a entidade é imutável (sem setters) e o `AuditoriaRepository` estende `Repository` (não `MongoRepository`), expondo apenas `insert` e consultas. Não existe tela ou endpoint de alteração/exclusão (`DELETE /api/auditoria/...` → 403).
- **Proteção de dados:** senhas e campos de credenciais nunca são gravados (na troca de senha registra-se só "senha redefinida"); telefones são exibidos mascarados (`*******4321`).
- Consulta exclusiva de ADMIN e GERENTE: tela **Auditoria** (filtro por entidade e id, paginação), seção "Auditoria deste pedido" no detalhe do pedido e `GET /api/auditoria`.

Exemplo de registro (como aparece na tela):

| Data e hora         | Usuário                 | Operação            | Campos alterados                       |
|---------------------|-------------------------|---------------------|----------------------------------------|
| 23/09/2026 14:35:12 | atendente01 (ATENDENTE) | Alteração de cliente| logradouro: Rua das Flores → Av. Brasil |

---

## Principais alterações em relação ao projeto base

| Área | Alteração |
|------|-----------|
| Configuração | Credenciais do MongoDB e senhas iniciais saíram do `application.properties` para variáveis de ambiente (`.env`); removido usuário padrão do Spring Security |
| Segurança | `SecurityConfig` com duas cadeias (API stateless com HTTP Basic / telas com sessão e CSRF), regras por perfil, `@EnableMethodSecurity`, respostas 401/403 padronizadas, bloqueio por tentativas |
| Usuários | `Perfil` (ADMIN/GERENTE/ATENDENTE) no lugar de `role` livre; `/api/usuarios` deixou de ser público; DTOs de entrada e saída |
| Pedidos | Novo modelo com itens e valores calculados no servidor, `StatusPedido` com transições, desconto e cancelamento |
| Auditoria | Nova entidade, repositório somente-inserção, serviço, tela e API de consulta |
| Erros | `RegraNegocioException`/`RecursoNaoEncontradoException` com handlers para API (ProblemDetail: 400/404/409/422) e telas |
| Telas | Novas telas de pedido (cadastro, detalhe e ações), auditoria e produtos (a tela anterior estava quebrada); menu por perfil; botão Sair |
| Código | Controladores separados em `controller.api` e `controller.web`, injeção por construtor, validação com Bean Validation, removidas anotações JPA indevidas e páginas sem uso |
| Testes | 62 testes: regras de pedido, transições, auditoria, autorização nos serviços e nas rotas (API e telas) |

---

## API REST

Autenticação **HTTP Basic** (`-u usuario:senha`). Erros no formato ProblemDetail (JSON).

| Método | Rota | Perfis |
|--------|------|--------|
| GET | `/api/pedidos`, `/api/pedidos/{id}` | ADMIN, GERENTE, ATENDENTE |
| POST | `/api/pedidos` | GERENTE, ATENDENTE |
| PUT | `/api/pedidos/{id}/status` | GERENTE, ATENDENTE |
| POST | `/api/pedidos/{id}/desconto` | **GERENTE** |
| POST | `/api/pedidos/{id}/cancelamento` | **GERENTE** |
| GET | `/api/clientes`, `/api/clientes/{id}` | GERENTE, ATENDENTE |
| POST/PUT/DELETE | `/api/clientes...` | ATENDENTE |
| GET | `/api/produtos...` | autenticados |
| POST/PUT/DELETE | `/api/produtos...` | GERENTE |
| * | `/api/usuarios...` | ADMIN |
| GET | `/api/auditoria?entidade=PEDIDO&entidadeId=...&pagina=0&tamanho=20` | ADMIN, GERENTE |

### Demonstrando os bloqueios no backend

```bash
# Atendente tentando cancelar -> 403
curl -i -u atendente01:SENHA -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/pedidos/ID/cancelamento -d '{"justificativa":"tentativa indevida"}'

# Admin tentando aplicar desconto -> 403
curl -i -u admin:SENHA -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/pedidos/ID/desconto -d '{"percentual":10}'

# Gerente com desconto acima de 20% -> 400
curl -i -u gerente01:SENHA -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/pedidos/ID/desconto -d '{"percentual":25}'

# Transição inválida -> 422
curl -i -u atendente01:SENHA -H "Content-Type: application/json" \
  -X PUT http://localhost:8080/api/pedidos/ID/status -d '{"status":"FINALIZADO"}'

# Cadastro de usuário sem autenticação -> 401 / atendente consultando auditoria -> 403
curl -i -H "Content-Type: application/json" -X POST http://localhost:8080/api/usuarios \
  -d '{"username":"hacker","password":"12345678","perfil":"ADMIN"}'
curl -i -u atendente01:SENHA http://localhost:8080/api/auditoria
```

---

## Roteiro sugerido para o vídeo

1. **Login com os três perfis** e o menu de cada um (ADMIN: Usuários/Auditoria; GERENTE: Clientes/Auditoria; ATENDENTE: Clientes).
2. **ATENDENTE**: cadastra um cliente e registra um pedido (mostrar total calculado, entrada e responsável).
3. **Fluxo do pedido**: avança para Em preparação → Pronto → Saiu para entrega (mostrar data/hora e responsável pela saída) → Finalizado.
4. **Bloqueios**: atendente acessando `/auditoria` ou `/usuarios` (403); comandos `curl` acima (403/422/401).
5. **GERENTE**: aplica desconto de 10% (e tenta 25%), cancela outro pedido com justificativa e mostra que ele não aceita mais alterações; tenta cancelar um pedido que já saiu (bloqueado).
6. **ATENDENTE** altera endereço e telefone do cliente.
7. **Auditoria** (GERENTE ou ADMIN): mostrar os registros gerados nos passos anteriores, com valores anteriores/novos, justificativa e telefone mascarado.
8. **ADMIN** tentando alterar o próprio perfil (bloqueado).

---

## Estrutura

```
src/main/java/com/umc/pediupatrao
├── config/        SecurityConfig, DataInitializer, MongoInitConfig, AppConfig
├── controller/
│   ├── api/       Controladores REST + ApiExceptionHandler
│   └── web/       Controladores das telas Thymeleaf + WebExceptionHandler
├── dto/           Dados de entrada/saída (formulários, requests, respostas)
├── entity/        Documentos MongoDB e enums (Perfil, StatusPedido, TipoOperacao...)
├── exception/     Exceções de negócio
├── repository/    Repositórios Spring Data (AuditoriaRepository somente-inserção)
├── security/      Usuário logado, bloqueio de login, respostas 401/403
└── service/       Regras de negócio + autorização (@PreAuthorize) + auditoria
```
