package com.umc.pediupatrao.exception;

/**
 * Violação de uma regra de negócio (transição inválida, desconto acima do limite etc.).
 * Respondida como HTTP 422 na API e como mensagem de erro nas telas.
 */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String message) {
        super(message);
    }
}
