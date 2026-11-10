package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.PedidoService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Gera 90 dias de pedidos fictícios para demonstrar o dashboard.
 * Só é ativado com app.demo.gerar-dados=true (DEMO_GERAR_DADOS no .env) e só executa quando o banco
 * ainda não tem pedidos, para nunca misturar dados fictícios com dados reais.
 * A sequência aleatória usa semente fixa: a mesma execução gera sempre o mesmo padrão de vendas.
 */
@Configuration
@ConditionalOnProperty(name = "app.demo.gerar-dados", havingValue = "true")
public class DemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    static final int DIAS = 90;
    static final int PEDIDOS_RECENTES = 7;
    private static final long SEMENTE = 2026L;

    private static final String[] NOMES = {"Ana Souza", "Bruno Lima", "Carla Mendes", "Diego Alves",
        "Eduarda Rocha", "Felipe Costa", "Gabriela Nunes", "Henrique Dias", "Isabela Martins", "João Pereira",
        "Larissa Gomes", "Marcos Ribeiro", "Natália Freitas", "Otávio Cardoso", "Paula Moreira"};
    private static final String[] BAIRROS = {"Centro", "Vila Nova", "Jardim das Flores", "Mogilar", "Brás Cubas"};

    @Bean
    @Order(2)
    CommandLineRunner gerarDadosDemonstracao(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, Clock clock) {
        return args -> {
            if (pedidoRepository.count() > 0) {
                log.info("Dados de demonstração não gerados: o banco já possui pedidos. "
                        + "Use outro MONGODB_DATABASE para uma base só de demonstração.");
                return;
            }
            List<Produto> produtos = produtoRepository.findByAtivoTrueOrderByTipoAscNomeAsc();
            if (produtos.isEmpty()) {
                log.warn("Dados de demonstração não gerados: nenhum produto ativo cadastrado.");
                return;
            }
            List<Cliente> clientes = clientesDemonstracao(clienteRepository);
            List<Pedido> pedidos = new Gerador(new Random(SEMENTE), produtos, clientes, LocalDateTime.now(clock))
                    .gerar();
            pedidoRepository.saveAll(pedidos);
            log.info("Dados de demonstração gerados: {} pedidos em {} dias.", pedidos.size(), DIAS);
        };
    }

    private static List<Cliente> clientesDemonstracao(ClienteRepository repo) {
        List<Cliente> existentes = repo.findAll();
        if (existentes.size() >= 5) {
            return existentes;
        }
        List<Cliente> novos = new ArrayList<>();
        for (int i = 0; i < NOMES.length; i++) {
            Cliente c = new Cliente();
            c.setNome(NOMES[i]);
            c.setTelefone(String.format("(11) 9%04d-%04d", 1000 + i * 37, 2000 + i * 53));
            c.setLogradouro("Rua Exemplo " + (i + 1));
            c.setNumero(String.valueOf(10 + i * 7));
            c.setBairro(BAIRROS[i % BAIRROS.length]);
            c.setCidade("Mogi das Cruzes");
            c.setEstado("SP");
            novos.add(c);
        }
        List<Cliente> todos = new ArrayList<>(existentes);
        todos.addAll(repo.saveAll(novos));
        return todos;
    }

    /** Regras de geração isoladas para manter o runner simples. */
    static final class Gerador {

        private final Random random;
        private final List<Produto> produtos;
        private final List<Cliente> clientes;
        private final LocalDateTime agora;

        Gerador(Random random, List<Produto> produtos, List<Cliente> clientes, LocalDateTime agora) {
            this.random = random;
            this.produtos = produtos;
            this.clientes = clientes;
            this.agora = agora;
        }

        List<Pedido> gerar() {
            List<Pedido> pedidos = new ArrayList<>();
            LocalDate hoje = agora.toLocalDate();
            for (int d = DIAS - 1; d >= 0; d--) {
                LocalDate dia = hoje.minusDays(d);
                int quantidade = pedidosNoDia(dia, DIAS - 1 - d);
                for (int i = 0; i < quantidade; i++) {
                    LocalDateTime entrada = dia.atTime(hora(), random.nextInt(60), random.nextInt(60));
                    if (!entrada.isAfter(agora)) {
                        pedidos.add(pedido(entrada));
                    }
                }
            }
            // Alguns pedidos na última hora, para sempre haver pedidos em andamento na demonstração
            for (int i = 0; i < PEDIDOS_RECENTES; i++) {
                pedidos.add(pedido(agora.minusMinutes(2 + (long) i * 9)));
            }
            return pedidos;
        }

        /** Fim de semana vende mais e há um leve crescimento ao longo do período. */
        private int pedidosNoDia(LocalDate dia, int indiceDia) {
            DayOfWeek semana = dia.getDayOfWeek();
            double fator = switch (semana) {
                case FRIDAY, SATURDAY -> 1.8;
                case SUNDAY -> 1.5;
                case MONDAY -> 0.7;
                default -> 1.0;
            };
            double base = 8 + indiceDia * 0.05;
            return (int) Math.round(base * fator + random.nextInt(5));
        }

        /** Pico no jantar (18h às 23h) e movimento menor no almoço (11h às 14h). */
        private int hora() {
            return random.nextInt(100) < 22 ? 11 + random.nextInt(4) : 18 + random.nextInt(6);
        }

        private Pedido pedido(LocalDateTime entrada) {
            Cliente cliente = clientes.get(random.nextInt(clientes.size()));
            String atendente = random.nextInt(100) < 80 ? "atendente01" : "gerente01";

            Pedido pedido = new Pedido();
            pedido.setClienteId(cliente.getId());
            pedido.setNomeCliente(cliente.getNome());
            pedido.setTipoEntrega(random.nextInt(100) < 65 ? TipoEntrega.ENTREGA : TipoEntrega.RETIRADA);
            pedido.setItens(itens());
            pedido.setDataHoraEntrada(entrada);
            pedido.setResponsavelEntrada(atendente);
            pedido.setStatus(StatusPedido.RECEBIDO);
            pedido.getHistorico().add(new HistoricoStatus(StatusPedido.RECEBIDO, entrada, atendente));

            if (random.nextInt(100) < 10) {
                pedido.setPercentualDesconto(new BigDecimal(5 * (1 + random.nextInt(4))));
                pedido.setResponsavelDesconto("gerente01");
                pedido.setDataHoraDesconto(entrada.plusMinutes(2));
            }
            PedidoService.recalcularValores(pedido);
            aplicarAndamento(pedido, entrada, atendente);
            return pedido;
        }

        private List<ItemPedido> itens() {
            List<ItemPedido> itens = new ArrayList<>();
            int linhas = 1 + random.nextInt(3);
            for (int i = 0; i < linhas; i++) {
                Produto p = produtos.get(random.nextInt(produtos.size()));
                // pizzas aparecem com mais frequência
                if (!"Pizza".equals(p.getTipo()) && random.nextInt(100) < 40) {
                    p = produtos.stream().filter(x -> "Pizza".equals(x.getTipo())).findAny().orElse(p);
                }
                int quantidade = 1 + (random.nextInt(100) < 25 ? 1 : 0);
                ItemPedido item = new ItemPedido(p.getId(), p.getNome(), p.getPreco(), quantidade);
                item.setCategoria(p.getTipo());
                itens.add(item);
            }
            return itens;
        }

        /** Pedidos antigos terminam finalizados (ou cancelados); os de hoje ficam em etapas conforme o horário. */
        private void aplicarAndamento(Pedido pedido, LocalDateTime entrada, String atendente) {
            long minutos = java.time.Duration.between(entrada, agora).toMinutes();
            boolean cancelar = random.nextInt(100) < 6;
            int minutosAteSaida = 25 + random.nextInt(30);

            if (cancelar && minutos > 10) {
                LocalDateTime quando = entrada.plusMinutes(5 + random.nextInt(10));
                pedido.setStatus(StatusPedido.CANCELADO);
                pedido.setDataHoraCancelamento(quando);
                pedido.setResponsavelCancelamento("gerente01");
                pedido.setJustificativaCancelamento("Cliente desistiu do pedido (dado de demonstração).");
                pedido.getHistorico().add(new HistoricoStatus(StatusPedido.CANCELADO, quando, "gerente01"));
                return;
            }
            avancar(pedido, StatusPedido.EM_PREPARACAO, entrada.plusMinutes(5), atendente, minutos);
            avancar(pedido, StatusPedido.PRONTO, entrada.plusMinutes(minutosAteSaida - 5), atendente, minutos);
            StatusPedido saida = pedido.getTipoEntrega() == TipoEntrega.ENTREGA
                    ? StatusPedido.SAIU_PARA_ENTREGA : StatusPedido.RETIRADO;
            if (avancar(pedido, saida, entrada.plusMinutes(minutosAteSaida), atendente, minutos)) {
                pedido.setDataHoraSaida(entrada.plusMinutes(minutosAteSaida));
                pedido.setResponsavelSaida(atendente);
            }
            LocalDateTime fim = entrada.plusMinutes(minutosAteSaida + 20);
            if (avancar(pedido, StatusPedido.FINALIZADO, fim, atendente, minutos)) {
                pedido.setDataHoraFinalizacao(fim);
                pedido.setResponsavelFinalizacao(atendente);
            }
        }

        private boolean avancar(Pedido pedido, StatusPedido status, LocalDateTime quando, String usuario,
                long minutosDesdeEntrada) {
            long minutosAteEtapa = java.time.Duration.between(pedido.getDataHoraEntrada(), quando).toMinutes();
            if (pedido.getStatus() == StatusPedido.CANCELADO || minutosAteEtapa > minutosDesdeEntrada) {
                return false;
            }
            pedido.setStatus(status);
            pedido.getHistorico().add(new HistoricoStatus(status, quando, usuario));
            return true;
        }
    }
}
