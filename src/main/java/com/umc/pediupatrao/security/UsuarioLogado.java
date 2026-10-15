package com.umc.pediupatrao.security;

/** Identificação do usuário autenticado na requisição atual. */
public record UsuarioLogado(String username, String perfil) {
}
