package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/**
 * Uma célula do mapa de calor: pedidos não cancelados num dia da semana e hora.
 *
 * @param diaSemana 1 = segunda-feira ... 7 = domingo (ISO 8601)
 * @param dia       abreviação do dia (Seg, Ter...)
 * @param hora      0 a 23
 */
public record VendaPorDiaHora(int diaSemana, String dia, int hora, long pedidos, BigDecimal faturamento) {
}
