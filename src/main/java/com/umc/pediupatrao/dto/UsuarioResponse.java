package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;

/** Representação pública do usuário: nunca inclui a senha (nem o hash). */
public record UsuarioResponse(String id, String username, Perfil perfil) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getPerfil());
    }
}
