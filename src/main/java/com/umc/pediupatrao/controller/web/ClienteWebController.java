package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.dto.ClienteForm;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clientes")
public class ClienteWebController {

    private final ClienteService clienteService;

    public ClienteWebController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("content", "clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        return formulario(model, new ClienteForm());
    }

    @PostMapping("/salvar")
    public String salvar(@Valid @ModelAttribute("cliente") ClienteForm form, BindingResult result, Model model,
            RedirectAttributes redirect) {
        form.setId(null);
        if (result.hasErrors()) {
            return formulario(model, form);
        }
        clienteService.criar(form);
        redirect.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        return "redirect:/clientes";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model) {
        return formulario(model, ClienteForm.de(clienteService.buscarPorId(id)));
    }

    @PostMapping("/editar/{id}")
    public String atualizar(@PathVariable String id, @Valid @ModelAttribute("cliente") ClienteForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (result.hasErrors()) {
            return formulario(model, form);
        }
        clienteService.atualizar(id, form);
        redirect.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        return "redirect:/clientes";
    }

    @PostMapping("/excluir/{id}")
    public String excluir(@PathVariable String id, RedirectAttributes redirect) {
        try {
            clienteService.excluir(id);
            redirect.addFlashAttribute("sucesso", "Cliente excluído com sucesso!");
        } catch (RegraNegocioException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/clientes";
    }

    private String formulario(Model model, ClienteForm form) {
        model.addAttribute("cliente", form);
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }
}
