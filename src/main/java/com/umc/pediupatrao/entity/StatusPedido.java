package com.umc.pediupatrao.entity;

import java.util.List;

/**
 * Fluxo obrigatório do pedido:
 * RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA | RETIRADO -> FINALIZADO.
 * CANCELADO é um estado excepcional, alcançado apenas pela operação de cancelamento.
 */
public enum StatusPedido {

    RECEBIDO("Recebido", "bg-secondary"),
    EM_PREPARACAO("Em preparação", "bg-warning text-dark"),
    PRONTO("Pronto", "bg-info text-dark"),
    SAIU_PARA_ENTREGA("Saiu para entrega", "bg-primary"),
    RETIRADO("Retirado", "bg-primary"),
    FINALIZADO("Finalizado", "bg-success"),
    CANCELADO("Cancelado", "bg-danger");

    private final String descricao;
    private final String corBadge;

    StatusPedido(String descricao, String corBadge) {
        this.descricao = descricao;
        this.corBadge = corBadge;
    }

    /**
     * Próximos status permitidos no fluxo normal. A saída depende do tipo do pedido:
     * pedidos de entrega saem para entrega e pedidos de retirada são retirados no balcão.
     */
    public List<StatusPedido> proximosPermitidos(TipoEntrega tipoEntrega) {
        return switch (this) {
            case RECEBIDO -> List.of(EM_PREPARACAO);
            case EM_PREPARACAO -> List.of(PRONTO);
            case PRONTO -> List.of(tipoEntrega == TipoEntrega.RETIRADA ? RETIRADO : SAIU_PARA_ENTREGA);
            case SAIU_PARA_ENTREGA, RETIRADO -> List.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> List.of();
        };
    }

    public boolean podeAvancarPara(StatusPedido novoStatus, TipoEntrega tipoEntrega) {
        return proximosPermitidos(tipoEntrega).contains(novoStatus);
    }

    /** Status que representam a saída do pedido do estabelecimento. */
    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    /** Cancelamento e desconto só são possíveis enquanto o pedido não saiu. */
    public boolean isAntesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCorBadge() {
        return corBadge;
    }
}
