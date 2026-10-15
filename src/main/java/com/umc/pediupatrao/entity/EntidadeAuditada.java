package com.umc.pediupatrao.entity;

public enum EntidadeAuditada {

    PEDIDO("Pedido"),
    CLIENTE("Cliente"),
    PRODUTO("Produto"),
    USUARIO("Usuário");

    private final String descricao;

    EntidadeAuditada(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
