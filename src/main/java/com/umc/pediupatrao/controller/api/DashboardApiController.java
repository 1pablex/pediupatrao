package com.umc.pediupatrao.controller.api;

import com.umc.pediupatrao.dto.PaginaResponse;
import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltro;
import com.umc.pediupatrao.dto.dashboard.PedidoResumo;
import com.umc.pediupatrao.dto.dashboard.RankingProdutos;
import com.umc.pediupatrao.dto.dashboard.RespostaDashboard;
import com.umc.pediupatrao.dto.dashboard.ResumoVendas;
import com.umc.pediupatrao.dto.dashboard.SerieVendas;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.VendaPorCategoria;
import com.umc.pediupatrao.dto.dashboard.VendaPorDiaHora;
import com.umc.pediupatrao.dto.dashboard.VendaPorHora;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.service.DashboardService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API do dashboard administrativo (somente leitura). Todos os endpoints aceitam os filtros comuns:
 * dataInicio, dataFim (yyyy-MM-dd), status (um ou mais) e tipoEntrega.
 * Documentação completa em docs/api-dashboard.md e docs/openapi.yaml.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardApiController {

    private final DashboardService dashboardService;
    private final Clock clock;

    public DashboardApiController(DashboardService dashboardService, Clock clock) {
        this.dashboardService = dashboardService;
        this.clock = clock;
    }

    @GetMapping("/resumo")
    public RespostaDashboard<ResumoVendas> resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) List<StatusPedido> status,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        FiltroDashboard filtro = filtro(dataInicio, dataFim, status, tipoEntrega);
        return responder(filtro, dashboardService.resumo(filtro));
    }

    @GetMapping("/vendas/serie")
    public RespostaDashboard<SerieVendas> serie(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) List<StatusPedido> status,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(defaultValue = "DIA") Agrupamento agrupamento) {
        FiltroDashboard filtro = filtro(dataInicio, dataFim, status, tipoEntrega);
        return responder(filtro, dashboardService.serie(filtro, agrupamento));
    }

    @GetMapping("/vendas/por-categoria")
    public RespostaDashboard<List<VendaPorCategoria>> porCategoria(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) List<StatusPedido> status,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        FiltroDashboard filtro = filtro(dataInicio, dataFim, status, tipoEntrega);
        return responder(filtro, dashboardService.porCategoria(filtro));
    }

    @GetMapping("/vendas/por-hora")
    public RespostaDashboard<List<VendaPorHora>> porHora(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) List<StatusPedido> status,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        FiltroDashboard filtro = filtro(dataInicio, dataFim, status, tipoEntrega);
        return responder(filtro, dashboardService.porHora(filtro));
    }

    @GetMapping("/vendas/por-dia-hora")
    public RespostaDashboard<List<VendaPorDiaHora>> porDiaEHora(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) List<StatusPedido> status,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        FiltroDashboard filtro = filtro(dataInicio, dataFim, status, tipoEntrega);
        return responder(filtro, dashboardService.porDiaEHora(filtro));
    }

    @GetMapping("/pedidos/situacao")
    public RespostaDashboard<SituacaoPedidos> situacao(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) List<StatusPedido> status,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        FiltroDashboard filtro = filtro(dataInicio, dataFim, status, tipoEntrega);
        return responder(filtro, dashboardService.situacao(filtro));
    }

    @GetMapping("/pedidos")
    public RespostaDashboard<PaginaResponse<PedidoResumo>> pedidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) List<StatusPedido> status,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanho) {
        FiltroDashboard filtro = filtro(dataInicio, dataFim, status, tipoEntrega);
        return responder(filtro, dashboardService.pedidos(filtro, pagina, tamanho));
    }

    @GetMapping("/produtos/ranking")
    public RespostaDashboard<RankingProdutos> ranking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) List<StatusPedido> status,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "QUANTIDADE") CriterioRanking ordenarPor,
            @RequestParam(required = false) Integer limite) {
        FiltroDashboard filtro = filtro(dataInicio, dataFim, status, tipoEntrega);
        return responder(filtro, dashboardService.ranking(filtro, categoria, ordenarPor, limite));
    }

    /** Opções dos filtros (status, tipos, categorias, agrupamentos) e limites aceitos. */
    @GetMapping("/filtros")
    public OpcoesFiltro filtros() {
        return dashboardService.opcoes();
    }

    private FiltroDashboard filtro(LocalDate dataInicio, LocalDate dataFim, List<StatusPedido> status,
            TipoEntrega tipoEntrega) {
        return FiltroDashboard.de(dataInicio, dataFim, status, tipoEntrega, LocalDate.now(clock));
    }

    private <T> RespostaDashboard<T> responder(FiltroDashboard filtro, T dados) {
        return new RespostaDashboard<>(filtro, OffsetDateTime.now(clock), dados);
    }
}
