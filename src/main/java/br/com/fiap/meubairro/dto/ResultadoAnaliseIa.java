package br.com.fiap.meubairro.dto;

import br.com.fiap.meubairro.entity.Categoria;
import br.com.fiap.meubairro.entity.Prioridade;

public record ResultadoAnaliseIa(Categoria categoria, Prioridade prioridade, String justificativa) {
}
