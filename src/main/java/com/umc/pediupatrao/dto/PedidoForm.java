package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.TipoEntrega;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * Dados aceitos na criação do pedido. Não há campos de preço, desconto, total, status ou responsável:
 * tudo isso é definido pelo servidor. Campos extras enviados no JSON são ignorados.
 */
public class PedidoForm {

    @NotBlank(message = "Selecione o cliente.")
    private String clienteId;

    @NotNull(message = "Selecione entrega ou retirada.")
    private TipoEntrega tipoEntrega;

    @Size(max = 300, message = "A observação deve ter no máximo 300 caracteres.")
    private String observacao;

    @Valid
    @NotEmpty(message = "Adicione pelo menos um item ao pedido.")
    @Size(max = 30, message = "O pedido pode ter no máximo 30 itens.")
    private List<ItemPedidoForm> itens = new ArrayList<>();

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public TipoEntrega getTipoEntrega() {
        return tipoEntrega;
    }

    public void setTipoEntrega(TipoEntrega tipoEntrega) {
        this.tipoEntrega = tipoEntrega;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public List<ItemPedidoForm> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedidoForm> itens) {
        this.itens = itens;
    }
}
