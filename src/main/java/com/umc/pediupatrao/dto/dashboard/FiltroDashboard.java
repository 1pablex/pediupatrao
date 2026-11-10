package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Filtros comuns a todos os endpoints do dashboard (RF05).
 * O período é fechado nas duas pontas: de dataInicio 00:00 até dataFim 23:59:59.
 *
 * @param dataInicio  primeiro dia do período (inclusive)
 * @param dataFim     último dia do período (inclusive)
 * @param status      status a considerar; vazio = todos
 * @param tipoEntrega ENTREGA ou RETIRADA; nulo = ambos
 */
public record FiltroDashboard(LocalDate dataInicio, LocalDate dataFim, List<StatusPedido> status,
        TipoEntrega tipoEntrega) {

    public static final int DIAS_PADRAO = 30;
    public static final int MAXIMO_DIAS = 366;

    public FiltroDashboard {
        status = status == null ? List.of() : status.stream().distinct().toList();
    }

    /**
     * Monta e valida o filtro a partir dos parâmetros da requisição.
     * Sem datas, usa os últimos 30 dias até hoje; só com uma das datas, completa a outra.
     */
    public static FiltroDashboard de(LocalDate dataInicio, LocalDate dataFim, List<StatusPedido> status,
            TipoEntrega tipoEntrega, LocalDate hoje) {
        LocalDate fim = dataFim != null ? dataFim
                : dataInicio != null ? dataInicio.plusDays(DIAS_PADRAO - 1L) : hoje;
        LocalDate inicio = dataInicio != null ? dataInicio : fim.minusDays(DIAS_PADRAO - 1L);

        if (inicio.isAfter(fim)) {
            throw new ParametroInvalidoException("dataInicio", "dataInicio deve ser anterior ou igual a dataFim.");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > MAXIMO_DIAS) {
            throw new ParametroInvalidoException("dataFim",
                    "O período pode ter no máximo " + MAXIMO_DIAS + " dias (informado: " + dias + ").");
        }
        return new FiltroDashboard(inicio, fim, status, tipoEntrega);
    }

    public long quantidadeDias() {
        return ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
    }

    /** Período imediatamente anterior, com a mesma duração e os mesmos filtros (usado no comparativo). */
    public FiltroDashboard periodoAnterior() {
        long dias = quantidadeDias();
        return new FiltroDashboard(dataInicio.minusDays(dias), dataInicio.minusDays(1), status, tipoEntrega);
    }

    public LocalDateTime inicio() {
        return dataInicio.atStartOfDay();
    }

    /** Limite superior exclusivo: início do dia seguinte a dataFim. */
    public LocalDateTime fimExclusivo() {
        return dataFim.plusDays(1).atStartOfDay();
    }
}
