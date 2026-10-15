package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.TipoOperacao;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Registro de auditoria pronto para exibição. Dados pessoais sensíveis (telefone) já vêm mascarados.
 */
public class RegistroAuditoriaDTO {

    private final String id;
    private final LocalDateTime dataHora;
    private final String usuario;
    private final String perfil;
    private final TipoOperacao operacao;
    private final EntidadeAuditada entidade;
    private final String entidadeId;
    private final List<AlteracaoCampo> alteracoes;
    private final String justificativa;

    public RegistroAuditoriaDTO(String id, LocalDateTime dataHora, String usuario, String perfil,
            TipoOperacao operacao, EntidadeAuditada entidade, String entidadeId, List<AlteracaoCampo> alteracoes,
            String justificativa) {
        this.id = id;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes;
        this.justificativa = justificativa;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public TipoOperacao getOperacao() {
        return operacao;
    }

    public String getOperacaoDescricao() {
        return operacao == null ? null : operacao.getDescricao();
    }

    public EntidadeAuditada getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public List<AlteracaoCampo> getAlteracoes() {
        return alteracoes;
    }

    public String getJustificativa() {
        return justificativa;
    }
}
