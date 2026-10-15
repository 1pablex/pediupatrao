package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.RegistroAuditoriaDTO;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.EntidadeAuditada;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.security.ContextoUsuario;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"),
            ZoneId.of("America/Sao_Paulo"));

    @Mock
    private AuditoriaRepository auditoriaRepository;
    @Mock
    private ContextoUsuario contextoUsuario;

    @Test
    void registraUsuarioPerfilDataHoraEOmiteCredenciais() {
        when(contextoUsuario.atual()).thenReturn(new UsuarioLogado("atendente01", "ATENDENTE"));
        AuditoriaService service = new AuditoriaService(auditoriaRepository, contextoUsuario, RELOGIO);

        service.registrar(TipoOperacao.ALTERACAO_CLIENTE, EntidadeAuditada.CLIENTE, "c1",
                new Alteracoes()
                        .comparar("logradouro", "Rua das Flores", "Av. Brasil")
                        .comparar("senha", "123", "456"));

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaRepository).insert(captor.capture());
        RegistroAuditoria registro = captor.getValue();
        assertThat(registro.getUsuario()).isEqualTo("atendente01");
        assertThat(registro.getPerfil()).isEqualTo("ATENDENTE");
        assertThat(registro.getDataHora()).isEqualTo(LocalDateTime.now(RELOGIO));
        assertThat(registro.getOperacao()).isEqualTo(TipoOperacao.ALTERACAO_CLIENTE);
        assertThat(registro.getEntidadeId()).isEqualTo("c1");
        assertThat(registro.getAlteracoes()).extracting(AlteracaoCampo::getCampo).containsExactly("logradouro");
    }

    @Test
    void registroDeAuditoriaEImutavel() {
        when(contextoUsuario.atual()).thenReturn(new UsuarioLogado("gerente01", "GERENTE"));
        when(auditoriaRepository.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        AuditoriaService service = new AuditoriaService(auditoriaRepository, contextoUsuario, RELOGIO);

        service.registrar(TipoOperacao.DESCONTO_PEDIDO, EntidadeAuditada.PEDIDO, "p1",
                new Alteracoes().comparar("percentualDesconto", "0", "10"));

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaRepository).insert(captor.capture());
        List<AlteracaoCampo> alteracoes = captor.getValue().getAlteracoes();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> alteracoes.add(new AlteracaoCampo("x", "1", "2")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void telefoneEExibidoMascarado() {
        RegistroAuditoria registro = RegistroAuditoria.novo(LocalDateTime.now(RELOGIO), "atendente01", "ATENDENTE",
                TipoOperacao.ALTERACAO_CLIENTE, EntidadeAuditada.CLIENTE, "c1",
                List.of(new AlteracaoCampo("telefone", "(11) 98765-4321", "(11) 91234-0000"),
                        new AlteracaoCampo("logradouro", "Rua das Flores", "Av. Brasil")),
                null);

        RegistroAuditoriaDTO dto = AuditoriaService.paraExibicao(registro);

        assertThat(dto.getAlteracoes().get(0).getValorAnterior()).isEqualTo("*******4321");
        assertThat(dto.getAlteracoes().get(0).getValorNovo()).isEqualTo("*******0000");
        assertThat(dto.getAlteracoes().get(1).getValorNovo()).isEqualTo("Av. Brasil");
    }
}
