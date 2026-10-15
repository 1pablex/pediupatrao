package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Produto;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProdutoRepository extends MongoRepository<Produto, String> {

    List<Produto> findAllByOrderByTipoAscNomeAsc();

    List<Produto> findByAtivoTrueOrderByTipoAscNomeAsc();
}
