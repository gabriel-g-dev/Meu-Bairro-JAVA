package br.com.fiap.meubairro.security;

import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.repository.UsuarioRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATRIBUTO_ERRO_TOKEN = "meubairro.erroToken";

    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(TokenService tokenService, UsuarioRepository usuarioRepository) {
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = recuperarToken(request);
        if (token != null) {
            try {
                String email = tokenService.validarToken(token);
                Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
                if (usuario.isPresent()) {
                    UsernamePasswordAuthenticationToken autenticacao =
                            new UsernamePasswordAuthenticationToken(usuario.get(), null, usuario.get().getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(autenticacao);
                } else {
                    request.setAttribute(ATRIBUTO_ERRO_TOKEN, "O usuário do token não existe mais");
                }
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(ATRIBUTO_ERRO_TOKEN, "Token inválido ou expirado");
            }
        }
        filterChain.doFilter(request, response);
    }

    private String recuperarToken(HttpServletRequest request) {
        String cabecalho = request.getHeader("Authorization");
        if (cabecalho == null || !cabecalho.startsWith("Bearer ")) {
            return null;
        }
        return cabecalho.substring(7).trim();
    }
}
