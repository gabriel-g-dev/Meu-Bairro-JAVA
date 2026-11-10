package br.com.fiap.meubairro.dto;

import br.com.fiap.meubairro.entity.StatusOcorrencia;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizacaoStatusRequest(
        @NotNull(message = "O status é obrigatório")
        StatusOcorrencia status,

        @Size(max = 300, message = "A observação deve ter no máximo 300 caracteres")
        String observacao) {
}
