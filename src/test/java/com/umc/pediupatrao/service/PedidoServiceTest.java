package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ItemPedidoForm;
import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.ContextoUsuario;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"),
            ZoneId.of("America/Sao_Paulo"));
    private static final LocalDateTime AGORA = LocalDateTime.now(RELOGIO);

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ContextoUsuario contextoUsuario;

    private PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(pedidoRepository, produtoRepository, clienteRepository, auditoriaService,
                contextoUsuario, RELOGIO);
        lenient().when(contextoUsuario.atual()).thenReturn(new UsuarioLogado("gerente01", "GERENTE"));
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("p1");
            }
            return p;
        });
    }

    // ---------------------------------------------------------------- criação

    @Test
    void criarPedidoCalculaTotalNoServidorComPrecosDoCadastro() {
        when(contextoUsuario.atual()).thenReturn(new UsuarioLogado("atendente01", "ATENDENTE"));
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("pizza")).thenReturn(Optional.of(produto("pizza", "Pizza Calabresa", "52.90", true)));
        when(produtoRepository.findById("refri")).thenReturn(Optional.of(produto("refri", "Refrigerante 2L", "14.00", true)));

        Pedido pedido = service.criarPedido(form(new ItemPedidoForm("pizza", 2), new ItemPedidoForm("refri", 1)));

        assertThat(pedido.getSubtotal()).isEqualByComparingTo("119.80");
        assertThat(pedido.getValorDesconto()).isEqualByComparingTo("0");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("119.80");
        assertThat(pedido.getItens()).extracting(ItemPedido::getNomeProduto)
                .containsExactly("Pizza Calabresa", "Refrigerante 2L");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.RECEBIDO);
        assertThat(pedido.getDataHoraEntrada()).isEqualTo(AGORA);
        assertThat(pedido.getResponsavelEntrada()).isEqualTo("atendente01");
        assertThat(pedido.getNomeCliente()).isEqualTo("João da Silva");
        assertThat(pedido.getHistorico()).hasSize(1);
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_PEDIDO), eq(EntidadeAuditada.PEDIDO), eq("p1"),
                any(Alteracoes.class));
    }

    @Test
    void criarPedidoRejeitaProdutoInativo() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("velho")).thenReturn(Optional.of(produto("velho", "Pizza Antiga", "40.00", false)));

        assertThatThrownBy(() -> service.criarPedido(form(new ItemPedidoForm("velho", 1))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("inativo");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void criarPedidoRejeitaClienteInexistente() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarPedido(form(new ItemPedidoForm("pizza", 1))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("cliente");
    }

    @Test
    void criarPedidoRejeitaPedidoSemItens() {
        assertThatThrownBy(() -> service.criarPedido(form()))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void criarPedidoRejeitaQuantidadeForaDoLimite() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));

        assertThatThrownBy(() -> service.criarPedido(form(new ItemPedidoForm("pizza", 0))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("quantidade");
    }

    // ---------------------------------------------------------------- transições

    @Test
    void percorreFluxoCompletoDeEntregaRegistrandoSaidaEFinalizacao() {
        Pedido pedido = pedidoNoStatus(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));
        when(contextoUsuario.atual()).thenReturn(new UsuarioLogado("atendente01", "ATENDENTE"));

        service.avancarStatus("p1", StatusPedido.EM_PREPARACAO);
        service.avancarStatus("p1", StatusPedido.PRONTO);
        assertThat(pedido.getDataHoraSaida()).isNull();

        service.avancarStatus("p1", StatusPedido.SAIU_PARA_ENTREGA);
        assertThat(pedido.getDataHoraSaida()).isEqualTo(AGORA);
        assertThat(pedido.getResponsavelSaida()).isEqualTo("atendente01");

        service.avancarStatus("p1", StatusPedido.FINALIZADO);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.FINALIZADO);
        assertThat(pedido.getResponsavelFinalizacao()).isEqualTo("atendente01");
        assertThat(pedido.getHistorico()).extracting(h -> h.getStatus()).containsExactly(
                StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO,
                StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.FINALIZADO);
    }

    @Test
    void saidaDePedidoDeRetiradaFicaRegistradaNaAuditoria() {
        Pedido pedido = pedidoNoStatus(StatusPedido.PRONTO, TipoEntrega.RETIRADA);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));

        service.avancarStatus("p1", StatusPedido.RETIRADO);

        ArgumentCaptor<Alteracoes> captor = ArgumentCaptor.forClass(Alteracoes.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_STATUS_PEDIDO), eq(EntidadeAuditada.PEDIDO),
                eq("p1"), captor.capture());
        assertThat(captor.getValue().lista()).extracting(AlteracaoCampo::getCampo)
                .contains("status", "dataHoraSaida", "responsavelSaida");
        assertThat(captor.getValue().lista().get(0).getValorAnterior()).isEqualTo("PRONTO");
        assertThat(captor.getValue().lista().get(0).getValorNovo()).isEqualTo("RETIRADO");
    }

    @Test
    void naoPermiteFinalizarPedidoEmPreparacao() {
        Pedido pedido = pedidoNoStatus(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> service.avancarStatus("p1", StatusPedido.FINALIZADO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Transição inválida");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.EM_PREPARACAO);
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void pedidoDeRetiradaNaoPodeSairParaEntrega() {
        when(pedidoRepository.findById("p1"))
                .thenReturn(Optional.of(pedidoNoStatus(StatusPedido.PRONTO, TipoEntrega.RETIRADA)));

        assertThatThrownBy(() -> service.avancarStatus("p1", StatusPedido.SAIU_PARA_ENTREGA))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void pedidoCanceladoNaoAceitaNovoStatus() {
        when(pedidoRepository.findById("p1"))
                .thenReturn(Optional.of(pedidoNoStatus(StatusPedido.CANCELADO, TipoEntrega.ENTREGA)));

        assertThatThrownBy(() -> service.avancarStatus("p1", StatusPedido.EM_PREPARACAO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    void cancelamentoNaoPodeSerFeitoPelaTrocaDeStatus() {
        assertThatThrownBy(() -> service.avancarStatus("p1", StatusPedido.CANCELADO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("cancelamento");
        verify(pedidoRepository, never()).findById(anyString());
    }

    // ---------------------------------------------------------------- desconto

    @Test
    void descontoECalculadoNoServidorSobreOSubtotal() {
        Pedido pedido = pedidoNoStatus(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));

        service.aplicarDesconto("p1", new BigDecimal("15"));

        assertThat(pedido.getPercentualDesconto()).isEqualByComparingTo("15");
        assertThat(pedido.getValorDesconto()).isEqualByComparingTo("15.00");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("85.00");
        assertThat(pedido.getResponsavelDesconto()).isEqualTo("gerente01");

        ArgumentCaptor<Alteracoes> captor = ArgumentCaptor.forClass(Alteracoes.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.DESCONTO_PEDIDO), eq(EntidadeAuditada.PEDIDO), eq("p1"),
                captor.capture());
        assertThat(captor.getValue().lista())
                .anySatisfy(a -> {
                    assertThat(a.getCampo()).isEqualTo("valorTotal");
                    assertThat(a.getValorAnterior()).isEqualTo("100");
                    assertThat(a.getValorNovo()).isEqualTo("85");
                });
    }

    @Test
    void descontoAcimaDe20PorCentoERejeitado() {
        assertThatThrownBy(() -> service.aplicarDesconto("p1", new BigDecimal("20.01")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("20");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void descontoNegativoERejeitado() {
        assertThatThrownBy(() -> service.aplicarDesconto("p1", new BigDecimal("-1")))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void descontoDeExatamente20PorCentoEPermitido() {
        Pedido pedido = pedidoNoStatus(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));

        service.aplicarDesconto("p1", new BigDecimal("20"));

        assertThat(pedido.getValorTotal()).isEqualByComparingTo("80.00");
    }

    @Test
    void descontoNaoPodeSerAplicadoAposSaida() {
        when(pedidoRepository.findById("p1"))
                .thenReturn(Optional.of(pedidoNoStatus(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.ENTREGA)));

        assertThatThrownBy(() -> service.aplicarDesconto("p1", new BigDecimal("10")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("antes da saída");
    }

    // ---------------------------------------------------------------- cancelamento

    @Test
    void cancelamentoPreservaDadosERegistraJustificativa() {
        Pedido pedido = pedidoNoStatus(StatusPedido.PRONTO, TipoEntrega.ENTREGA);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));

        service.cancelar("p1", "  Cliente desistiu por telefone  ");

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedido.getJustificativaCancelamento()).isEqualTo("Cliente desistiu por telefone");
        assertThat(pedido.getResponsavelCancelamento()).isEqualTo("gerente01");
        assertThat(pedido.getDataHoraCancelamento()).isEqualTo(AGORA);
        assertThat(pedido.getItens()).hasSize(1);
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("100.00");
        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq(EntidadeAuditada.PEDIDO),
                eq("p1"), any(Alteracoes.class), eq("Cliente desistiu por telefone"));
    }

    @Test
    void cancelamentoExigeJustificativa() {
        assertThatThrownBy(() -> service.cancelar("p1", "   "))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("justificativa");
        assertThatThrownBy(() -> service.cancelar("p1", "curta"))
                .isInstanceOf(RegraNegocioException.class);
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void naoCancelaPedidoQueJaSaiu() {
        when(pedidoRepository.findById("p1"))
                .thenReturn(Optional.of(pedidoNoStatus(StatusPedido.RETIRADO, TipoEntrega.RETIRADA)));

        assertThatThrownBy(() -> service.cancelar("p1", "Cliente reclamou do atraso"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("saiu");
    }

    @Test
    void naoCancelaPedidoJaCancelado() {
        when(pedidoRepository.findById("p1"))
                .thenReturn(Optional.of(pedidoNoStatus(StatusPedido.CANCELADO, TipoEntrega.ENTREGA)));

        assertThatThrownBy(() -> service.cancelar("p1", "Cancelando novamente"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já está cancelado");
    }

    // ---------------------------------------------------------------- apoio

    private static PedidoForm form(ItemPedidoForm... itens) {
        PedidoForm form = new PedidoForm();
        form.setClienteId("c1");
        form.setTipoEntrega(TipoEntrega.ENTREGA);
        form.setItens(new java.util.ArrayList<>(List.of(itens)));
        return form;
    }

    private static Cliente cliente() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("João da Silva");
        return c;
    }

    private static Produto produto(String id, String nome, String preco, boolean ativo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(new BigDecimal(preco));
        p.setAtivo(ativo);
        return p;
    }

    /** Pedido com um item de R$ 100,00. */
    private static Pedido pedidoNoStatus(StatusPedido status, TipoEntrega tipo) {
        Pedido pedido = new Pedido();
        pedido.setId("p1");
        pedido.setTipoEntrega(tipo);
        pedido.setItens(new java.util.ArrayList<>(List.of(new ItemPedido("pizza", "Pizza", new BigDecimal("50.00"), 2))));
        PedidoService.recalcularValores(pedido);
        pedido.setStatus(status);
        pedido.getHistorico().add(new com.umc.pediupatrao.entity.HistoricoStatus(StatusPedido.RECEBIDO, AGORA, "atendente01"));
        return pedido;
    }
}
