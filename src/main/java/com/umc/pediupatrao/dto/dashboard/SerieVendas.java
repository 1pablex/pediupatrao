package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Série temporal de vendas (RF02). Todo intervalo do período aparece, mesmo sem vendas (valores zerados),
 * para que o gráfico não "pule" dias. Cada ponto traz também o intervalo correspondente do período anterior
 * (mesma posição na série), para comparar as duas linhas no gráfico.
 */
public record SerieVendas(Agrupamento agrupamento, List<Ponto> pontos) {

    /**
     * @param periodo              data de início do intervalo (yyyy-MM-dd)
     * @param rotulo               rótulo curto para o eixo (dd/MM ou MM/yyyy)
     * @param faturamento          soma dos pedidos não cancelados
     * @param pedidos              quantidade de pedidos não cancelados
     * @param cancelados           quantidade de pedidos cancelados
     * @param ticketMedio          faturamento / pedidos
     * @param descontos            descontos concedidos nos pedidos não cancelados
     * @param periodoAnterior      início do intervalo correspondente no período anterior (nulo se não houver)
     * @param faturamentoAnterior  faturamento desse intervalo anterior
     * @param pedidosAnterior      pedidos desse intervalo anterior
     * @param ticketMedioAnterior  ticket médio desse intervalo anterior
     */
    public record Ponto(String periodo, String rotulo, BigDecimal faturamento, long pedidos, long cancelados,
            BigDecimal ticketMedio, BigDecimal descontos, String periodoAnterior, BigDecimal faturamentoAnterior,
            long pedidosAnterior, BigDecimal ticketMedioAnterior) {
    }
}
