package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

/**
 * Registro de cada mudança de status do pedido (quem fez e quando).
 */
public class HistoricoStatus {

    private StatusPedido status;
    private LocalDateTime dataHora;
    private String usuario;

    public HistoricoStatus() {
    }

    public HistoricoStatus(StatusPedido status, LocalDateTime dataHora, String usuario) {
        this.status = status;
        this.dataHora = dataHora;
        this.usuario = usuario;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }
}
