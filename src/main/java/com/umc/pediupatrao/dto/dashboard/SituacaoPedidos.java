package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.util.List;

/**
 * Situação dos pedidos do período (RF03).
 *
 * @param porStatus                 todos os status, inclusive os que não têm pedidos (quantidade 0)
 * @param emAndamento               RECEBIDO + EM_PREPARACAO + PRONTO + SAIU_PARA_ENTREGA
 * @param tempoMedioAteSaidaMinutos média entre a entrada e a saída (entrega/retirada); nulo sem saídas
 */
public record SituacaoPedidos(long total, long emAndamento, long finalizados, long cancelados,
        BigDecimal tempoMedioAteSaidaMinutos, List<Contagem> porStatus) {

    public record Contagem(StatusPedido status, String descricao, long quantidade, BigDecimal valor,
            BigDecimal percentual) {
    }
}
