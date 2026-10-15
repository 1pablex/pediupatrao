package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.dto.ItemPedidoForm;
import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/pedidos")
public class PedidoWebController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final AuditoriaService auditoriaService;

    public PedidoWebController(PedidoService pedidoService, ClienteService clienteService,
            ProdutoService produtoService, AuditoriaService auditoriaService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos());
        model.addAttribute("content", "pedidos/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        PedidoForm form = new PedidoForm();
        form.setTipoEntrega(TipoEntrega.ENTREGA);
        form.getItens().add(new ItemPedidoForm(null, 1));
        return formulario(model, form);
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("pedido") PedidoForm form, BindingResult result, Model model,
            RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return formulario(model, form);
        }
        Pedido pedido;
        try {
            pedido = pedidoService.criarPedido(form);
        } catch (RegraNegocioException ex) {
            model.addAttribute("erro", ex.getMessage());
            return formulario(model, form);
        }
        redirect.addFlashAttribute("sucesso", "Pedido registrado com sucesso!");
        return "redirect:/pedidos/" + pedido.getId();
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable String id, Model model, HttpServletRequest request) {
        Pedido pedido = pedidoService.buscarPorId(id);
        model.addAttribute("pedido", pedido);
        model.addAttribute("proximosStatus", pedido.getStatus().proximosPermitidos(pedido.getTipoEntrega()));
        if (request.isUserInRole("ADMIN") || request.isUserInRole("GERENTE")) {
            model.addAttribute("auditoria", auditoriaService.historicoDe(EntidadeAuditada.PEDIDO, id));
        }
        model.addAttribute("content", "pedidos/detalhe :: content");
        return "layout";
    }

    @PostMapping("/{id}/status")
    public String alterarStatus(@PathVariable String id, @RequestParam StatusPedido status,
            RedirectAttributes redirect) {
        return executar(id, redirect, "Status atualizado para \"" + status.getDescricao() + "\".",
                () -> pedidoService.avancarStatus(id, status));
    }

    @PostMapping("/{id}/desconto")
    public String aplicarDesconto(@PathVariable String id, @RequestParam BigDecimal percentual,
            RedirectAttributes redirect) {
        return executar(id, redirect, "Desconto aplicado com sucesso.",
                () -> pedidoService.aplicarDesconto(id, percentual));
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(defaultValue = "") String justificativa,
            RedirectAttributes redirect) {
        return executar(id, redirect, "Pedido cancelado.", () -> pedidoService.cancelar(id, justificativa));
    }

    private String executar(String id, RedirectAttributes redirect, String mensagemSucesso, Runnable operacao) {
        try {
            operacao.run();
            redirect.addFlashAttribute("sucesso", mensagemSucesso);
        } catch (RegraNegocioException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    private String formulario(Model model, PedidoForm form) {
        if (form.getItens().isEmpty()) {
            form.getItens().add(new ItemPedidoForm(null, 1));
        }
        model.addAttribute("pedido", form);
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarAtivos());
        model.addAttribute("tiposEntrega", TipoEntrega.values());
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }
}
