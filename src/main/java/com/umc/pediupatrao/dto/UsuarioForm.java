package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados aceitos no cadastro/edição de usuário. Só estes campos podem ser enviados,
 * evitando que a requisição defina id ou outros atributos diretamente na entidade.
 */
public class UsuarioForm {

    private String id;

    @NotBlank(message = "Informe o username.")
    @Size(min = 3, max = 40, message = "O username deve ter entre 3 e 40 caracteres.")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Use apenas letras, números, ponto, hífen ou sublinhado.")
    private String username;

    /** Obrigatória no cadastro; na edição, em branco mantém a senha atual. */
    @Size(max = 72, message = "A senha deve ter no máximo 72 caracteres.")
    private String password;

    @NotNull(message = "Selecione um perfil.")
    private Perfil perfil;

    public static UsuarioForm de(Usuario usuario) {
        UsuarioForm form = new UsuarioForm();
        form.setId(usuario.getId());
        form.setUsername(usuario.getUsername());
        form.setPerfil(usuario.getPerfil());
        return form;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public void setPerfil(Perfil perfil) {
        this.perfil = perfil;
    }
}
