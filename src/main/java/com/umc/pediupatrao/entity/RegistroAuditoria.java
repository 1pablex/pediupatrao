package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Evento de auditoria persistido no MongoDB.
 * Imutável: só pode ser criado (nunca alterado ou excluído pela aplicação).
 */
@Document(collection = "auditoria")
@CompoundIndex(name = "entidade_registro", def = "{'entidade': 1, 'entidadeId': 1, 'dataHora': -1}")
public class RegistroAuditoria {

    @Id
    private String id; // preenchido pelo MongoDB na inserção

    @Indexed
    private final LocalDateTime dataHora;

    private final String usuario;
    private final String perfil;
    private final TipoOperacao operacao;
    private final EntidadeAuditada entidade;
    private final String entidadeId;
    private final List<AlteracaoCampo> alteracoes;
    private final String justificativa;

    @PersistenceCreator
    RegistroAuditoria(String id, LocalDateTime dataHora, String usuario, String perfil, TipoOperacao operacao,
            EntidadeAuditada entidade, String entidadeId, List<AlteracaoCampo> alteracoes, String justificativa) {
        this.id = id;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes == null ? List.of() : List.copyOf(alteracoes);
        this.justificativa = justificativa;
    }

    public static RegistroAuditoria novo(LocalDateTime dataHora, String usuario, String perfil, TipoOperacao operacao,
            EntidadeAuditada entidade, String entidadeId, List<AlteracaoCampo> alteracoes, String justificativa) {
        return new RegistroAuditoria(null, dataHora, usuario, perfil, operacao, entidade, entidadeId, alteracoes,
                justificativa);
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
