package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Série temporal de vendas (RF02). Todo intervalo do período aparece, mesmo sem vendas (valores zerados),
 * para que o gráfico não "pule" dias.
 */
public record SerieVendas(Agrupamento agrupamento, List<Ponto> pontos) {

    /**
     * @param periodo     data de início do intervalo (yyyy-MM-dd)
     * @param rotulo      rótulo curto para o eixo (dd/MM ou MM/yyyy)
     * @param faturamento soma dos pedidos não cancelados
     * @param pedidos     quantidade de pedidos não cancelados
     * @param cancelados  quantidade de pedidos cancelados
     * @param ticketMedio faturamento / pedidos
     */
    public record Ponto(String periodo, String rotulo, BigDecimal faturamento, long pedidos, long cancelados,
            BigDecimal ticketMedio) {
    }
}
