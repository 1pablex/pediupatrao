package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ProdutoForm;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Cardápio. Qualquer usuário autenticado consulta; somente o GERENTE altera preços e produtos.
 */
@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public ProdutoService(ProdutoRepository produtoRepository, AuditoriaService auditoriaService) {
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("isAuthenticated()")
    public List<Produto> listarProdutos() {
        return produtoRepository.findAllByOrderByTipoAscNomeAsc();
    }

    @PreAuthorize("isAuthenticated()")
    public List<Produto> listarAtivos() {
        return produtoRepository.findByAtivoTrueOrderByTipoAscNomeAsc();
    }

    @PreAuthorize("isAuthenticated()")
    public Produto buscarPorId(String id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Produto criar(ProdutoForm form) {
        Produto produto = new Produto();
        copiar(form, produto);
        Produto salvo = produtoRepository.save(produto);
        auditoriaService.registrar(TipoOperacao.INCLUSAO_PRODUTO, EntidadeAuditada.PRODUTO, salvo.getId(),
                new Alteracoes()
                        .registrar("nome", null, salvo.getNome())
                        .registrar("tipo", null, salvo.getTipo())
                        .registrar("preco", null, salvo.getPreco())
                        .registrar("ativo", null, salvo.getAtivo()));
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Produto atualizar(String id, ProdutoForm form) {
        Produto existente = buscarPorId(id);
        Alteracoes alteracoes = new Alteracoes()
                .comparar("nome", existente.getNome(), form.getNome().trim())
                .comparar("tipo", existente.getTipo(), form.getTipo().trim())
                .comparar("preco", existente.getPreco(), form.getPreco())
                .comparar("ativo", existente.isDisponivel(), form.isAtivo());
        if (alteracoes.isVazia()) {
            return existente;
        }
        copiar(form, existente);
        Produto salvo = produtoRepository.save(existente);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_PRODUTO, EntidadeAuditada.PRODUTO, salvo.getId(),
                alteracoes);
        return salvo;
    }

    /** Os pedidos guardam nome e preço dos itens, então excluir um produto não altera pedidos existentes. */
    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Produto existente = buscarPorId(id);
        produtoRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_PRODUTO, EntidadeAuditada.PRODUTO, id,
                new Alteracoes()
                        .registrar("nome", existente.getNome(), null)
                        .registrar("preco", existente.getPreco(), null));
    }

    private static void copiar(ProdutoForm form, Produto produto) {
        produto.setNome(form.getNome().trim());
        produto.setTipo(form.getTipo().trim());
        produto.setPreco(form.getPreco());
        produto.setAtivo(form.isAtivo());
    }
}
