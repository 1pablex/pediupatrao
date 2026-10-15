package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.dto.ProdutoForm;
import com.umc.pediupatrao.service.ProdutoService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/produtos")
public class ProdutoWebController {

    static final List<String> TIPOS = List.of("Pizza", "Lanche", "Bebida", "Porção", "Adicional");

    private final ProdutoService produtoService;

    public ProdutoWebController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("content", "produtos/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        return formulario(model, new ProdutoForm());
    }

    @PostMapping("/salvar")
    public String salvar(@Valid @ModelAttribute("produto") ProdutoForm form, BindingResult result, Model model,
            RedirectAttributes redirect) {
        form.setId(null);
        if (result.hasErrors()) {
            return formulario(model, form);
        }
        produtoService.criar(form);
        redirect.addFlashAttribute("sucesso", "Produto criado com sucesso!");
        return "redirect:/produtos";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model) {
        return formulario(model, ProdutoForm.de(produtoService.buscarPorId(id)));
    }

    @PostMapping("/editar/{id}")
    public String atualizar(@PathVariable String id, @Valid @ModelAttribute("produto") ProdutoForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (result.hasErrors()) {
            return formulario(model, form);
        }
        produtoService.atualizar(id, form);
        redirect.addFlashAttribute("sucesso", "Produto atualizado com sucesso!");
        return "redirect:/produtos";
    }

    @PostMapping("/excluir/{id}")
    public String excluir(@PathVariable String id, RedirectAttributes redirect) {
        produtoService.excluir(id);
        redirect.addFlashAttribute("sucesso", "Produto excluído com sucesso!");
        return "redirect:/produtos";
    }

    private String formulario(Model model, ProdutoForm form) {
        model.addAttribute("produto", form);
        model.addAttribute("tipos", TIPOS);
        model.addAttribute("content", "produtos/form :: content");
        return "layout";
    }
}
