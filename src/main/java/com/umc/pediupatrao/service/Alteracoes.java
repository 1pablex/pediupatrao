package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Monta a lista de campos alterados (valor anterior x novo) de um evento de auditoria.
 */
public final class Alteracoes {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final List<AlteracaoCampo> lista = new ArrayList<>();

    /** Adiciona o campo apenas se o valor mudou. */
    public Alteracoes comparar(String campo, Object anterior, Object novo) {
        String valorAnterior = formatar(anterior);
        String valorNovo = formatar(novo);
        if (!Objects.equals(valorAnterior, valorNovo)) {
            lista.add(new AlteracaoCampo(campo, valorAnterior, valorNovo));
        }
        return this;
    }

    /** Adiciona o campo sempre (usado em inclusões e em valores informativos). */
    public Alteracoes registrar(String campo, Object anterior, Object novo) {
        lista.add(new AlteracaoCampo(campo, formatar(anterior), formatar(novo)));
        return this;
    }

    public boolean isVazia() {
        return lista.isEmpty();
    }

    public List<AlteracaoCampo> lista() {
        return List.copyOf(lista);
    }

    static String formatar(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof BigDecimal numero) {
            return numero.stripTrailingZeros().toPlainString();
        }
        if (valor instanceof LocalDateTime dataHora) {
            return dataHora.format(FORMATO_DATA);
        }
        if (valor instanceof Enum<?> constante) {
            return constante.name();
        }
        String texto = valor.toString().trim();
        return texto.isEmpty() ? null : texto;
    }
}
