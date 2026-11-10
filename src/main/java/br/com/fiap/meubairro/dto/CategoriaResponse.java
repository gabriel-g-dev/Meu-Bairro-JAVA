package br.com.fiap.meubairro.dto;

import br.com.fiap.meubairro.entity.Categoria;

public record CategoriaResponse(Long id, String codigo, String nome, String descricao) {

    public CategoriaResponse(Categoria categoria) {
        this(categoria.getId(), categoria.getCodigo(), categoria.getNome(), categoria.getDescricao());
    }
}
