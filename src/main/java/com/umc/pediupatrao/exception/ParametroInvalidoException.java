package com.umc.pediupatrao.exception;

/**
 * Parâmetro de consulta inválido (ex.: período com data inicial maior que a final).
 * Respondido como HTTP 400 na API.
 */
public class ParametroInvalidoException extends RuntimeException {

    private final String parametro;

    public ParametroInvalidoException(String parametro, String message) {
        super(message);
        this.parametro = parametro;
    }

    public String getParametro() {
        return parametro;
    }
}
