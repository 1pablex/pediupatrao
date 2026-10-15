package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PedidoRepository extends MongoRepository<Pedido, String> {

    List<Pedido> findByClienteId(String clienteId);

    List<Pedido> findAllByOrderByDataHoraEntradaDesc();

    boolean existsByClienteId(String clienteId);
}
