package com.umc.pediupatrao.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Registra em log os eventos de autenticação (sem nunca registrar senhas)
 * e alimenta o bloqueio por tentativas inválidas.
 */
@Component
public class EventosAutenticacaoListener {

    private static final Logger log = LoggerFactory.getLogger(EventosAutenticacaoListener.class);

    private final BloqueioLoginService bloqueioLoginService;

    public EventosAutenticacaoListener(BloqueioLoginService bloqueioLoginService) {
        this.bloqueioLoginService = bloqueioLoginService;
    }

    @EventListener
    public void aoAutenticar(AuthenticationSuccessEvent evento) {
        String username = evento.getAuthentication().getName();
        bloqueioLoginService.registrarSucesso(username);
        log.info("Login realizado: usuario={}", username);
    }

    @EventListener
    public void aoFalhar(AbstractAuthenticationFailureEvent evento) {
        String username = evento.getAuthentication().getName();
        bloqueioLoginService.registrarFalha(username);
        log.warn("Falha de login: usuario={} motivo={}", username, evento.getException().getClass().getSimpleName());
    }
}
