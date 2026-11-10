package com.umc.pediupatrao.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DemoDataInitializerTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 20, 30);

    @Test
    void geraPedidosCoerentesNos90DiasAteAgora() {
        List<Pedido> pedidos = new DemoDataInitializer.Gerador(new Random(1), produtos(), clientes(), AGORA).gerar();

        assertThat(pedidos).hasSizeGreaterThan(500);
        assertThat(pedidos).allSatisfy(p -> {
            assertThat(p.getDataHoraEntrada()).isBefore(AGORA).isAfter(AGORA.minusDays(DemoDataInitializer.DIAS));
            BigDecimal somaItens = p.getItens().stream().map(ItemPedido::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(p.getSubtotal()).isEqualByComparingTo(somaItens);
            assertThat(p.getValorTotal()).isEqualByComparingTo(p.getSubtotal().subtract(p.getValorDesconto()));
            assertThat(p.getPercentualDesconto()).isBetween(BigDecimal.ZERO, new BigDecimal("20"));
            assertThat(p.getItens()).allSatisfy(i -> assertThat(i.getCategoria()).isNotBlank());
            if (p.getStatus().isSaida() || p.getStatus() == StatusPedido.FINALIZADO) {
                assertThat(p.getDataHoraSaida()).isAfter(p.getDataHoraEntrada());
            }
        });
        assertThat(pedidos).anyMatch(p -> p.getStatus() == StatusPedido.CANCELADO);
        assertThat(pedidos).anyMatch(p -> p.getStatus().isAntesDaSaida());
    }

    @Test
    void mesmaSementeGeraOsMesmosDados() {
        List<Pedido> a = new DemoDataInitializer.Gerador(new Random(7), produtos(), clientes(), AGORA).gerar();
        List<Pedido> b = new DemoDataInitializer.Gerador(new Random(7), produtos(), clientes(), AGORA).gerar();

        assertThat(a).hasSameSizeAs(b);
        assertThat(a.get(a.size() / 2).getValorTotal()).isEqualByComparingTo(b.get(b.size() / 2).getValorTotal());
    }

    private static List<Produto> produtos() {
        return List.of(produto("1", "Pizza Calabresa", "Pizza", "52.90"),
                produto("2", "Refrigerante 2L", "Bebida", "14.00"),
                produto("3", "X-Burguer", "Lanche", "24.90"));
    }

    private static Produto produto(String id, String nome, String tipo, String preco) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(new BigDecimal(preco));
        p.setAtivo(true);
        return p;
    }

    private static List<Cliente> clientes() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Cliente Demo");
        return List.of(c);
    }
}
