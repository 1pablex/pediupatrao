package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.ContextoUsuario;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ContextoUsuario contextoUsuario;

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(usuarioRepository, passwordEncoder, auditoriaService, contextoUsuario);
        lenient().when(contextoUsuario.atual()).thenReturn(new UsuarioLogado("admin", "ADMIN"));
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        lenient().when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void adminNaoPodeAlterarOProprioPerfil() {
        when(usuarioRepository.findById("u-admin")).thenReturn(Optional.of(usuario("u-admin", "admin", Perfil.ADMIN)));

        assertThatThrownBy(() -> service.atualizar("u-admin", form("admin", null, Perfil.ATENDENTE)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("próprio perfil");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void naoPermiteRemoverOUltimoAdmin() {
        when(usuarioRepository.findById("u2")).thenReturn(Optional.of(usuario("u2", "outroadmin", Perfil.ADMIN)));
        when(usuarioRepository.countByPerfil(Perfil.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.atualizar("u2", form("outroadmin", null, Perfil.GERENTE)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("pelo menos um usuário ADMIN");
    }

    @Test
    void adminNaoPodeExcluirASiMesmo() {
        when(usuarioRepository.findById("u-admin")).thenReturn(Optional.of(usuario("u-admin", "admin", Perfil.ADMIN)));

        assertThatThrownBy(() -> service.excluir("u-admin"))
                .isInstanceOf(RegraNegocioException.class);
        verify(usuarioRepository, never()).deleteById(anyString());
    }

    @Test
    void criarExigeSenhaComTamanhoMinimo() {
        assertThatThrownBy(() -> service.criar(form("novo", "1234567", Perfil.ATENDENTE)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("8 caracteres");
    }

    @Test
    void criarRejeitaUsernameDuplicado() {
        when(usuarioRepository.existsByUsername("atendente01")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(form(" Atendente01 ", "SenhaForte1", Perfil.ATENDENTE)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void criarGravaSenhaCriptografadaENaoAuditaSenha() {
        Usuario salvo = service.criar(form("Novo.Atendente", "SenhaForte1", Perfil.ATENDENTE));

        assertThat(salvo.getUsername()).isEqualTo("novo.atendente");
        assertThat(salvo.getPassword()).isEqualTo("$2a$10$hash");
        ArgumentCaptor<Alteracoes> captor = ArgumentCaptor.forClass(Alteracoes.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_USUARIO), eq(EntidadeAuditada.USUARIO), any(),
                captor.capture());
        assertThat(captor.getValue().lista()).extracting(AlteracaoCampo::getCampo).containsExactly("username", "perfil");
    }

    @Test
    void redefinicaoDeSenhaNaoExpoeValorNaAuditoria() {
        when(usuarioRepository.findById("u3")).thenReturn(Optional.of(usuario("u3", "atendente01", Perfil.ATENDENTE)));

        service.atualizar("u3", form("atendente01", "NovaSenha123", Perfil.ATENDENTE));

        ArgumentCaptor<Alteracoes> captor = ArgumentCaptor.forClass(Alteracoes.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_USUARIO), eq(EntidadeAuditada.USUARIO), eq("u3"),
                captor.capture());
        assertThat(captor.getValue().lista()).allSatisfy(a -> {
            assertThat(a.getValorNovo()).doesNotContain("NovaSenha123").doesNotContain("$2a$");
            assertThat(a.getCampo()).isNotEqualTo("password");
        });
    }

    private static UsuarioForm form(String username, String senha, Perfil perfil) {
        UsuarioForm form = new UsuarioForm();
        form.setUsername(username);
        form.setPassword(senha);
        form.setPerfil(perfil);
        return form;
    }

    private static Usuario usuario(String id, String username, Perfil perfil) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setPassword("$2a$10$antigo");
        u.setPerfil(perfil);
        return u;
    }
}
