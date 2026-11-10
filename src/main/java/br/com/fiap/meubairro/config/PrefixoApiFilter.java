package br.com.fiap.meubairro.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PrefixoApiFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "/api";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = caminhoDaAplicacao(request);
        return !caminho.equals(PREFIXO) && !caminho.startsWith(PREFIXO + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String caminho = caminhoDaAplicacao(request).substring(PREFIXO.length());
        filterChain.doFilter(new RequisicaoSemPrefixo(request, caminho.isEmpty() ? "/" : caminho), response);
    }

    private String caminhoDaAplicacao(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static class RequisicaoSemPrefixo extends HttpServletRequestWrapper {

        private final String caminho;

        RequisicaoSemPrefixo(HttpServletRequest request, String caminho) {
            super(request);
            this.caminho = caminho;
        }

        @Override
        public String getRequestURI() {
            return getContextPath() + caminho;
        }

        @Override
        public String getServletPath() {
            return caminho;
        }

        @Override
        public StringBuffer getRequestURL() {
            String urlOriginal = super.getRequestURL().toString();
            String inicio = urlOriginal.substring(0, urlOriginal.length() - super.getRequestURI().length());
            return new StringBuffer(inicio).append(getRequestURI());
        }
    }
}
