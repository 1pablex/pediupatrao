package com.umc.pediupatrao.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.web.PedidoWebController;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Telas: rotas protegidas por perfil e formulários protegidos por token CSRF. */
@WebMvcTest(controllers = PedidoWebController.class)
@Import(SecurityConfig.class)
class SegurancaWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private ClienteService clienteService;
    @MockitoBean
    private ProdutoService produtoService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void usuarioNaoAutenticadoERedirecionadoParaLogin() throws Exception {
        mvc.perform(get("/pedidos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void formularioSemTokenCsrfERecusado() throws Exception {
        mvc.perform(post("/pedidos/p1/cancelar").param("justificativa", "Cliente desistiu do pedido")
                        .with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).cancelar(anyString(), anyString());
    }

    @Test
    void gerenteCancelaComTokenCsrf() throws Exception {
        mvc.perform(post("/pedidos/p1/cancelar").param("justificativa", "Cliente desistiu do pedido")
                        .with(user("gerente01").roles("GERENTE")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pedidos/p1"));
        verify(pedidoService).cancelar("p1", "Cliente desistiu do pedido");
    }

    @Test
    void atendenteNaoCancelaMesmoComTokenCsrf() throws Exception {
        mvc.perform(post("/pedidos/p1/cancelar").param("justificativa", "Cliente desistiu do pedido")
                        .with(user("atendente01").roles("ATENDENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).cancelar(anyString(), anyString());
    }

    @Test
    void adminNaoRegistraPedidos() throws Exception {
        mvc.perform(get("/pedidos/novo").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
