package com.umc.pediupatrao.security;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.AppConfig;
import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.api.DashboardApiController;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.ResumoVendas;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API do dashboard: acesso exclusivo de ADMIN e GERENTE, somente leitura, e erros de parâmetro em ProblemDetail.
 */
@WebMvcTest(controllers = DashboardApiController.class)
@Import({SecurityConfig.class, AppConfig.class})
class SegurancaDashboardApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void semAutenticacaoRetorna401() throws Exception {
        mvc.perform(get("/api/v1/dashboard/resumo"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")));
        verifyNoInteractions(dashboardService);
    }

    @Test
    void chamadaAjaxSemSessaoRecebe401SemDesafioBasic() throws Exception {
        mvc.perform(get("/api/v1/dashboard/resumo").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void atendenteNaoAcessaODashboard() throws Exception {
        mvc.perform(get("/api/v1/dashboard/resumo").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dashboardService);
    }

    @Test
    void gerenteEAdminAcessamComSessao() throws Exception {
        when(dashboardService.resumo(any())).thenReturn(resumoVazio());

        mvc.perform(get("/api/v1/dashboard/resumo").with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.faturamento").value(0))
                .andExpect(jsonPath("$.filtros.dataInicio").exists())
                .andExpect(jsonPath("$.geradoEm").exists());
        mvc.perform(get("/api/v1/dashboard/resumo").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void integracaoExternaComHttpBasic() throws Exception {
        when(usuarioDetailsService.loadUserByUsername("gerente01")).thenReturn((User) User.withUsername("gerente01")
                .password(new BCryptPasswordEncoder().encode("Senha@123")).roles("GERENTE").build());
        when(dashboardService.resumo(any())).thenReturn(resumoVazio());

        mvc.perform(get("/api/v1/dashboard/resumo").with(httpBasic("gerente01", "Senha@123")))
                .andExpect(status().isOk());
    }

    @Test
    void apiESomenteLeitura() throws Exception {
        mvc.perform(post("/api/v1/dashboard/resumo").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/dashboard/pedidos").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void filtrosSaoRepassadosAoServico() throws Exception {
        when(dashboardService.resumo(any())).thenReturn(resumoVazio());

        mvc.perform(get("/api/v1/dashboard/resumo").with(user("gerente01").roles("GERENTE"))
                        .param("dataInicio", "2026-11-01").param("dataFim", "2026-11-07")
                        .param("status", "FINALIZADO", "CANCELADO").param("tipoEntrega", "RETIRADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros.status.length()").value(2));

        verify(dashboardService).resumo(eq(new FiltroDashboard(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7),
                List.of(StatusPedido.FINALIZADO, StatusPedido.CANCELADO), TipoEntrega.RETIRADA)));
    }

    @Test
    void periodoInvertidoRetorna400() throws Exception {
        mvc.perform(get("/api/v1/dashboard/resumo").with(user("gerente01").roles("GERENTE"))
                        .param("dataInicio", "2026-11-10").param("dataFim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parâmetro inválido"))
                .andExpect(jsonPath("$.erros.dataInicio").exists());
        verifyNoInteractions(dashboardService);
    }

    @Test
    void valoresInvalidosRetornam400() throws Exception {
        mvc.perform(get("/api/v1/dashboard/resumo").with(user("gerente01").roles("GERENTE"))
                        .param("status", "ENTREGUE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.status").exists());
        mvc.perform(get("/api/v1/dashboard/vendas/serie").with(user("gerente01").roles("GERENTE"))
                        .param("dataInicio", "10/11/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.dataInicio").exists());
        mvc.perform(get("/api/v1/dashboard/vendas/serie").with(user("gerente01").roles("GERENTE"))
                        .param("agrupamento", "ANO"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dashboardService);
    }

    @Test
    void endpointsDeListagemRespondem() throws Exception {
        when(dashboardService.pedidos(any(), anyInt(), anyInt()))
                .thenReturn(com.umc.pediupatrao.dto.PaginaResponse.de(org.springframework.data.domain.Page.empty()));

        mvc.perform(get("/api/v1/dashboard/pedidos").with(user("admin").roles("ADMIN"))
                        .param("pagina", "0").param("tamanho", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados.conteudo").isArray());
        verify(dashboardService).pedidos(any(), eq(0), eq(5));
    }

    @Test
    void mapaDeCalorSoParaAdminEGerente() throws Exception {
        when(dashboardService.porDiaEHora(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/dashboard/vendas/por-dia-hora").with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados").isArray());
        mvc.perform(get("/api/v1/dashboard/vendas/por-dia-hora").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
    }

    private static ResumoVendas resumoVazio() {
        return new ResumoVendas(BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0,
                new ResumoVendas.Comparativo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), BigDecimal.ZERO,
                        0, BigDecimal.ZERO, null, null, null));
    }
}
