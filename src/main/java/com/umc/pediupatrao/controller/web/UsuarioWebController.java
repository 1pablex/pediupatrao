package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.UsuarioService;
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
@RequestMapping("/usuarios")
public class UsuarioWebController {

    private final UsuarioService usuarioService;

    public UsuarioWebController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("content", "usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        return formulario(model, new UsuarioForm());
    }

    @PostMapping("/salvar")
    public String salvar(@Valid @ModelAttribute("usuario") UsuarioForm form, BindingResult result, Model model,
            RedirectAttributes redirect) {
        form.setId(null);
        if (result.hasErrors()) {
            return formulario(model, form);
        }
        try {
            usuarioService.criar(form);
        } catch (RegraNegocioException ex) {
            model.addAttribute("erro", ex.getMessage());
            return formulario(model, form);
        }
        redirect.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        return "redirect:/usuarios";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model) {
        return formulario(model, UsuarioForm.de(usuarioService.buscarPorId(id)));
    }

    @PostMapping("/editar/{id}")
    public String atualizar(@PathVariable String id, @Valid @ModelAttribute("usuario") UsuarioForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (result.hasErrors()) {
            return formulario(model, form);
        }
        try {
            usuarioService.atualizar(id, form);
        } catch (RegraNegocioException ex) {
            model.addAttribute("erro", ex.getMessage());
            return formulario(model, form);
        }
        redirect.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/deletar/{id}")
    public String excluir(@PathVariable String id, RedirectAttributes redirect) {
        try {
            usuarioService.excluir(id);
            redirect.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (RegraNegocioException ex) {
            redirect.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/usuarios";
    }

    private String formulario(Model model, UsuarioForm form) {
        model.addAttribute("usuario", form);
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }
}
