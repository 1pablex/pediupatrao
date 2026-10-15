package com.umc.pediupatrao.service;

import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.BloqueioLoginService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final BloqueioLoginService bloqueioLoginService;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository, BloqueioLoginService bloqueioLoginService) {
        this.usuarioRepository = usuarioRepository;
        this.bloqueioLoginService = bloqueioLoginService;
    }

    /**
     * A role vem do perfil gravado no banco (ROLE_ADMIN, ROLE_GERENTE ou ROLE_ATENDENTE).
     * Usuários sem perfil não conseguem autenticar.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizado = username == null ? "" : username.trim().toLowerCase();
        return usuarioRepository.findByUsername(normalizado)
                .filter(usuario -> usuario.getPerfil() != null)
                .map(usuario -> User.builder()
                        .username(usuario.getUsername())
                        .password(usuario.getPassword())
                        .roles(usuario.getPerfil().name())
                        .accountLocked(bloqueioLoginService.isBloqueado(usuario.getUsername()))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
