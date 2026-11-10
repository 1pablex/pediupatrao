package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/**
 * Vendas por categoria de produto (Pizza, Bebida...).
 *
 * @param faturamento  valor dos itens já com o desconto do pedido aplicado proporcionalmente
 * @param participacao percentual do faturamento da categoria sobre o total de itens
 */
public record VendaPorCategoria(String categoria, long quantidade, BigDecimal faturamento,
        BigDecimal participacao) {
}
