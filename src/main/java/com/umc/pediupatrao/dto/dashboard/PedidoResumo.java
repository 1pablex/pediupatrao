package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Linha da tabela de pedidos do dashboard: só os dados necessários para o acompanhamento. */
public record PedidoResumo(String id, LocalDateTime dataHoraEntrada, String nomeCliente, TipoEntrega tipoEntrega,
        StatusPedido status, String statusDescricao, BigDecimal valorTotal, int quantidadeItens,
        String responsavelEntrada, LocalDateTime dataHoraSaida) {

    public static PedidoResumo de(Pedido pedido) {
        StatusPedido status = pedido.getStatus();
        return new PedidoResumo(pedido.getId(), pedido.getDataHoraEntrada(), pedido.getNomeCliente(),
                pedido.getTipoEntrega(), status, status == null ? null : status.getDescricao(),
                pedido.getValorTotal(), pedido.getItens() == null ? 0 : pedido.getQuantidadeTotalItens(),
                pedido.getResponsavelEntrada(), pedido.getDataHoraSaida());
    }
}
