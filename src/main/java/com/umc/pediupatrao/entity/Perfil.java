package com.umc.pediupatrao.entity;

/**
 * Perfis de acesso do sistema. O nome do enum vira a role do Spring Security (ROLE_ADMIN, ROLE_GERENTE...).
 */
public enum Perfil {

    ADMIN("Administrador"),
    GERENTE("Gerente"),
    ATENDENTE("Atendente");

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
