package com.umc.pediupatrao.dto.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FiltroDashboardTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    @Test
    void semDatasUsaOsUltimos30DiasAteHoje() {
        FiltroDashboard f = FiltroDashboard.de(null, null, null, null, HOJE);

        assertThat(f.dataFim()).isEqualTo(HOJE);
        assertThat(f.dataInicio()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(f.quantidadeDias()).isEqualTo(30);
        assertThat(f.status()).isEmpty();
    }

    @Test
    void comApenasDataInicioCompletaTrintaDias() {
        FiltroDashboard f = FiltroDashboard.de(LocalDate.of(2026, 1, 1), null, null, null, HOJE);

        assertThat(f.dataFim()).isEqualTo(LocalDate.of(2026, 1, 30));
    }

    @Test
    void periodoCobreDoInicioDoPrimeiroDiaAoFimDoUltimo() {
        FiltroDashboard f = FiltroDashboard.de(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), null, null, HOJE);

        assertThat(f.inicio()).isEqualTo(LocalDate.of(2026, 11, 1).atStartOfDay());
        assertThat(f.fimExclusivo()).isEqualTo(LocalDate.of(2026, 11, 2).atStartOfDay());
    }

    @Test
    void dataInicialDepoisDaFinalERejeitada() {
        assertThatThrownBy(() -> FiltroDashboard.de(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), null,
                null, HOJE))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("dataInicio")
                .extracting("parametro").isEqualTo("dataInicio");
    }

    @Test
    void periodoMaiorQue366DiasERejeitado() {
        assertThatThrownBy(() -> FiltroDashboard.de(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2), null, null,
                HOJE))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("366");
    }

    @Test
    void periodoDeExatamente366DiasEAceito() {
        FiltroDashboard f = FiltroDashboard.de(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null, null,
                HOJE);

        assertThat(f.quantidadeDias()).isEqualTo(366);
    }

    @Test
    void periodoAnteriorTemAMesmaDuracaoEImediatamenteAntes() {
        FiltroDashboard f = FiltroDashboard.de(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7),
                List.of(StatusPedido.FINALIZADO), null, HOJE);

        FiltroDashboard anterior = f.periodoAnterior();

        assertThat(anterior.dataInicio()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(anterior.dataFim()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(anterior.status()).containsExactly(StatusPedido.FINALIZADO);
    }

    @Test
    void statusRepetidosSaoIgnorados() {
        FiltroDashboard f = FiltroDashboard.de(null, null,
                List.of(StatusPedido.PRONTO, StatusPedido.PRONTO, StatusPedido.RECEBIDO), null, HOJE);

        assertThat(f.status()).containsExactly(StatusPedido.PRONTO, StatusPedido.RECEBIDO);
    }
}
