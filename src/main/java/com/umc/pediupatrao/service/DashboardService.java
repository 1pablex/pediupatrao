package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PaginaResponse;
import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltro;
import com.umc.pediupatrao.dto.dashboard.PedidoResumo;
import com.umc.pediupatrao.dto.dashboard.RankingProdutos;
import com.umc.pediupatrao.dto.dashboard.ResumoVendas;
import com.umc.pediupatrao.dto.dashboard.SerieVendas;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.VendaPorCategoria;
import com.umc.pediupatrao.dto.dashboard.VendaPorHora;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import com.umc.pediupatrao.repository.DashboardRepository;
import com.umc.pediupatrao.repository.DashboardRepository.Agregado;
import com.umc.pediupatrao.repository.DashboardRepository.ItemVendido;
import com.umc.pediupatrao.repository.DashboardRepository.ResultadoRanking;
import com.umc.pediupatrao.repository.DashboardRepository.Totais;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Indicadores do dashboard administrativo. Exclusivo de ADMIN e GERENTE (responsáveis pelo estabelecimento).
 *
 * Definições usadas em todas as métricas:
 * - venda = pedido não cancelado (inclui os que ainda estão em andamento);
 * - o período considera a data/hora de ENTRADA do pedido;
 * - valores em reais com 2 casas decimais (arredondamento bancário) e percentuais com 2 casas.
 */
