package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.RegistroAuditoriaDTO;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.security.ContextoUsuario;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Registro e consulta da auditoria.
 * - O usuário e o perfil vêm sempre do contexto de segurança.
 * - Campos de credenciais nunca são gravados.
 * - Não existe operação de alteração ou exclusão de registros.
 */
@Service
public class AuditoriaService {

    private static final Set<String> CAMPOS_PROIBIDOS = Set.of("password", "senha", "token", "hash");
    private static final Set<String> CAMPOS_TELEFONE = Set.of("telefone");
    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final AuditoriaRepository auditoriaRepository;
    private final ContextoUsuario contextoUsuario;
    private final Clock clock;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, ContextoUsuario contextoUsuario, Clock clock) {
        this.auditoriaRepository = auditoriaRepository;
        this.contextoUsuario = contextoUsuario;
        this.clock = clock;
    }

    public void registrar(TipoOperacao operacao, EntidadeAuditada entidade, String entidadeId,
            Alteracoes alteracoes) {
        registrar(operacao, entidade, entidadeId, alteracoes, null);
    }

    public void registrar(TipoOperacao operacao, EntidadeAuditada entidade, String entidadeId,
            Alteracoes alteracoes, String justificativa) {
        UsuarioLogado usuario = contextoUsuario.atual();
        List<AlteracaoCampo> campos = alteracoes.lista().stream()
                .filter(a -> !CAMPOS_PROIBIDOS.contains(a.getCampo().toLowerCase()))
                .toList();
        auditoriaRepository.insert(RegistroAuditoria.novo(LocalDateTime.now(clock), usuario.username(),
                usuario.perfil(), operacao, entidade, entidadeId, campos, justificativa));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Page<RegistroAuditoriaDTO> consultar(EntidadeAuditada entidade, String entidadeId, int pagina,
            int tamanho) {
        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO_PAGINA));
        Page<RegistroAuditoria> resultado;
        if (entidade != null && entidadeId != null && !entidadeId.isBlank()) {
            resultado = auditoriaRepository.findByEntidadeAndEntidadeIdOrderByDataHoraDesc(entidade,
                    entidadeId.trim(), pageRequest);
        } else if (entidade != null) {
            resultado = auditoriaRepository.findByEntidadeOrderByDataHoraDesc(entidade, pageRequest);
        } else {
            resultado = auditoriaRepository.findAllByOrderByDataHoraDesc(pageRequest);
        }
        return resultado.map(AuditoriaService::paraExibicao);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<RegistroAuditoriaDTO> historicoDe(EntidadeAuditada entidade, String entidadeId) {
        return auditoriaRepository.findByEntidadeAndEntidadeIdOrderByDataHoraDesc(entidade, entidadeId).stream()
                .map(AuditoriaService::paraExibicao)
                .toList();
    }

    /** Minimiza a exposição de dados pessoais: telefones são exibidos mascarados. */
    static RegistroAuditoriaDTO paraExibicao(RegistroAuditoria registro) {
        List<AlteracaoCampo> alteracoes = registro.getAlteracoes().stream()
                .map(a -> CAMPOS_TELEFONE.contains(a.getCampo())
                        ? new AlteracaoCampo(a.getCampo(), mascararTelefone(a.getValorAnterior()),
                                mascararTelefone(a.getValorNovo()))
                        : a)
                .toList();
        return new RegistroAuditoriaDTO(registro.getId(), registro.getDataHora(), registro.getUsuario(),
                registro.getPerfil(), registro.getOperacao(), registro.getEntidade(), registro.getEntidadeId(),
                alteracoes, registro.getJustificativa());
    }

    static String mascararTelefone(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() <= 4) {
            return "****";
        }
        return "*".repeat(digitos.length() - 4) + digitos.substring(digitos.length() - 4);
    }
}
