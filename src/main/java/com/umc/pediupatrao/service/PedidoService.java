package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemPedidoForm;
import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.ContextoUsuario;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Regras de negócio dos pedidos.
 * - Valores (subtotal, desconto, total) são sempre calculados aqui, a partir do cadastro de produtos.
 * - O fluxo de status segue StatusPedido; transições fora do fluxo são rejeitadas.
 * - Desconto e cancelamento são exclusivos do GERENTE e só valem antes da saída do pedido.
 * - Toda inclusão e alteração gera um registro de auditoria.
 */
@Service
public class PedidoService {

    static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");
    static final int JUSTIFICATIVA_MINIMA = 10;
    static final int JUSTIFICATIVA_MAXIMA = 500;

    private static final BigDecimal CEM = new BigDecimal("100");

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;
    private final ContextoUsuario contextoUsuario;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
            ClienteRepository clienteRepository, AuditoriaService auditoriaService,
            ContextoUsuario contextoUsuario, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
        this.contextoUsuario = contextoUsuario;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAllByOrderByDataHoraEntradaDesc();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    // ------------------------------------------------------------------
    // Entrada (RECEBIDO)
    // ------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(PedidoForm form) {
        if (form.getItens() == null || form.getItens().isEmpty()) {
            throw new RegraNegocioException("Adicione pelo menos um item ao pedido.");
        }
        if (form.getTipoEntrega() == null) {
            throw new RegraNegocioException("Selecione entrega ou retirada.");
        }
        if (form.getClienteId() == null || form.getClienteId().isBlank()) {
            throw new RegraNegocioException("Selecione o cliente.");
        }
        Cliente cliente = clienteRepository.findById(form.getClienteId())
                .orElseThrow(() -> new RegraNegocioException("O cliente informado não existe."));

        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedidoForm itemForm : form.getItens()) {
            itens.add(montarItem(itemForm));
        }

        String usuario = contextoUsuario.atual().username();
        LocalDateTime agora = agora();

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setNomeCliente(cliente.getNome());
        pedido.setTipoEntrega(form.getTipoEntrega());
        pedido.setObservacao(textoOuNulo(form.getObservacao()));
        pedido.setItens(itens);
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        recalcularValores(pedido);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(agora);
        pedido.setResponsavelEntrada(usuario);
        pedido.getHistorico().add(new HistoricoStatus(StatusPedido.RECEBIDO, agora, usuario));

        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.INCLUSAO_PEDIDO, EntidadeAuditada.PEDIDO, salvo.getId(),
                new Alteracoes()
                        .registrar("cliente", null, salvo.getNomeCliente())
                        .registrar("tipoEntrega", null, salvo.getTipoEntrega())
                        .registrar("itens", null, descreverItens(salvo.getItens()))
                        .registrar("valorTotal", null, salvo.getValorTotal())
                        .registrar("status", null, salvo.getStatus())
                        .registrar("dataHoraEntrada", null, salvo.getDataHoraEntrada())
                        .registrar("responsavelEntrada", null, salvo.getResponsavelEntrada()));
        return salvo;
    }

    // ------------------------------------------------------------------
    // Transições de status (preparação, pronto, saída, finalização)
    // ------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido avancarStatus(String id, StatusPedido novoStatus) {
        if (novoStatus == null) {
            throw new RegraNegocioException("Informe o novo status.");
        }
        if (novoStatus == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Para cancelar, use a operação de cancelamento (exclusiva do gerente).");
        }
        Pedido pedido = buscarPorId(id);
        exigirNaoCancelado(pedido);

        StatusPedido atual = pedido.getStatus();
        if (!atual.podeAvancarPara(novoStatus, pedido.getTipoEntrega())) {
            List<StatusPedido> permitidos = atual.proximosPermitidos(pedido.getTipoEntrega());
            String sugestao = permitidos.isEmpty()
                    ? "O pedido já foi finalizado."
                    : "Próximo status permitido: " + permitidos.stream()
                            .map(StatusPedido::getDescricao).collect(Collectors.joining(", ")) + ".";
            throw new RegraNegocioException("Transição inválida: " + atual.getDescricao() + " → "
                    + novoStatus.getDescricao() + ". " + sugestao);
        }

        String usuario = contextoUsuario.atual().username();
        LocalDateTime agora = agora();
        Alteracoes alteracoes = new Alteracoes().comparar("status", atual, novoStatus);

        pedido.setStatus(novoStatus);
        if (novoStatus.isSaida()) {
            pedido.setDataHoraSaida(agora);
            pedido.setResponsavelSaida(usuario);
            alteracoes.registrar("dataHoraSaida", null, agora).registrar("responsavelSaida", null, usuario);
        }
        if (novoStatus == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(agora);
            pedido.setResponsavelFinalizacao(usuario);
            alteracoes.registrar("dataHoraFinalizacao", null, agora)
                    .registrar("responsavelFinalizacao", null, usuario);
        }
        pedido.getHistorico().add(new HistoricoStatus(novoStatus, agora, usuario));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_STATUS_PEDIDO, EntidadeAuditada.PEDIDO, salvo.getId(),
                alteracoes);
        return salvo;
    }

    // ------------------------------------------------------------------
    // Operações exclusivas do GERENTE
    // ------------------------------------------------------------------

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        if (percentual == null) {
            throw new RegraNegocioException("Informe o percentual de desconto.");
        }
        if (percentual.signum() < 0 || percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraNegocioException("O desconto deve estar entre 0% e " + DESCONTO_MAXIMO + "%.");
        }
        if (percentual.stripTrailingZeros().scale() > 2) {
            throw new RegraNegocioException("Use no máximo duas casas decimais no percentual.");
        }
        Pedido pedido = buscarPorId(id);
        exigirNaoCancelado(pedido);
        if (!pedido.getStatus().isAntesDaSaida()) {
            throw new RegraNegocioException("O desconto só pode ser aplicado antes da saída para entrega ou retirada.");
        }
        if (percentual.compareTo(pedido.getPercentualDesconto()) == 0) {
            throw new RegraNegocioException("Este percentual de desconto já está aplicado ao pedido.");
        }

        BigDecimal percentualAnterior = pedido.getPercentualDesconto();
        BigDecimal descontoAnterior = pedido.getValorDesconto();
        BigDecimal totalAnterior = pedido.getValorTotal();

        pedido.setPercentualDesconto(percentual);
        recalcularValores(pedido);
        pedido.setDataHoraDesconto(agora());
        pedido.setResponsavelDesconto(contextoUsuario.atual().username());

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.DESCONTO_PEDIDO, EntidadeAuditada.PEDIDO, salvo.getId(),
                new Alteracoes()
                        .comparar("percentualDesconto", percentualAnterior, salvo.getPercentualDesconto())
                        .comparar("valorDesconto", descontoAnterior, salvo.getValorDesconto())
                        .comparar("valorTotal", totalAnterior, salvo.getValorTotal()));
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        String motivo = textoOuNulo(justificativa);
        if (motivo == null || motivo.length() < JUSTIFICATIVA_MINIMA) {
            throw new RegraNegocioException("Informe uma justificativa com pelo menos " + JUSTIFICATIVA_MINIMA
                    + " caracteres para cancelar o pedido.");
        }
        if (motivo.length() > JUSTIFICATIVA_MAXIMA) {
            throw new RegraNegocioException("A justificativa deve ter no máximo " + JUSTIFICATIVA_MAXIMA
                    + " caracteres.");
        }
        Pedido pedido = buscarPorId(id);
        if (pedido.isCancelado()) {
            throw new RegraNegocioException("Este pedido já está cancelado.");
        }
        if (!pedido.getStatus().isAntesDaSaida()) {
            throw new RegraNegocioException("Não é possível cancelar um pedido que já saiu para entrega ou foi retirado.");
        }

        String usuario = contextoUsuario.atual().username();
        LocalDateTime agora = agora();
        StatusPedido statusAnterior = pedido.getStatus();

        // Itens e valores são preservados: o cancelamento só muda o status e registra quem/quando/por quê.
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataHoraCancelamento(agora);
        pedido.setResponsavelCancelamento(usuario);
        pedido.setJustificativaCancelamento(motivo);
        pedido.getHistorico().add(new HistoricoStatus(StatusPedido.CANCELADO, agora, usuario));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, EntidadeAuditada.PEDIDO, salvo.getId(),
                new Alteracoes()
                        .comparar("status", statusAnterior, StatusPedido.CANCELADO)
                        .registrar("dataHoraCancelamento", null, agora)
                        .registrar("responsavelCancelamento", null, usuario),
                motivo);
        return salvo;
    }

    // ------------------------------------------------------------------
    // Cálculos e validações auxiliares
    // ------------------------------------------------------------------

    private ItemPedido montarItem(ItemPedidoForm itemForm) {
        if (itemForm == null || itemForm.getProdutoId() == null || itemForm.getProdutoId().isBlank()) {
            throw new RegraNegocioException("Selecione o produto de todos os itens.");
        }
        Integer quantidade = itemForm.getQuantidade();
        if (quantidade == null || quantidade < 1 || quantidade > 50) {
            throw new RegraNegocioException("A quantidade de cada item deve estar entre 1 e 50.");
        }
        Produto produto = produtoRepository.findById(itemForm.getProdutoId())
                .orElseThrow(() -> new RegraNegocioException("Produto informado não existe."));
        if (!produto.isDisponivel()) {
            throw new RegraNegocioException("O produto '" + produto.getNome() + "' está inativo.");
        }
        if (produto.getPreco() == null || produto.getPreco().signum() <= 0) {
            throw new RegraNegocioException("O produto '" + produto.getNome() + "' está sem preço válido.");
        }
        return new ItemPedido(produto.getId(), produto.getNome(), produto.getPreco(), quantidade);
    }

    /** subtotal = soma dos itens; desconto = subtotal x percentual; total = subtotal - desconto. */
    static void recalcularValores(Pedido pedido) {
        BigDecimal subtotal = pedido.getItens().stream()
                .map(item -> item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal percentual = pedido.getPercentualDesconto() == null ? BigDecimal.ZERO
                : pedido.getPercentualDesconto();
        BigDecimal valorDesconto = subtotal.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_EVEN);

        pedido.setSubtotal(subtotal);
        pedido.setValorDesconto(valorDesconto);
        pedido.setValorTotal(subtotal.subtract(valorDesconto));
    }

    private static void exigirNaoCancelado(Pedido pedido) {
        if (pedido.isCancelado()) {
            throw new RegraNegocioException("Pedido cancelado não pode ser alterado.");
        }
    }

    private static String descreverItens(List<ItemPedido> itens) {
        return itens.stream()
                .map(i -> i.getQuantidade() + "x " + i.getNomeProduto() + " (R$ "
                        + i.getPrecoUnitario().setScale(2, RoundingMode.HALF_EVEN).toPlainString() + ")")
                .collect(Collectors.joining("; "));
    }

    private static String textoOuNulo(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.trim();
        return limpo.isEmpty() ? null : limpo;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }
}
