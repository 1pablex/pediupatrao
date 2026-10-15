package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Repositório "somente inserção e leitura".
 * Estende Repository (e não MongoRepository) de propósito: não expõe save/update/delete,
 * garantindo que os registros de auditoria não sejam alterados nem excluídos pela aplicação.
 */
public interface AuditoriaRepository extends Repository<RegistroAuditoria, String> {

    /** insert falha se o id já existir, então nunca sobrescreve um registro. */
    RegistroAuditoria insert(RegistroAuditoria registro);

    Page<RegistroAuditoria> findAllByOrderByDataHoraDesc(Pageable pageable);

    Page<RegistroAuditoria> findByEntidadeOrderByDataHoraDesc(EntidadeAuditada entidade, Pageable pageable);

    Page<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(EntidadeAuditada entidade,
            String entidadeId, Pageable pageable);

    List<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(EntidadeAuditada entidade,
            String entidadeId);
}
