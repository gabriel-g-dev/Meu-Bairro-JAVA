package br.com.fiap.meubairro.dto;

import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.entity.Usuario;

public record UsuarioDetalheResponse(Long id, String nome, String email, Perfil perfil) {

    public UsuarioDetalheResponse(Usuario usuario) {
        this(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil());
    }
}
