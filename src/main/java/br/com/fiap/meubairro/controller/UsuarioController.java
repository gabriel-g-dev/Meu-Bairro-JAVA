package br.com.fiap.meubairro.controller;

import br.com.fiap.meubairro.dto.CadastroUsuarioRequest;
import br.com.fiap.meubairro.dto.UsuarioDetalheResponse;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuários", description = "Dados do usuário logado e cadastro de gestores")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/me")
    @Operation(summary = "Retorna os dados e o perfil do usuário logado")
    public UsuarioDetalheResponse buscarUsuarioLogado(@AuthenticationPrincipal Usuario usuario) {
        return new UsuarioDetalheResponse(usuario);
    }

    @PostMapping("/gestores")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um novo gestor", description = "Somente um GESTOR já cadastrado pode criar outro gestor.")
    public UsuarioDetalheResponse cadastrarGestor(@RequestBody @Valid CadastroUsuarioRequest dados) {
        return usuarioService.cadastrarGestor(dados);
    }
}
