package br.com.fiap.meubairro.security;

import br.com.fiap.meubairro.entity.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final String EMISSOR = "meu-bairro-api";

    private final SecretKey chave;
    private final long expiracaoMinutos;

    public TokenService(@Value("${meubairro.jwt.secret:}") String segredo,
                        @Value("${meubairro.jwt.expiracao-minutos:120}") long expiracaoMinutos) {
        this.expiracaoMinutos = expiracaoMinutos;
        if (segredo == null || segredo.isBlank()) {
            log.warn("JWT_SECRET não foi definido. Usando uma chave aleatória: os tokens deixam de valer quando a API reinicia.");
            this.chave = Jwts.SIG.HS256.key().build();
        } else {
            byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 caracteres");
            }
            this.chave = Keys.hmacShaKeyFor(bytes);
        }
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .issuer(EMISSOR)
                .subject(usuario.getEmail())
                .claim("id", usuario.getId())
                .claim("perfil", usuario.getPerfil().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracaoMinutos, ChronoUnit.MINUTES)))
                .signWith(chave)
                .compact();
    }

    public String validarToken(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .requireIssuer(EMISSOR)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
