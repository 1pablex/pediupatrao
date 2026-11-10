package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.RankingProdutos;
import com.umc.pediupatrao.dto.dashboard.ResumoVendas;
import com.umc.pediupatrao.dto.dashboard.SerieVendas;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.VendaPorCategoria;
import com.umc.pediupatrao.dto.dashboard.VendaPorHora;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import com.umc.pediupatrao.repository.DashboardRepository;
import com.umc.pediupatrao.repository.DashboardRepository.Agregado;
import com.umc.pediupatrao.repository.DashboardRepository.ItemVendido;
import com.umc.pediupatrao.repository.DashboardRepository.ResultadoRanking;
import com.umc.pediupatrao.repository.DashboardRepository.Totais;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"),
            ZoneId.of("America/Sao_Paulo"));

    @Mock
    private DashboardRepository repository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(repository, RELOGIO);
    }

    // ---------------------------------------------------------------- RF01

    @Test
    void resumoCalculaIndicadoresEComparaComPeriodoAnterior() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-07");
        when(repository.totais(filtro)).thenReturn(
                new Totais(12, 2, new BigDecimal("1000.00"), new BigDecimal("50.00"), 30, null));
        when(repository.totais(filtro.periodoAnterior())).thenReturn(
                new Totais(8, 0, new BigDecimal("800.00"), BigDecimal.ZERO, 20, null));

        ResumoVendas r = service.resumo(filtro);

        assertThat(r.faturamento()).isEqualByComparingTo("1000.00");
        assertThat(r.totalPedidos()).isEqualTo(12);
        assertThat(r.pedidosValidos()).isEqualTo(10);
        assertThat(r.pedidosCancelados()).isEqualTo(2);
        assertThat(r.ticketMedio()).isEqualByComparingTo("100.00");
        assertThat(r.taxaCancelamento()).isEqualByComparingTo("16.67");
        assertThat(r.totalDescontos()).isEqualByComparingTo("50.00");
        assertThat(r.itensVendidos()).isEqualTo(30);

        ResumoVendas.Comparativo c = r.comparativo();
        assertThat(c.dataInicio()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(c.dataFim()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(c.variacaoFaturamento()).isEqualByComparingTo("25.00");
        assertThat(c.variacaoPedidos()).isEqualByComparingTo("25.00");
        assertThat(c.variacaoTicketMedio()).isEqualByComparingTo("0.00");
    }

    @Test
    void resumoSemVendasNoPeriodoAnteriorNaoTemVariacao() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-07");
        when(repository.totais(filtro)).thenReturn(
                new Totais(3, 0, new BigDecimal("150.00"), BigDecimal.ZERO, 5, null));
        when(repository.totais(filtro.periodoAnterior())).thenReturn(Totais.VAZIO);

        ResumoVendas r = service.resumo(filtro);

        assertThat(r.comparativo().variacaoFaturamento()).isNull();
        assertThat(r.comparativo().variacaoPedidos()).isNull();
    }

    @Test
    void resumoDePeriodoVazioRetornaZerosSemDivisaoPorZero() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-07");
        when(repository.totais(any())).thenReturn(Totais.VAZIO);

        ResumoVendas r = service.resumo(filtro);

        assertThat(r.faturamento()).isEqualByComparingTo("0");
        assertThat(r.ticketMedio()).isEqualByComparingTo("0");
        assertThat(r.taxaCancelamento()).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- RF02

    @Test
    void serieDiariaPreencheDiasSemVendaComZero() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-05");
        when(repository.seriePorPeriodo(filtro, Agrupamento.DIA)).thenReturn(Map.of(
                "2026-11-02", new Agregado(4, 1, new BigDecimal("200.00"), new BigDecimal("10.00")),
                "2026-11-04", new Agregado(1, 0, new BigDecimal("55.50"))));
        when(repository.seriePorPeriodo(filtro.periodoAnterior(), Agrupamento.DIA)).thenReturn(Map.of(
                "2026-10-28", new Agregado(2, 0, new BigDecimal("150.00"))));

        SerieVendas serie = service.serie(filtro, Agrupamento.DIA);

        assertThat(serie.pontos()).extracting(SerieVendas.Ponto::periodo)
                .containsExactly("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05");
        assertThat(serie.pontos()).extracting(SerieVendas.Ponto::pedidos).containsExactly(0L, 4L, 0L, 1L, 0L);
        SerieVendas.Ponto dia2 = serie.pontos().get(1);
        assertThat(dia2.rotulo()).isEqualTo("02/11");
        assertThat(dia2.ticketMedio()).isEqualByComparingTo("50.00");
        assertThat(dia2.cancelados()).isEqualTo(1);
        assertThat(dia2.descontos()).isEqualByComparingTo("10.00");
    }

    @Test
    void serieTrazOIntervaloCorrespondenteDoPeriodoAnterior() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-05");
        when(repository.seriePorPeriodo(filtro, Agrupamento.DIA)).thenReturn(Map.of());
        when(repository.seriePorPeriodo(filtro.periodoAnterior(), Agrupamento.DIA)).thenReturn(Map.of(
                "2026-10-28", new Agregado(2, 0, new BigDecimal("150.00"))));

        SerieVendas serie = service.serie(filtro, Agrupamento.DIA);

        // o período anterior de 01/11 a 05/11 é 27/10 a 31/10: o 2º ponto corresponde a 28/10
        SerieVendas.Ponto ponto = serie.pontos().get(1);
        assertThat(ponto.periodoAnterior()).isEqualTo("2026-10-28");
        assertThat(ponto.faturamentoAnterior()).isEqualByComparingTo("150.00");
        assertThat(ponto.pedidosAnterior()).isEqualTo(2);
        assertThat(ponto.ticketMedioAnterior()).isEqualByComparingTo("75.00");
        assertThat(serie.pontos().get(0).periodoAnterior()).isEqualTo("2026-10-27");
    }

    @Test
    void mapaDeCalorTrazAs168CelulasComecandoNaSegunda() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-07");
        when(repository.porDiaEHora(filtro)).thenReturn(List.of(
                new DashboardRepository.Celula(5, 21, new Agregado(9, 0, new BigDecimal("900")))));

        List<com.umc.pediupatrao.dto.dashboard.VendaPorDiaHora> celulas = service.porDiaEHora(filtro);

        assertThat(celulas).hasSize(168);
        assertThat(celulas.get(0).dia()).isEqualTo("Seg");
        assertThat(celulas.get(0).diaSemana()).isEqualTo(1);
        assertThat(celulas.get(167).dia()).isEqualTo("Dom");
        assertThat(celulas).filteredOn(c -> c.diaSemana() == 5 && c.hora() == 21).singleElement()
                .satisfies(c -> {
                    assertThat(c.dia()).isEqualTo("Sex");
                    assertThat(c.pedidos()).isEqualTo(9);
                });
        assertThat(celulas.stream().mapToLong(c -> c.pedidos()).sum()).isEqualTo(9);
    }

    @Test
    void serieSemanalComecaNaSegundaFeira() {
        // 05/11/2026 é quinta-feira: a primeira semana começa na segunda 02/11
        FiltroDashboard filtro = filtro("2026-11-05", "2026-11-20");
        when(repository.seriePorPeriodo(any(), eq(Agrupamento.SEMANA))).thenReturn(Map.of());

        SerieVendas serie = service.serie(filtro, Agrupamento.SEMANA);

        assertThat(serie.pontos()).extracting(SerieVendas.Ponto::periodo)
                .containsExactly("2026-11-02", "2026-11-09", "2026-11-16");
    }

    @Test
    void serieMensalUsaOPrimeiroDiaDoMes() {
        FiltroDashboard filtro = filtro("2026-09-15", "2026-11-10");
        when(repository.seriePorPeriodo(any(), eq(Agrupamento.MES))).thenReturn(Map.of());

        SerieVendas serie = service.serie(filtro, Agrupamento.MES);

        assertThat(serie.pontos()).extracting(SerieVendas.Ponto::rotulo)
                .containsExactly("09/2026", "10/2026", "11/2026");
    }

    @Test
    void vendasPorHoraTrazAs24Horas() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-07");
        when(repository.porHora(filtro)).thenReturn(Map.of(20, new Agregado(9, 0, new BigDecimal("450"))));

        List<VendaPorHora> horas = service.porHora(filtro);

        assertThat(horas).hasSize(24);
        assertThat(horas.get(20).pedidos()).isEqualTo(9);
        assertThat(horas.get(3).pedidos()).isZero();
    }

    @Test
    void vendasPorCategoriaCalculaParticipacao() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-07");
        when(repository.vendasPorCategoria(filtro)).thenReturn(List.of(
                new DashboardRepository.VendaCategoria("Pizza", 10, new BigDecimal("750")),
                new DashboardRepository.VendaCategoria("Bebida", 5, new BigDecimal("250"))));

        List<VendaPorCategoria> categorias = service.porCategoria(filtro);

        assertThat(categorias).extracting(VendaPorCategoria::participacao)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("75.00"), new BigDecimal("25.00"));
    }

    // ---------------------------------------------------------------- RF03

    @Test
    void situacaoListaTodosOsStatusESomaOsEmAndamento() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-07");
        Map<StatusPedido, Agregado> porStatus = new EnumMap<>(StatusPedido.class);
        porStatus.put(StatusPedido.RECEBIDO, new Agregado(2, 0, new BigDecimal("100")));
        porStatus.put(StatusPedido.EM_PREPARACAO, new Agregado(1, 0, new BigDecimal("50")));
        porStatus.put(StatusPedido.FINALIZADO, new Agregado(6, 0, new BigDecimal("600")));
        porStatus.put(StatusPedido.CANCELADO, new Agregado(1, 0, new BigDecimal("40")));
        when(repository.porStatus(filtro)).thenReturn(porStatus);
        when(repository.totais(filtro)).thenReturn(
                new Totais(10, 1, BigDecimal.ZERO, BigDecimal.ZERO, 0, 2_100_000.0));

        SituacaoPedidos s = service.situacao(filtro);

        assertThat(s.porStatus()).hasSize(StatusPedido.values().length);
        assertThat(s.total()).isEqualTo(10);
        assertThat(s.emAndamento()).isEqualTo(3);
        assertThat(s.finalizados()).isEqualTo(6);
        assertThat(s.cancelados()).isEqualTo(1);
        assertThat(s.tempoMedioAteSaidaMinutos()).isEqualByComparingTo("35.0");
        assertThat(s.porStatus()).filteredOn(c -> c.status() == StatusPedido.PRONTO)
                .singleElement().satisfies(c -> assertThat(c.quantidade()).isZero());
        assertThat(s.porStatus()).filteredOn(c -> c.status() == StatusPedido.FINALIZADO)
                .singleElement().satisfies(c -> assertThat(c.percentual()).isEqualByComparingTo("60.00"));
    }

    @Test
    void listaDePedidosValidaPaginacao() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-07");

        assertThatThrownBy(() -> service.pedidos(filtro, -1, 10)).isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> service.pedidos(filtro, 0, 0)).isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> service.pedidos(filtro, 0, 101))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("100");
        verify(repository, never()).listarPedidos(any(), anyInt(), anyInt());
    }

    // ---------------------------------------------------------------- RF04

    @Test
    void rankingNumeraPosicoesECalculaParticipacao() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-07");
        when(repository.rankingProdutos(filtro, null, CriterioRanking.QUANTIDADE, 10)).thenReturn(
                new ResultadoRanking(List.of(
                        new ItemVendido("p1", "Pizza Calabresa", "Pizza", 20, new BigDecimal("1000"), 15),
                        new ItemVendido("p2", "Refrigerante 2L", "Bebida", 12, new BigDecimal("168"), 12)),
                        new BigDecimal("2000"), 50));

        RankingProdutos ranking = service.ranking(filtro, " ", null, null);

        assertThat(ranking.ordenarPor()).isEqualTo(CriterioRanking.QUANTIDADE);
        assertThat(ranking.limite()).isEqualTo(10);
        assertThat(ranking.categoria()).isNull();
        assertThat(ranking.produtos()).extracting(RankingProdutos.Item::posicao).containsExactly(1, 2);
        assertThat(ranking.produtos().get(0).participacao()).isEqualByComparingTo("50.00");
        assertThat(ranking.produtos().get(1).participacao()).isEqualByComparingTo("8.40");
    }

    @Test
    void rankingRepassaCategoriaECriterio() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-07");
        when(repository.rankingProdutos(eq(filtro), eq("Pizza"), eq(CriterioRanking.FATURAMENTO), eq(5)))
                .thenReturn(new ResultadoRanking(List.of(), BigDecimal.ZERO, 0));

        RankingProdutos ranking = service.ranking(filtro, " Pizza ", CriterioRanking.FATURAMENTO, 5);

        assertThat(ranking.categoria()).isEqualTo("Pizza");
        assertThat(ranking.produtos()).isEmpty();
    }

    @Test
    void rankingValidaLimite() {
        FiltroDashboard filtro = filtro("2026-11-01", "2026-11-07");

        assertThatThrownBy(() -> service.ranking(filtro, null, null, 0)).isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> service.ranking(filtro, null, null, 51))
                .isInstanceOf(ParametroInvalidoException.class)
                .extracting("parametro").isEqualTo("limite");
    }

    // ---------------------------------------------------------------- RF05

    @Test
    void opcoesTrazemValoresAceitosEPeriodoPadrao() {
        when(repository.categorias()).thenReturn(List.of("Bebida", "Pizza"));

        var opcoes = service.opcoes();

        assertThat(opcoes.status()).hasSize(StatusPedido.values().length);
        assertThat(opcoes.categorias()).containsExactly("Bebida", "Pizza");
        assertThat(opcoes.dataFimPadrao()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(opcoes.dataInicioPadrao()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(opcoes.maximoDiasPeriodo()).isEqualTo(366);
    }

    private static FiltroDashboard filtro(String inicio, String fim) {
        return new FiltroDashboard(LocalDate.parse(inicio), LocalDate.parse(fim), List.of(), null);
    }
}
