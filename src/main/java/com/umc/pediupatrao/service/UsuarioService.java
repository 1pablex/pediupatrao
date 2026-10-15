package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.ContextoUsuario;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Administração de usuários: exclusiva do ADMIN.
 * Impede que o administrador altere o próprio perfil, exclua a si mesmo
 * ou deixe o sistema sem nenhum ADMIN.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final ContextoUsuario contextoUsuario;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService, ContextoUsuario contextoUsuario) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.contextoUsuario = contextoUsuario;
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAllByOrderByUsernameAsc();
    }

    public Usuario buscarPorId(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    public Usuario criar(UsuarioForm form) {
        String username = normalizar(form.getUsername());
        if (usuarioRepository.existsByUsername(username)) {
            throw new RegraNegocioException("Já existe um usuário com o username '" + username + "'.");
        }
        validarSenha(form.getPassword(), true);
        exigirPerfil(form.getPerfil());

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(form.getPassword()));
        usuario.setPerfil(form.getPerfil());
        Usuario salvo = usuarioRepository.save(usuario);

        auditoriaService.registrar(TipoOperacao.INCLUSAO_USUARIO, EntidadeAuditada.USUARIO, salvo.getId(),
                new Alteracoes()
                        .registrar("username", null, salvo.getUsername())
                        .registrar("perfil", null, salvo.getPerfil()));
        return salvo;
    }

    public Usuario atualizar(String id, UsuarioForm form) {
        Usuario existente = buscarPorId(id);
        String novoUsername = normalizar(form.getUsername());
        exigirPerfil(form.getPerfil());
        boolean ehOProprioUsuario = existente.getUsername().equals(contextoUsuario.atual().username());

        if (ehOProprioUsuario && form.getPerfil() != existente.getPerfil()) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
        }
        if (ehOProprioUsuario && !novoUsername.equals(existente.getUsername())) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio username.");
        }
        if (existente.getPerfil() == Perfil.ADMIN && form.getPerfil() != Perfil.ADMIN) {
            garantirOutroAdmin();
        }
        if (!novoUsername.equals(existente.getUsername()) && usuarioRepository.existsByUsername(novoUsername)) {
            throw new RegraNegocioException("Já existe um usuário com o username '" + novoUsername + "'.");
        }

        Alteracoes alteracoes = new Alteracoes()
                .comparar("username", existente.getUsername(), novoUsername)
                .comparar("perfil", existente.getPerfil(), form.getPerfil());

        existente.setUsername(novoUsername);
        existente.setPerfil(form.getPerfil());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            validarSenha(form.getPassword(), false);
            existente.setPassword(passwordEncoder.encode(form.getPassword()));
            // A senha nunca é gravada na auditoria: registra-se apenas que ela foi redefinida.
            alteracoes.registrar("credencial", null, "senha redefinida");
        }
        Usuario salvo = usuarioRepository.save(existente);

        if (!alteracoes.isVazia()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_USUARIO, EntidadeAuditada.USUARIO, salvo.getId(),
                    alteracoes);
        }
        return salvo;
    }

    public void excluir(String id) {
        Usuario existente = buscarPorId(id);
        if (existente.getUsername().equals(contextoUsuario.atual().username())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (existente.getPerfil() == Perfil.ADMIN) {
            garantirOutroAdmin();
        }
        usuarioRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_USUARIO, EntidadeAuditada.USUARIO, id,
                new Alteracoes()
                        .registrar("username", existente.getUsername(), null)
                        .registrar("perfil", existente.getPerfil(), null));
    }

    private void garantirOutroAdmin() {
        if (usuarioRepository.countByPerfil(Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("O sistema precisa ter pelo menos um usuário ADMIN.");
        }
    }

    private static void exigirPerfil(Perfil perfil) {
        if (perfil == null) {
            throw new RegraNegocioException("Selecione um perfil válido.");
        }
    }

    private static void validarSenha(String senha, boolean obrigatoria) {
        if (senha == null || senha.isBlank()) {
            if (obrigatoria) {
                throw new RegraNegocioException("Informe a senha.");
            }
            return;
        }
        if (senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }

    private static String normalizar(String username) {
        if (username == null || username.isBlank()) {
            throw new RegraNegocioException("Informe o username.");
        }
        return username.trim().toLowerCase();
    }
}
