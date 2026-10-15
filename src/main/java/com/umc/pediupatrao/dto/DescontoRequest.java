package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DescontoRequest(
        @NotNull(message = "Informe o percentual de desconto.")
        @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo.")
        @DecimalMax(value = "20.00", message = "O desconto é limitado a 20%.")
        BigDecimal percentual) {
}
