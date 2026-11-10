package br.com.fiap.meubairro.security;

import br.com.fiap.meubairro.exception.ErroResposta;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class SegurancaErroHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SegurancaErroHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        Object erroToken = request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_ERRO_TOKEN);
        String mensagem = erroToken != null
                ? erroToken.toString()
                : "Autenticação necessária. Envie o token no cabeçalho Authorization: Bearer <token>";
        response.setHeader("WWW-Authenticate", "Bearer");
        escrever(response, HttpStatus.UNAUTHORIZED, mensagem, request);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        escrever(response, HttpStatus.FORBIDDEN, "Você não tem permissão para acessar este recurso", request);
    }

    private void escrever(HttpServletResponse response, HttpStatus status, String mensagem, HttpServletRequest request)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ErroResposta(status, mensagem, request.getRequestURI(), List.of()));
    }
}
