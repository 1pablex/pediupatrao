package com.umc.pediupatrao.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Obtém o usuário autenticado a partir do contexto do Spring Security.
 * Os serviços usam esta classe para registrar responsáveis e auditoria, nunca dados enviados pelo cliente.
 */
@Component
public class ContextoUsuario {

    private static final String PREFIXO_ROLE = "ROLE_";

    public UsuarioLogado atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Usuário não autenticado.");
        }
        String perfil = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(PREFIXO_ROLE))
                .map(a -> a.substring(PREFIXO_ROLE.length()))
                .findFirst()
                .orElse("DESCONHECIDO");
        return new UsuarioLogado(auth.getName(), perfil);
    }
}
