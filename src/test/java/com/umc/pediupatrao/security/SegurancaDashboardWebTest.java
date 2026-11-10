package com.umc.pediupatrao.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.web.DashboardWebController;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Página do dashboard: só ADMIN e GERENTE; a página carrega o script que consome a API. */
@WebMvcTest(controllers = DashboardWebController.class)
@Import(SecurityConfig.class)
class SegurancaDashboardWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void gerenteAbreODashboard() throws Exception {
        mvc.perform(get("/dashboard").with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/js/dashboard.js")))
                .andExpect(content().string(containsString("data-painel=\"resumo\"")));
    }

    @Test
    void adminAbreODashboard() throws Exception {
        mvc.perform(get("/dashboard").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void atendenteNaoAbreODashboard() throws Exception {
        mvc.perform(get("/dashboard").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void semLoginRedirecionaParaOLogin() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
