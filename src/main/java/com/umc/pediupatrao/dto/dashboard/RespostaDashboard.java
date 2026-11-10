package com.umc.pediupatrao.dto.dashboard;

import java.time.OffsetDateTime;

/**
 * Envelope padrão das respostas do dashboard: informa os filtros efetivamente aplicados
 * (já com os valores padrão) e o instante em que os dados foram calculados (RF07).
 */
public record RespostaDashboard<T>(FiltroDashboard filtros, OffsetDateTime geradoEm, T dados) {
}