@Service
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardService {

    public static final int LIMITE_PADRAO_RANKING = 10;
    public static final int LIMITE_MAXIMO_RANKING = 50;
    public static final int TAMANHO_MAXIMO_PAGINA = 100;

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final Set<StatusPedido> EM_ANDAMENTO = Set.of(StatusPedido.RECEBIDO,
            StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA);
    private static final DateTimeFormatter ROTULO_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter ROTULO_MES = DateTimeFormatter.ofPattern("MM/yyyy");

    private final DashboardRepository repository;
    private final Clock clock;

    public DashboardService(DashboardRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ RF01

    public ResumoVendas resumo(FiltroDashboard filtro) {
        Totais atual = repository.totais(filtro);
        FiltroDashboard filtroAnterior = filtro.periodoAnterior();
        Totais anterior = repository.totais(filtroAnterior);

        long validos = atual.pedidos() - atual.cancelados();
        long validosAnterior = anterior.pedidos() - anterior.cancelados();
        BigDecimal ticket = dividir(atual.faturamento(), validos);
        BigDecimal ticketAnterior = dividir(anterior.faturamento(), validosAnterior);

        ResumoVendas.Comparativo comparativo = new ResumoVendas.Comparativo(filtroAnterior.dataInicio(),
                filtroAnterior.dataFim(), moeda(anterior.faturamento()), validosAnterior, ticketAnterior,
                variacao(atual.faturamento(), anterior.faturamento()),
                variacao(BigDecimal.valueOf(validos), BigDecimal.valueOf(validosAnterior)),
                variacao(ticket, ticketAnterior));

        return new ResumoVendas(moeda(atual.faturamento()), atual.pedidos(), validos, atual.cancelados(), ticket,
                percentual(atual.cancelados(), atual.pedidos()), moeda(atual.descontos()), atual.itens(),
                comparativo);
    }

    // ------------------------------------------------------------------ RF02

    public SerieVendas serie(FiltroDashboard filtro, Agrupamento agrupamento) {
        Agrupamento grupo = agrupamento == null ? Agrupamento.DIA : agrupamento;
        Map<String, Agregado> valores = repository.seriePorPeriodo(filtro, grupo);

        List<SerieVendas.Ponto> pontos = new ArrayList<>();
        for (LocalDate inicio = alinhar(filtro.dataInicio(), grupo); !inicio.isAfter(filtro.dataFim());
                inicio = avancar(inicio, grupo)) {
            Agregado a = valores.get(inicio.toString());
            long pedidos = a == null ? 0 : a.pedidos();
            BigDecimal faturamento = a == null ? BigDecimal.ZERO : a.faturamento();
            pontos.add(new SerieVendas.Ponto(inicio.toString(), rotulo(inicio, grupo), moeda(faturamento), pedidos,
                    a == null ? 0 : a.cancelados(), dividir(faturamento, pedidos)));
        }
        return new SerieVendas(grupo, pontos);
    }

    public List<VendaPorHora> porHora(FiltroDashboard filtro) {
        Map<Integer, Agregado> valores = repository.porHora(filtro);
        List<VendaPorHora> horas = new ArrayList<>(24);
        for (int hora = 0; hora < 24; hora++) {
            Agregado a = valores.get(hora);
            horas.add(a == null
                    ? new VendaPorHora(hora, 0, moeda(BigDecimal.ZERO))
                    : new VendaPorHora(hora, a.pedidos(), moeda(a.faturamento())));
        }
        return horas;
    }

    public List<VendaPorCategoria> porCategoria(FiltroDashboard filtro) {
        List<DashboardRepository.VendaCategoria> categorias = repository.vendasPorCategoria(filtro);
        BigDecimal total = categorias.stream().map(DashboardRepository.VendaCategoria::faturamento)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return categorias.stream()
                .map(c -> new VendaPorCategoria(c.categoria(), c.quantidade(), moeda(c.faturamento()),
                        percentual(c.faturamento(), total)))
                .toList();
    }

    // ------------------------------------------------------------------ RF03

    public SituacaoPedidos situacao(FiltroDashboard filtro) {
        Map<StatusPedido, Agregado> valores = repository.porStatus(filtro);
        long total = valores.values().stream().mapToLong(Agregado::pedidos).sum();

        List<SituacaoPedidos.Contagem> contagens = new ArrayList<>();
        long emAndamento = 0;
        for (StatusPedido status : StatusPedido.values()) {
            Agregado a = valores.get(status);
            long quantidade = a == null ? 0 : a.pedidos();
            if (EM_ANDAMENTO.contains(status)) {
                emAndamento += quantidade;
            }
            contagens.add(new SituacaoPedidos.Contagem(status, status.getDescricao(), quantidade,
                    moeda(a == null ? BigDecimal.ZERO : a.faturamento()), percentual(quantidade, total)));
        }

        Double tempoMs = repository.totais(filtro).tempoMedioSaidaMs();
        BigDecimal tempoMinutos = tempoMs == null ? null
                : BigDecimal.valueOf(tempoMs).divide(BigDecimal.valueOf(60_000), 1, RoundingMode.HALF_EVEN);

        return new SituacaoPedidos(total, emAndamento, quantidade(valores, StatusPedido.FINALIZADO),
                quantidade(valores, StatusPedido.CANCELADO), tempoMinutos, contagens);
    }

    public PaginaResponse<PedidoResumo> pedidos(FiltroDashboard filtro, int pagina, int tamanho) {
        if (pagina < 0) {
            throw new ParametroInvalidoException("pagina", "pagina deve ser maior ou igual a 0.");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO_PAGINA) {
            throw new ParametroInvalidoException("tamanho",
                    "tamanho deve estar entre 1 e " + TAMANHO_MAXIMO_PAGINA + ".");
        }
        List<PedidoResumo> conteudo = repository.listarPedidos(filtro, pagina, tamanho).stream()
                .map(PedidoResumo::de)
                .toList();
        long total = repository.contarPedidos(filtro);
        return PaginaResponse.de(new PageImpl<>(conteudo, PageRequest.of(pagina, tamanho), total));
    }

    // ------------------------------------------------------------------ RF04

    public RankingProdutos ranking(FiltroDashboard filtro, String categoria, CriterioRanking criterio,
            Integer limite) {
        int limiteEfetivo = limite == null ? LIMITE_PADRAO_RANKING : limite;
        if (limiteEfetivo < 1 || limiteEfetivo > LIMITE_MAXIMO_RANKING) {
            throw new ParametroInvalidoException("limite",
                    "limite deve estar entre 1 e " + LIMITE_MAXIMO_RANKING + ".");
        }
        CriterioRanking ordem = criterio == null ? CriterioRanking.QUANTIDADE : criterio;
        String categoriaFiltro = categoria == null || categoria.isBlank() ? null : categoria.trim();

        ResultadoRanking resultado = repository.rankingProdutos(filtro, categoriaFiltro, ordem, limiteEfetivo);
        List<RankingProdutos.Item> itens = new ArrayList<>();
        int posicao = 1;
        for (ItemVendido item : resultado.itens()) {
            itens.add(new RankingProdutos.Item(posicao++, item.produtoId(), item.nome(), item.categoria(),
                    item.quantidade(), moeda(item.faturamento()), item.pedidos(),
                    percentual(item.faturamento(), resultado.faturamentoTotal())));
        }
        return new RankingProdutos(ordem, categoriaFiltro, limiteEfetivo, moeda(resultado.faturamentoTotal()),
                resultado.quantidadeTotal(), itens);
    }

    // ------------------------------------------------------------------ RF05

    public OpcoesFiltro opcoes() {
        LocalDate hoje = LocalDate.now(clock);
        return new OpcoesFiltro(
                Arrays.stream(StatusPedido.values()).map(s -> new OpcoesFiltro.Opcao(s.name(), s.getDescricao())).toList(),
                Arrays.stream(TipoEntrega.values()).map(t -> new OpcoesFiltro.Opcao(t.name(), t.getDescricao())).toList(),
                repository.categorias(),
                Arrays.stream(Agrupamento.values()).map(a -> new OpcoesFiltro.Opcao(a.name(), a.getDescricao())).toList(),
                Arrays.stream(CriterioRanking.values()).map(c -> new OpcoesFiltro.Opcao(c.name(), c.getDescricao())).toList(),
                hoje.minusDays(FiltroDashboard.DIAS_PADRAO - 1L), hoje, FiltroDashboard.MAXIMO_DIAS,
                TAMANHO_MAXIMO_PAGINA, LIMITE_MAXIMO_RANKING);
    }

    // ------------------------------------------------------------------ cálculos

    static LocalDate alinhar(LocalDate data, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> data;
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> data.withDayOfMonth(1);
        };
    }

    private static LocalDate avancar(LocalDate data, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> data.plusDays(1);
            case SEMANA -> data.plusWeeks(1);
            case MES -> data.plusMonths(1);
        };
    }

    private static String rotulo(LocalDate data, Agrupamento agrupamento) {
        return agrupamento == Agrupamento.MES ? data.format(ROTULO_MES) : data.format(ROTULO_DIA);
    }

    private static long quantidade(Map<StatusPedido, Agregado> valores, StatusPedido status) {
        Agregado a = valores.get(status);
        return a == null ? 0 : a.pedidos();
    }

    static BigDecimal moeda(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_EVEN);
    }

    static BigDecimal dividir(BigDecimal valor, long quantidade) {
        if (quantidade == 0) {
            return moeda(BigDecimal.ZERO);
        }
        return valor.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_EVEN);
    }

    static BigDecimal percentual(long parte, long total) {
        return percentual(BigDecimal.valueOf(parte), BigDecimal.valueOf(total));
    }

    static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return parte.multiply(CEM).divide(total, 2, RoundingMode.HALF_EVEN);
    }

    /** (atual - anterior) / anterior em %; nulo quando não há base de comparação. */
    static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) {
            return null;
        }
        return atual.subtract(anterior).multiply(CEM).divide(anterior, 2, RoundingMode.HALF_EVEN);
    }
}
