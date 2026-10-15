package com.umc.pediupatrao.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.api.AuditoriaApiController;
import com.umc.pediupatrao.controller.api.PedidoApiController;
import com.umc.pediupatrao.controller.api.UsuarioApiController;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tentativas de acesso às APIs com perfis sem permissão devem ser bloqueadas no backend (401/403),
 * sem chegar à camada de serviço.
 */
@WebMvcTest(controllers = {PedidoApiController.class, UsuarioApiController.class, AuditoriaApiController.class})
@Import(SecurityConfig.class)
class SegurancaApiTest {

    private static final String JUSTIFICATIVA = "{\"justificativa\":\"Cliente desistiu do pedido\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void semCredenciaisRetorna401() throws Exception {
        mvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void senhaErradaRetorna401() throws Exception {
        when(usuarioDetailsService.loadUserByUsername("gerente01")).thenReturn(usuarioComSenha("gerente01", "GERENTE"));

        mvc.perform(get("/api/pedidos").with(httpBasic("gerente01", "senha-errada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void atendenteAutenticadoViaBasicNaoPodeCancelar() throws Exception {
        when(usuarioDetailsService.loadUserByUsername("atendente01"))
                .thenReturn(usuarioComSenha("atendente01", "ATENDENTE"));

        mvc.perform(post("/api/pedidos/p1/cancelamento").with(httpBasic("atendente01", "Senha@123"))
                        .contentType(MediaType.APPLICATION_JSON).content(JUSTIFICATIVA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verify(pedidoService, never()).cancelar(anyString(), anyString());
    }

    @Test
    void adminNaoPodeAplicarDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\":10}"))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).aplicarDesconto(anyString(), any());
    }

    @Test
    void gerentePodeCancelar() throws Exception {
        when(pedidoService.cancelar(eq("p1"), anyString())).thenReturn(new Pedido());

        mvc.perform(post("/api/pedidos/p1/cancelamento").with(user("gerente01").roles("GERENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(JUSTIFICATIVA))
                .andExpect(status().isOk());
        verify(pedidoService).cancelar("p1", "Cliente desistiu do pedido");
    }

    @Test
    void descontoAcimaDoLimiteERejeitadoNaValidacao() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto").with(user("gerente01").roles("GERENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\":25}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.percentual").exists());
        verify(pedidoService, never()).aplicarDesconto(anyString(), any());
    }

    @Test
    void atendenteNaoAdministraUsuarios() throws Exception {
        mvc.perform(post("/api/usuarios").with(user("atendente01").roles("ATENDENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacker\",\"password\":\"SenhaForte1\",\"perfil\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        verify(usuarioService, never()).criar(any());
    }

    @Test
    void cadastroDeUsuarioNaoEPublico() throws Exception {
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacker\",\"password\":\"SenhaForte1\",\"perfil\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void auditoriaSomenteParaAdminEGerente() throws Exception {
        when(auditoriaService.consultar(any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());

        mvc.perform(get("/api/auditoria").with(user("gerente01").roles("GERENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditoriaNaoPodeSerExcluida() throws Exception {
        mvc.perform(delete("/api/auditoria/qualquer").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    private static User usuarioComSenha(String username, String perfil) {
        return (User) User.withUsername(username)
                .password(new BCryptPasswordEncoder().encode("Senha@123"))
                .roles(perfil)
                .build();
    }
}
