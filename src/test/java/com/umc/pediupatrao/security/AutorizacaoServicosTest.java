package com.umc.pediupatrao.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ClienteForm;
import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Prova que a autorização está nos serviços (@PreAuthorize), independentemente das telas e das rotas:
 * mesmo chamando o serviço diretamente, um perfil sem permissão recebe AccessDeniedException.
 */
@SpringJUnitConfig(AutorizacaoServicosTest.Config.class)
class AutorizacaoServicosTest {

    @Configuration
    @EnableMethodSecurity
    @Import({PedidoService.class, UsuarioService.class, ClienteService.class, AuditoriaService.class})
    static class Config {

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }
    }

    @MockitoBean
    private PedidoRepository pedidoRepository;
    @MockitoBean
    private ProdutoRepository produtoRepository;
    @MockitoBean
    private ClienteRepository clienteRepository;
    @MockitoBean
    private UsuarioRepository usuarioRepository;
    @MockitoBean
    private AuditoriaRepository auditoriaRepository;
    @MockitoBean
    private ContextoUsuario contextoUsuario;
    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PedidoService pedidoService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private AuditoriaService auditoriaService;

    @BeforeEach
    void setUp() {
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedidoRecebido()));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contextoUsuario.atual()).thenReturn(new UsuarioLogado("gerente01", "GERENTE"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoCancelaPedido() {
        assertThatThrownBy(() -> pedidoService.cancelar("p1", "Tentativa indevida do admin"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoAplicaDesconto() {
        assertThatThrownBy(() -> pedidoService.aplicarDesconto("p1", BigDecimal.TEN))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoCancelaNemAplicaDesconto() {
        assertThatThrownBy(() -> pedidoService.cancelar("p1", "Tentativa indevida do atendente"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> pedidoService.aplicarDesconto("p1", BigDecimal.TEN))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteCancelaEAplicaDesconto() {
        assertThatCode(() -> pedidoService.aplicarDesconto("p1", BigDecimal.TEN)).doesNotThrowAnyException();
        assertThatCode(() -> pedidoService.cancelar("p1", "Cliente desistiu do pedido")).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminApenasConsultaPedidos() {
        assertThatCode(() -> pedidoService.listarPedidos()).doesNotThrowAnyException();
        assertThatThrownBy(() -> pedidoService.avancarStatus("p1", StatusPedido.EM_PREPARACAO))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteNaoAdministraUsuarios() {
        UsuarioForm form = new UsuarioForm();
        form.setUsername("gerente01");
        form.setPassword("SenhaForte1");
        form.setPerfil(Perfil.ADMIN);

        assertThatThrownBy(() -> usuarioService.criar(form)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> usuarioService.atualizar("u1", form)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteApenasConsultaClientes() {
        assertThatCode(() -> clienteService.listarClientes()).doesNotThrowAnyException();
        assertThatThrownBy(() -> clienteService.atualizar("c1", new ClienteForm()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoConsultaAuditoria() {
        assertThatThrownBy(() -> auditoriaService.consultar(null, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static Pedido pedidoRecebido() {
        Pedido pedido = new Pedido();
        pedido.setId("p1");
        pedido.setTipoEntrega(TipoEntrega.ENTREGA);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setItens(new ArrayList<>(List.of(new ItemPedido("x", "Pizza", new BigDecimal("50.00"), 2))));
        pedido.setSubtotal(new BigDecimal("100.00"));
        pedido.setValorTotal(new BigDecimal("100.00"));
        return pedido;
    }
}
