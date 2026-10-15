package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ClienteForm;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteRepository, pedidoRepository, auditoriaService);
    }

    @Test
    void alteracaoDeEnderecoRegistraSomenteOsCamposModificados() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(clienteExistente()));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        ClienteForm form = ClienteForm.de(clienteExistente());
        form.setLogradouro("Av. Brasil");
        form.setTelefone("(11) 91234-0000");
        service.atualizar("c1", form);

        ArgumentCaptor<Alteracoes> captor = ArgumentCaptor.forClass(Alteracoes.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), eq(EntidadeAuditada.CLIENTE), eq("c1"),
                captor.capture());
        assertThat(captor.getValue().lista())
                .extracting(AlteracaoCampo::getCampo, AlteracaoCampo::getValorAnterior, AlteracaoCampo::getValorNovo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("telefone", "(11) 98765-4321", "(11) 91234-0000"),
                        org.assertj.core.groups.Tuple.tuple("logradouro", "Rua das Flores", "Av. Brasil"));
    }

    @Test
    void edicaoSemMudancasNaoGeraAuditoria() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(clienteExistente()));

        service.atualizar("c1", ClienteForm.de(clienteExistente()));

        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void inclusaoDeClienteEAuditada() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId("novo");
            return c;
        });
        ClienteForm form = new ClienteForm();
        form.setNome("Maria");
        form.setTelefone("(11) 90000-1111");
        form.setEstado("sp");

        Cliente salvo = service.criar(form);

        assertThat(salvo.getEstado()).isEqualTo("SP");
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_CLIENTE), eq(EntidadeAuditada.CLIENTE), eq("novo"),
                any(Alteracoes.class));
    }

    @Test
    void naoExcluiClienteComPedidos() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(clienteExistente()));
        when(pedidoRepository.existsByClienteId("c1")).thenReturn(true);

        assertThatThrownBy(() -> service.excluir("c1"))
                .isInstanceOf(RegraNegocioException.class);
        verify(clienteRepository, never()).deleteById(anyString());
    }

    private static Cliente clienteExistente() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("João da Silva");
        c.setTelefone("(11) 98765-4321");
        c.setCep("01310-100");
        c.setLogradouro("Rua das Flores");
        c.setNumero("100");
        c.setBairro("Centro");
        c.setCidade("São Paulo");
        c.setEstado("SP");
        return c;
    }
}
