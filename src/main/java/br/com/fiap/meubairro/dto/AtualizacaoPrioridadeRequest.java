package br.com.fiap.meubairro.dto;

import br.com.fiap.meubairro.entity.Prioridade;
import jakarta.validation.constraints.NotNull;

public record AtualizacaoPrioridadeRequest(
        @NotNull(message = "A prioridade é obrigatória")
        Prioridade prioridade) {
}
