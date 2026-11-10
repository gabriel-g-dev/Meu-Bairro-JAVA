package br.com.fiap.meubairro.service;

import br.com.fiap.meubairro.dto.LoginRequest;
import br.com.fiap.meubairro.dto.LoginResponse;
import br.com.fiap.meubairro.dto.UsuarioResponse;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.security.TokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthService(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    public LoginResponse login(LoginRequest dados) {
        UsernamePasswordAuthenticationToken credenciais =
                new UsernamePasswordAuthenticationToken(dados.email().trim().toLowerCase(), dados.senha());
        Authentication autenticacao = authenticationManager.authenticate(credenciais);
        Usuario usuario = (Usuario) autenticacao.getPrincipal();
        return new LoginResponse(tokenService.gerarToken(usuario), new UsuarioResponse(usuario));
    }
}
