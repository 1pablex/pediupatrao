package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Produtos mais vendidos (RF04), considerando apenas pedidos não cancelados.
 *
 * @param categoria          categoria filtrada (nula = todas)
 * @param faturamentoTotal   faturamento de todos os itens do período (base da participação)
 * @param quantidadeTotal    quantidade de todos os itens do período
 */
public record RankingProdutos(CriterioRanking ordenarPor, String categoria, int limite,
        BigDecimal faturamentoTotal, long quantidadeTotal, List<Item> produtos) {

    /**
     * @param pedidos      em quantos pedidos o produto apareceu
     * @param participacao percentual sobre o faturamento total dos itens
     */
    public record Item(int posicao, String produtoId, String nome, String categoria, long quantidade,
            BigDecimal faturamento, long pedidos, BigDecimal participacao) {
    }
}
