package com.umc.pediupatrao.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respostas padronizadas para tentativas de acesso não autenticadas (401) ou não autorizadas (403).
 * Toda tentativa negada é registrada em log com usuário, método e URL.
 */
public final class RespostasSeguranca {

    private static final Logger log = LoggerFactory.getLogger(RespostasSeguranca.class);

    private RespostasSeguranca() {
    }

    /** API: 403 em JSON. */
    public static AccessDeniedHandler acessoNegadoApi() {
        return (request, response, ex) -> {
            registrarNegacao(request);
            escreverJson(response, HttpStatus.FORBIDDEN, "Acesso negado: seu perfil não permite esta operação.");
        };
    }

    /** API: 401 em JSON quando não há credenciais válidas. */
    public static AuthenticationEntryPoint naoAutenticadoApi() {
        return (request, response, ex) -> {
            log.warn("Acesso nao autenticado: metodo={} url={}", request.getMethod(), request.getRequestURI());
            response.setHeader("WWW-Authenticate", "Basic realm=\"pediupatrao\"");
            escreverJson(response, HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        };
    }

    /** Telas: 403 renderizado pela página de erro. */
    public static AccessDeniedHandler acessoNegadoWeb() {
        return (request, response, ex) -> {
            registrarNegacao(request);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Seu perfil não permite acessar este recurso.");
        };
    }

    private static void registrarNegacao(HttpServletRequest request) {
        String usuario = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonimo";
        log.warn("Acesso negado: usuario={} metodo={} url={}", usuario, request.getMethod(),
                request.getRequestURI());
    }

    private static void escreverJson(HttpServletResponse response, HttpStatus status, String mensagem)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status.value() + ",\"title\":\"" + status.getReasonPhrase()
                + "\",\"detail\":\"" + mensagem + "\"}");
    }
}
