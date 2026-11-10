package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/** Pedidos não cancelados por hora do dia (0 a 23), para identificar horários de pico. */
public record VendaPorHora(int hora, long pedidos, BigDecimal faturamento) {
}
