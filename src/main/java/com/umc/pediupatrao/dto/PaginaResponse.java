package com.umc.pediupatrao.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PaginaResponse<T>(List<T> conteudo, int pagina, int tamanho, int totalPaginas, long totalElementos) {

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalPages(),
                page.getTotalElements());
    }
}
