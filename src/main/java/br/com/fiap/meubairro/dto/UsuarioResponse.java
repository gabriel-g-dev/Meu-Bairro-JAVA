package br.com.fiap.meubairro.dto;

import br.com.fiap.meubairro.entity.Usuario;

public record UsuarioResponse(Long id, String nome, String email) {

    public UsuarioResponse(Usuario usuario) {
        this(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }
}
