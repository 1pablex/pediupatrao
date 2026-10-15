package com.umc.pediupatrao.controller.api;

import com.umc.pediupatrao.dto.PaginaResponse;
import com.umc.pediupatrao.dto.RegistroAuditoriaDTO;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Somente leitura: não há endpoints de alteração ou exclusão de registros de auditoria. */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaApiController {

    private final AuditoriaService auditoriaService;

    public AuditoriaApiController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public PaginaResponse<RegistroAuditoriaDTO> consultar(
            @RequestParam(required = false) EntidadeAuditada entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return PaginaResponse.de(auditoriaService.consultar(entidade, entidadeId, pagina, tamanho));
    }
}
