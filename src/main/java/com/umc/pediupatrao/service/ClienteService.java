package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ClienteForm;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Cadastro de clientes. ATENDENTE cadastra, altera e exclui; GERENTE apenas consulta.
 * Toda inclusão, alteração (especialmente endereço e telefone) e exclusão é auditada.
 */
@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, PedidoRepository pedidoRepository,
            AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAllByOrderByNomeAsc();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Cliente buscarPorId(String id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente criar(ClienteForm form) {
        Cliente cliente = new Cliente();
        copiar(form, cliente);
        Cliente salvo = clienteRepository.save(cliente);

        Alteracoes alteracoes = compararCampos(new Cliente(), salvo);
        auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, EntidadeAuditada.CLIENTE, salvo.getId(),
                alteracoes);
        return salvo;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente atualizar(String id, ClienteForm form) {
        Cliente existente = buscarPorId(id);
        Cliente anterior = copia(existente);

        copiar(form, existente);
        Alteracoes alteracoes = compararCampos(anterior, existente);
        if (alteracoes.isVazia()) {
            return existente;
        }
        Cliente salvo = clienteRepository.save(existente);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, EntidadeAuditada.CLIENTE, salvo.getId(),
                alteracoes);
        return salvo;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public void excluir(String id) {
        Cliente existente = buscarPorId(id);
        if (pedidoRepository.existsByClienteId(id)) {
            throw new RegraNegocioException("Não é possível excluir um cliente que possui pedidos registrados.");
        }
        clienteRepository.deleteById(id);
        // Registra apenas o nome: os demais dados pessoais não são necessários para identificar a exclusão.
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, EntidadeAuditada.CLIENTE, id,
                new Alteracoes().registrar("nome", existente.getNome(), null));
    }

    private static Alteracoes compararCampos(Cliente anterior, Cliente novo) {
        return new Alteracoes()
                .comparar("nome", anterior.getNome(), novo.getNome())
                .comparar("telefone", anterior.getTelefone(), novo.getTelefone())
                .comparar("cep", anterior.getCep(), novo.getCep())
                .comparar("logradouro", anterior.getLogradouro(), novo.getLogradouro())
                .comparar("numero", anterior.getNumero(), novo.getNumero())
                .comparar("complemento", anterior.getComplemento(), novo.getComplemento())
                .comparar("bairro", anterior.getBairro(), novo.getBairro())
                .comparar("cidade", anterior.getCidade(), novo.getCidade())
                .comparar("estado", anterior.getEstado(), novo.getEstado());
    }

    private static void copiar(ClienteForm form, Cliente cliente) {
        cliente.setNome(limpar(form.getNome()));
        cliente.setTelefone(limpar(form.getTelefone()));
        cliente.setCep(limpar(form.getCep()));
        cliente.setLogradouro(limpar(form.getLogradouro()));
        cliente.setNumero(limpar(form.getNumero()));
        cliente.setComplemento(limpar(form.getComplemento()));
        cliente.setBairro(limpar(form.getBairro()));
        cliente.setCidade(limpar(form.getCidade()));
        String estado = limpar(form.getEstado());
        cliente.setEstado(estado == null ? null : estado.toUpperCase());
    }

    private static Cliente copia(Cliente origem) {
        Cliente c = new Cliente();
        c.setId(origem.getId());
        c.setNome(origem.getNome());
        c.setTelefone(origem.getTelefone());
        c.setCep(origem.getCep());
        c.setLogradouro(origem.getLogradouro());
        c.setNumero(origem.getNumero());
        c.setComplemento(origem.getComplemento());
        c.setBairro(origem.getBairro());
        c.setCidade(origem.getCidade());
        c.setEstado(origem.getEstado());
        c.setEndereco(origem.getEndereco());
        return c;
    }

    private static String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }
}
