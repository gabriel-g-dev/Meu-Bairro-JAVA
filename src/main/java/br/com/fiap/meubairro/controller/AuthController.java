package br.com.fiap.meubairro.controller;

import br.com.fiap.meubairro.dto.CadastroUsuarioRequest;
import br.com.fiap.meubairro.dto.LoginRequest;
import br.com.fiap.meubairro.dto.LoginResponse;
import br.com.fiap.meubairro.dto.UsuarioResponse;
import br.com.fiap.meubairro.service.AuthService;
import br.com.fiap.meubairro.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação", description = "Cadastro de cidadãos e login com JWT")
public class AuthController {

    private final UsuarioService usuarioService;
    private final AuthService authService;

    public AuthController(UsuarioService usuarioService, AuthService authService) {
        this.usuarioService = usuarioService;
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um cidadão", description = "Todo cadastro público recebe o perfil CIDADAO.")
    public UsuarioResponse cadastrar(@RequestBody @Valid CadastroUsuarioRequest dados) {
        return usuarioService.cadastrarCidadao(dados);
    }

    @PostMapping("/login")
    @Operation(summary = "Faz login e devolve o token JWT")
    public LoginResponse login(@RequestBody @Valid LoginRequest dados) {
        return authService.login(dados);
    }
}
