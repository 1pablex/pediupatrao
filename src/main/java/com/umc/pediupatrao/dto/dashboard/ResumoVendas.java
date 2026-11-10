package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Indicadores gerais de vendas do período (RF01).
 *
 * @param faturamento        soma do valor total dos pedidos não cancelados
 * @param totalPedidos       todos os pedidos que entraram no período (inclui cancelados)
 * @param pedidosValidos     pedidos não cancelados
 * @param pedidosCancelados  pedidos cancelados
 * @param ticketMedio        faturamento / pedidosValidos
 * @param taxaCancelamento   pedidosCancelados / totalPedidos, em %
 * @param totalDescontos     soma dos descontos concedidos nos pedidos não cancelados
 * @param itensVendidos      soma das quantidades dos itens dos pedidos não cancelados
 * @param comparativo        mesmos indicadores no período anterior de mesma duração
 */
public record ResumoVendas(BigDecimal faturamento, long totalPedidos, long pedidosValidos, long pedidosCancelados,
        BigDecimal ticketMedio, BigDecimal taxaCancelamento, BigDecimal totalDescontos, long itensVendidos,
        Comparativo comparativo) {

    /**
     * Variações em % em relação ao período anterior; nulas quando o período anterior não tem base (zero).
     */
    public record Comparativo(LocalDate dataInicio, LocalDate dataFim, BigDecimal faturamento, long pedidosValidos,
            BigDecimal ticketMedio, BigDecimal variacaoFaturamento, BigDecimal variacaoPedidos,
            BigDecimal variacaoTicketMedio) {
    }
}
