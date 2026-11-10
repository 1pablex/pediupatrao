package com.umc.pediupatrao.dto.dashboard;

/** Granularidade da série temporal de vendas. A semana começa na segunda-feira. */
public enum Agrupamento {

    DIA("Dia", "day"),
    SEMANA("Semana", "week"),
    MES("Mês", "month");

    private final String descricao;
    private final String unidadeMongo;

    Agrupamento(String descricao, String unidadeMongo) {
        this.descricao = descricao;
        this.unidadeMongo = unidadeMongo;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Unidade usada no operador $dateTrunc do MongoDB. */
    public String unidadeMongo() {
        return unidadeMongo;
    }
}
