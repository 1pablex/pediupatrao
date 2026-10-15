package com.umc.pediupatrao.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Proteção simples contra força bruta: após 5 falhas de login seguidas o usuário fica bloqueado por 5 minutos.
 * Mantido em memória (suficiente para uma única instância da aplicação).
 */
@Service
public class BloqueioLoginService {

    static final int MAXIMO_TENTATIVAS = 5;
    static final Duration TEMPO_BLOQUEIO = Duration.ofMinutes(5);

    private final Map<String, Tentativas> tentativas = new ConcurrentHashMap<>();
    private final Clock clock;

    public BloqueioLoginService(Clock clock) {
        this.clock = clock;
    }

    public void registrarFalha(String username) {
        tentativas.compute(chave(username), (k, atual) -> {
            Instant agora = clock.instant();
            if (atual == null || atual.expirou(agora)) {
                return new Tentativas(1, agora);
            }
            return new Tentativas(atual.quantidade() + 1, agora);
        });
    }

    public void registrarSucesso(String username) {
        tentativas.remove(chave(username));
    }

    public boolean isBloqueado(String username) {
        Tentativas atual = tentativas.get(chave(username));
        return atual != null && atual.quantidade() >= MAXIMO_TENTATIVAS && !atual.expirou(clock.instant());
    }

    private static String chave(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private record Tentativas(int quantidade, Instant ultimaFalha) {

        boolean expirou(Instant agora) {
            return ultimaFalha.plus(TEMPO_BLOQUEIO).isBefore(agora);
        }
    }
}
