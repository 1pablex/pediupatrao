package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatusPedidoTest {

    @Test
    void fluxoDeEntregaSegueAsEtapasObrigatorias() {
        assertThat(StatusPedido.RECEBIDO.proximosPermitidos(TipoEntrega.ENTREGA))
                .containsExactly(StatusPedido.EM_PREPARACAO);
        assertThat(StatusPedido.EM_PREPARACAO.proximosPermitidos(TipoEntrega.ENTREGA))
                .containsExactly(StatusPedido.PRONTO);
        assertThat(StatusPedido.PRONTO.proximosPermitidos(TipoEntrega.ENTREGA))
                .containsExactly(StatusPedido.SAIU_PARA_ENTREGA);
        assertThat(StatusPedido.SAIU_PARA_ENTREGA.proximosPermitidos(TipoEntrega.ENTREGA))
                .containsExactly(StatusPedido.FINALIZADO);
    }

    @Test
    void pedidoDeRetiradaSaiComoRetirado() {
        assertThat(StatusPedido.PRONTO.proximosPermitidos(TipoEntrega.RETIRADA))
                .containsExactly(StatusPedido.RETIRADO);
        assertThat(StatusPedido.RETIRADO.proximosPermitidos(TipoEntrega.RETIRADA))
                .containsExactly(StatusPedido.FINALIZADO);
    }

    @Test
    void naoPermitePularEtapas() {
        assertThat(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA)).isFalse();
        assertThat(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.PRONTO, TipoEntrega.ENTREGA)).isFalse();
        assertThat(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.RETIRADO, TipoEntrega.ENTREGA)).isFalse();
    }

    @Test
    void estadosFinaisNaoPossuemTransicoes() {
        assertThat(StatusPedido.FINALIZADO.proximosPermitidos(TipoEntrega.ENTREGA)).isEmpty();
        assertThat(StatusPedido.CANCELADO.proximosPermitidos(TipoEntrega.ENTREGA)).isEmpty();
    }

    @Test
    void cancelamentoEDescontoSoAntesDaSaida() {
        assertThat(StatusPedido.RECEBIDO.isAntesDaSaida()).isTrue();
        assertThat(StatusPedido.EM_PREPARACAO.isAntesDaSaida()).isTrue();
        assertThat(StatusPedido.PRONTO.isAntesDaSaida()).isTrue();
        assertThat(StatusPedido.SAIU_PARA_ENTREGA.isAntesDaSaida()).isFalse();
        assertThat(StatusPedido.RETIRADO.isAntesDaSaida()).isFalse();
        assertThat(StatusPedido.FINALIZADO.isAntesDaSaida()).isFalse();
        assertThat(StatusPedido.CANCELADO.isAntesDaSaida()).isFalse();
    }
}
