package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * Valores aceitos pelos filtros do dashboard, para que a tela monte os campos sem valores fixos no código.
 */
public record OpcoesFiltro(List<Opcao> status, List<Opcao> tiposEntrega, List<String> categorias,
        List<Opcao> agrupamentos, List<Opcao> criteriosRanking, LocalDate dataInicioPadrao,
        LocalDate dataFimPadrao, int maximoDiasPeriodo, int maximoTamanhoPagina, int maximoLimiteRanking) {

    public record Opcao(String valor, String descricao) {
    }
}
