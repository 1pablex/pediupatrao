package com.umc.pediupatrao.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.DashboardRepository.Agregado;
import com.umc.pediupatrao.repository.DashboardRepository.ResultadoRanking;
import com.umc.pediupatrao.repository.DashboardRepository.Totais;
import com.umc.pediupatrao.service.PedidoService;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Integração real com o MongoDB: executa os pipelines de agregação sobre pedidos gravados pelo próprio teste.
 * Usa um banco exclusivo de teste e é ignorado (não falha) quando não há MongoDB em localhost:27017.
 */
@DataMongoTest
@Import({DashboardRepository.class, DashboardRepositoryMongoTest.RelogioConfig.class})
@TestPropertySource(properties = {
    "spring.data.mongodb.uri=mongodb://localhost:27017",
    "spring.data.mongodb.database=pediupatrao_teste_dashboard"
})
class DashboardRepositoryMongoTest {

    static class RelogioConfig {
        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }
    }

    private static final LocalDate DIA1 = LocalDate.of(2026, 11, 2); // segunda-feira
    private static final LocalDate DIA2 = LocalDate.of(2026, 11, 3);
    private static final FiltroDashboard PERIODO = new FiltroDashboard(DIA1, DIA2, List.of(), null);

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private DashboardRepository repository;

    private Produto pizza;
    private Produto refri;

    @BeforeAll
    static void exigirMongo() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 27017), 500);
        } catch (IOException e) {
            assumeTrue(false, "MongoDB local indisponível: teste de integração ignorado.");
        }
    }

    @BeforeEach
    void prepararDados() {
        mongoTemplate.dropCollection(Pedido.class);
        mongoTemplate.dropCollection(Produto.class);
        pizza = mongoTemplate.insert(produto("Pizza Calabresa", "Pizza", "50.00"));
        refri = mongoTemplate.insert(produto("Refrigerante 2L", "Bebida", "10.00"));

        // Dia 1, 20h: 2 pizzas + 1 refri = 110,00, entregue em 30 minutos
        Pedido p1 = pedido(DIA1.atTime(20, 0), TipoEntrega.ENTREGA, StatusPedido.FINALIZADO,
                item(pizza, 2), item(refri, 1));
        p1.setDataHoraSaida(DIA1.atTime(20, 30));
        // Dia 1, 21h: 1 pizza = 50,00 com 10% de desconto -> 45,00, retirado em 20 minutos
        Pedido p2 = pedido(DIA1.atTime(21, 15), TipoEntrega.RETIRADA, StatusPedido.FINALIZADO, item(pizza, 1));
        p2.setPercentualDesconto(new BigDecimal("10"));
        PedidoService.recalcularValores(p2);
        p2.setDataHoraSaida(DIA1.atTime(21, 35));
        // Dia 2, 12h: cancelado (não é venda)
        Pedido p3 = pedido(DIA2.atTime(12, 5), TipoEntrega.ENTREGA, StatusPedido.CANCELADO, item(pizza, 3));
        // Dia 2, 20h: em preparação, item antigo sem categoria gravada (deve buscar no cadastro)
        ItemPedido semCategoria = item(refri, 2);
        semCategoria.setCategoria(null);
        Pedido p4 = pedido(DIA2.atTime(20, 40), TipoEntrega.ENTREGA, StatusPedido.EM_PREPARACAO, semCategoria);
        // Fora do período
        Pedido p5 = pedido(DIA2.plusDays(1).atTime(0, 0), TipoEntrega.ENTREGA, StatusPedido.FINALIZADO,
                item(pizza, 1));
        mongoTemplate.insertAll(List.of(p1, p2, p3, p4, p5));

        // Pedido do modelo antigo, sem data de entrada nem itens: precisa ser ignorado sem erro
        mongoTemplate.getCollection("pedidos").insertOne(new Document("tipoPizza", "Calabresa")
                .append("status", "ENTREGUE").append("quantidade", 1));
    }

    @Test
    void totaisConsideramSoVendasNoPeriodo() {
        Totais t = repository.totais(PERIODO);

        assertThat(t.pedidos()).isEqualTo(4);
        assertThat(t.cancelados()).isEqualTo(1);
        assertThat(t.faturamento()).isEqualByComparingTo("175.00"); // 110 + 45 + 20
        assertThat(t.descontos()).isEqualByComparingTo("5.00");
        assertThat(t.itens()).isEqualTo(6); // 3 + 1 + 2
        assertThat(t.tempoMedioSaidaMs()).isEqualTo(25 * 60_000.0); // média de 30 e 20 minutos
    }

    @Test
    void seriePorDiaAgrupaNoFusoDaAplicacao() {
        Map<String, Agregado> serie = repository.seriePorPeriodo(PERIODO, Agrupamento.DIA);

        assertThat(serie).containsOnlyKeys("2026-11-02", "2026-11-03");
        assertThat(serie.get("2026-11-02").pedidos()).isEqualTo(2);
        assertThat(serie.get("2026-11-02").faturamento()).isEqualByComparingTo("155.00");
        assertThat(serie.get("2026-11-03").pedidos()).isEqualTo(1);
        assertThat(serie.get("2026-11-03").cancelados()).isEqualTo(1);
    }

    @Test
    void seriePorSemanaUsaASegundaFeiraComoChave() {
        Map<String, Agregado> serie = repository.seriePorPeriodo(PERIODO, Agrupamento.SEMANA);

        assertThat(serie).containsOnlyKeys("2026-11-02");
        assertThat(serie.get("2026-11-02").pedidos()).isEqualTo(3);
    }

    @Test
    void porHoraIgnoraCancelados() {
        Map<Integer, Agregado> horas = repository.porHora(PERIODO);

        assertThat(horas).containsOnlyKeys(20, 21);
        assertThat(horas.get(20).pedidos()).isEqualTo(2);
    }

    @Test
    void porStatusSomaValorDosPedidos() {
        Map<StatusPedido, Agregado> status = repository.porStatus(PERIODO);

        assertThat(status.get(StatusPedido.FINALIZADO).pedidos()).isEqualTo(2);
        assertThat(status.get(StatusPedido.CANCELADO).pedidos()).isEqualTo(1);
        assertThat(status.get(StatusPedido.EM_PREPARACAO).faturamento()).isEqualByComparingTo("20.00");
    }

    @Test
    void rankingPorQuantidadeRateiaODescontoEBuscaCategoriaNoCadastro() {
        ResultadoRanking r = repository.rankingProdutos(PERIODO, null, CriterioRanking.QUANTIDADE, 10);

        assertThat(r.quantidadeTotal()).isEqualTo(6);
        assertThat(r.faturamentoTotal()).isEqualByComparingTo("175.00");
        // Pizza: 3 unidades (2 + 1), 100,00 + 45,00 com o desconto rateado; Refri: 3 unidades, 10 + 20
        assertThat(r.itens()).extracting(DashboardRepository.ItemVendido::nome)
                .containsExactly("Pizza Calabresa", "Refrigerante 2L");
        assertThat(r.itens().get(0).faturamento()).isEqualByComparingTo("145.00");
        assertThat(r.itens().get(0).pedidos()).isEqualTo(2);
        assertThat(r.itens().get(1).categoria()).isEqualTo("Bebida");
    }

    @Test
    void rankingFiltraPorCategoria() {
        ResultadoRanking r = repository.rankingProdutos(PERIODO, "Bebida", CriterioRanking.FATURAMENTO, 10);

        assertThat(r.itens()).extracting(DashboardRepository.ItemVendido::produtoId).containsExactly(refri.getId());
        assertThat(r.faturamentoTotal()).isEqualByComparingTo("30.00");
    }

    @Test
    void vendasPorCategoria() {
        List<DashboardRepository.VendaCategoria> categorias = repository.vendasPorCategoria(PERIODO);

        assertThat(categorias).extracting(DashboardRepository.VendaCategoria::categoria)
                .containsExactly("Pizza", "Bebida");
        assertThat(categorias.get(0).faturamento()).isEqualByComparingTo("145.00");
    }

    @Test
    void filtrosDeStatusETipoDeEntrega() {
        FiltroDashboard soRetirada = new FiltroDashboard(DIA1, DIA2, List.of(), TipoEntrega.RETIRADA);
        FiltroDashboard soCancelados = new FiltroDashboard(DIA1, DIA2, List.of(StatusPedido.CANCELADO), null);

        assertThat(repository.totais(soRetirada).pedidos()).isEqualTo(1);
        assertThat(repository.totais(soCancelados).faturamento()).isEqualByComparingTo("0");
        assertThat(repository.porHora(soCancelados)).isEmpty();
    }

    @Test
    void listagemPaginadaOrdenaDoMaisRecente() {
        List<Pedido> pagina = repository.listarPedidos(PERIODO, 0, 3);

        assertThat(repository.contarPedidos(PERIODO)).isEqualTo(4);
        assertThat(pagina).hasSize(3);
        assertThat(pagina.get(0).getDataHoraEntrada()).isEqualTo(DIA2.atTime(20, 40));
        assertThat(repository.listarPedidos(PERIODO, 1, 3)).hasSize(1);
    }

    @Test
    void categoriasJuntamCadastroEPedidos() {
        assertThat(repository.categorias()).containsExactly("Bebida", "Pizza");
    }

    // ---------------------------------------------------------------- apoio

    private static Produto produto(String nome, String tipo, String preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(new BigDecimal(preco));
        p.setAtivo(true);
        return p;
    }

    private static ItemPedido item(Produto produto, int quantidade) {
        ItemPedido item = new ItemPedido(produto.getId(), produto.getNome(), produto.getPreco(), quantidade);
        item.setCategoria(produto.getTipo());
        return item;
    }

    private static Pedido pedido(LocalDateTime entrada, TipoEntrega tipo, StatusPedido status, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setNomeCliente("Cliente Teste");
        p.setTipoEntrega(tipo);
        p.setItens(new ArrayList<>(List.of(itens)));
        PedidoService.recalcularValores(p);
        p.setStatus(status);
        p.setDataHoraEntrada(entrada);
        p.setResponsavelEntrada("atendente01");
        return p;
    }
}
