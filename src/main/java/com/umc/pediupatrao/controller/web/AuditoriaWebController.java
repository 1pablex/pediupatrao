package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.dto.RegistroAuditoriaDTO;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/auditoria")
public class AuditoriaWebController {

    private static final int TAMANHO_PAGINA = 20;

    private final AuditoriaService auditoriaService;

    public AuditoriaWebController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public String consultar(@RequestParam(required = false) EntidadeAuditada entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(defaultValue = "0") int pagina,
            Model model) {
        Page<RegistroAuditoriaDTO> registros = auditoriaService.consultar(entidade, entidadeId, pagina,
                TAMANHO_PAGINA);
        model.addAttribute("registros", registros);
        model.addAttribute("entidades", EntidadeAuditada.values());
        model.addAttribute("entidadeSelecionada", entidade);
        model.addAttribute("entidadeId", entidadeId);
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}
