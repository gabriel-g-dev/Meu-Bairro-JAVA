package br.com.fiap.meubairro.config;

import br.com.fiap.meubairro.repository.UsuarioRepository;
import br.com.fiap.meubairro.security.JwtAuthenticationFilter;
import br.com.fiap.meubairro.security.SegurancaErroHandler;
import br.com.fiap.meubairro.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   TokenService tokenService,
                                                   UsuarioRepository usuarioRepository,
                                                   SegurancaErroHandler segurancaErroHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/fotos/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/categorias").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.PUT, "/categorias/*").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.DELETE, "/categorias/*").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.POST, "/ocorrencias").hasRole("CIDADAO")
                        .requestMatchers(HttpMethod.PATCH, "/ocorrencias/*/status", "/ocorrencias/*/prioridade").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.GET, "/ocorrencias/resumo").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.POST, "/usuarios/gestores").hasRole("GESTOR")
                        .anyRequest().authenticated())
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(segurancaErroHandler)
                        .accessDeniedHandler(segurancaErroHandler))
                .addFilterBefore(new JwtAuthenticationFilter(tokenService, usuarioRepository), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${meubairro.cors.origens:}") List<String> origensPermitidas) {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(origensPermitidas);
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuracao);
        return source;
    }
}
