package br.com.fiap.meubairro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(
        @NotBlank(message = "O código é obrigatório")
        @Pattern(regexp = "[A-Z][A-Z_]{1,19}", message = "O código deve ter de 2 a 20 letras maiúsculas, sem acentos (pode usar _)")
        String codigo,

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres")
        String nome,

        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
        String descricao) {
}
