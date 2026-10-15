package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria um usuário de cada perfil e um cardápio inicial quando o banco está vazio.
 * As senhas vêm de variáveis de ambiente (arquivo .env); sem senha definida, o usuário não é criado.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository usuarioRepository, ProdutoRepository produtoRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.senha-admin:}") String senhaAdmin,
            @Value("${app.seed.senha-gerente:}") String senhaGerente,
            @Value("${app.seed.senha-atendente:}") String senhaAtendente) {
        return args -> {
            criarUsuario(usuarioRepository, passwordEncoder, "admin", senhaAdmin, Perfil.ADMIN);
            criarUsuario(usuarioRepository, passwordEncoder, "gerente01", senhaGerente, Perfil.GERENTE);
            criarUsuario(usuarioRepository, passwordEncoder, "atendente01", senhaAtendente, Perfil.ATENDENTE);
            criarCardapio(produtoRepository);
        };
    }

    private static void criarUsuario(UsuarioRepository repo, PasswordEncoder encoder, String username,
            String senha, Perfil perfil) {
        Usuario existente = repo.findByUsername(username).orElse(null);
        if (existente != null && existente.getPerfil() != null) {
            return;
        }
        if (senha == null || senha.isBlank()) {
            log.warn("Usuário inicial '{}' não criado: defina a senha no arquivo .env.", username);
            return;
        }
        if (existente != null) {
            // Usuário do modelo antigo (sem perfil): recebe o perfil e a senha definidos no .env.
            existente.setPerfil(perfil);
            existente.setPassword(encoder.encode(senha));
            repo.save(existente);
            log.info("Usuário inicial migrado para o novo modelo de perfis: {} ({})", username, perfil);
            return;
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(encoder.encode(senha));
        usuario.setPerfil(perfil);
        repo.save(usuario);
        log.info("Usuário inicial criado: {} ({})", username, perfil);
    }

    private static void criarCardapio(ProdutoRepository repo) {
        if (repo.count() > 0) {
            return;
        }
        repo.save(produto("Pizza Calabresa", "Pizza", "52.90"));
        repo.save(produto("Pizza Margherita", "Pizza", "49.90"));
        repo.save(produto("Pizza Quatro Queijos", "Pizza", "58.90"));
        repo.save(produto("X-Burguer", "Lanche", "24.90"));
        repo.save(produto("Porção de Batata Frita", "Porção", "29.90"));
        repo.save(produto("Refrigerante 2L", "Bebida", "14.00"));
        repo.save(produto("Borda Recheada", "Adicional", "9.90"));
        log.info("Cardápio inicial criado.");
    }

    private static Produto produto(String nome, String tipo, String preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(new BigDecimal(preco));
        p.setAtivo(true);
        return p;
    }
}
