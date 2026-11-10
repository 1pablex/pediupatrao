package com.umc.pediupatrao.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Página do dashboard. A página não recebe dados do servidor: todo o conteúdo é carregado
 * pelo navegador a partir da API /api/v1/dashboard (static/js/dashboard.js).
 */
@Controller
public class DashboardWebController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("content", "dashboard :: content");
        return "layout";
    }
}
