package com.umc.pediupatrao.config;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import com.umc.pediupatrao.security.RespostasSeguranca;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segurança em duas camadas:
 * 1) regras por URL nas cadeias de filtros abaixo;
 * 2) @PreAuthorize nos serviços (habilitado por @EnableMethodSecurity), que protege a regra
 *    mesmo que um novo controlador seja criado sem a regra de URL correspondente.
 *
 * Perfis: ADMIN (usuários, consulta de pedidos, auditoria e dashboard), GERENTE (pedidos, descontos,
 * cancelamentos, consulta de clientes, auditoria e dashboard) e ATENDENTE (clientes e registro de pedidos).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    /** Senhas armazenadas com BCrypt; usado na autenticação junto com o UsuarioDetailsService. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * API do dashboard (/api/v1/dashboard/**): somente leitura (GET), para ADMIN e GERENTE.
     * Aceita a sessão de quem já entrou nas telas (é assim que a página do dashboard consome a API)
     * e também HTTP Basic, para integração com outros sistemas. A sessão nunca é criada aqui.
     * O CSRF continua ativo, mas só métodos seguros (GET) são permitidos; os demais recebem 403.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain dashboardApiFilterChain(HttpSecurity http,
            UsuarioDetailsService usuarioDetailsService) throws Exception {
        http
                .securityMatcher("/api/v1/dashboard/**")
                .userDetailsService(usuarioDetailsService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(GET, "/api/v1/dashboard/**").hasAnyRole(ADMIN, GERENTE)
                        .anyRequest().denyAll()
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(RespostasSeguranca.naoAutenticadoApi()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(RespostasSeguranca.naoAutenticadoApi())
                        .accessDeniedHandler(RespostasSeguranca.acessoNegadoApi()));

        return http.build();
    }

    /**
     * API REST (/api/**): autenticação HTTP Basic e sem sessão.
     * Como a API não usa cookie de sessão, não está sujeita a CSRF; por isso o CSRF é desligado só aqui.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, UsuarioDetailsService usuarioDetailsService)
            throws Exception {
        http
                .securityMatcher("/api/**")
                .userDetailsService(usuarioDetailsService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/usuarios/**").hasRole(ADMIN)
                        .requestMatchers(GET, "/api/auditoria/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers(POST, "/api/pedidos/*/desconto", "/api/pedidos/*/cancelamento")
                            .hasRole(GERENTE)
                        .requestMatchers(GET, "/api/pedidos/**").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers(POST, "/api/pedidos").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(PUT, "/api/pedidos/*/status").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(GET, "/api/clientes/**").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(POST, "/api/clientes").hasRole(ATENDENTE)
                        .requestMatchers(PUT, "/api/clientes/*").hasRole(ATENDENTE)
                        .requestMatchers(DELETE, "/api/clientes/*").hasRole(ATENDENTE)
                        .requestMatchers(GET, "/api/produtos/**").authenticated()
                        .requestMatchers("/api/produtos/**").hasRole(GERENTE)
                        .anyRequest().denyAll()
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(RespostasSeguranca.naoAutenticadoApi()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(RespostasSeguranca.naoAutenticadoApi())
                        .accessDeniedHandler(RespostasSeguranca.acessoNegadoApi()));

        return http.build();
    }

    /**
     * Telas Thymeleaf: login por formulário com sessão e proteção CSRF ativa
     * (o Thymeleaf insere o token automaticamente em todo formulário com th:action).
     */
    @Bean
    @Order(3)
    public SecurityFilterChain webFilterChain(HttpSecurity http, UsuarioDetailsService usuarioDetailsService)
            throws Exception {
        http
                .userDetailsService(usuarioDetailsService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/images/**", "/plugins/**",
                                "/dist/**", "/favicon.ico").permitAll()
                        .requestMatchers("/usuarios/**").hasRole(ADMIN)
                        .requestMatchers("/auditoria/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/dashboard/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers(POST, "/pedidos/*/desconto", "/pedidos/*/cancelar").hasRole(GERENTE)
                        .requestMatchers("/pedidos/novo").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(POST, "/pedidos/**").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers("/pedidos/**").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/clientes/novo", "/clientes/editar/**").hasRole(ATENDENTE)
                        .requestMatchers(POST, "/clientes/**").hasRole(ATENDENTE)
                        .requestMatchers("/clientes/**").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers("/produtos/novo", "/produtos/editar/**").hasRole(GERENTE)
                        .requestMatchers(POST, "/produtos/**").hasRole(GERENTE)
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?erro")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex.accessDeniedHandler(RespostasSeguranca.acessoNegadoWeb()));

        return http.build();
    }
}
