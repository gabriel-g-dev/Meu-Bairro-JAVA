package br.com.fiap.meubairro.dto;

public record LoginResponse(String token, UsuarioResponse usuario) {
}
