package br.com.fiap.meubairro.messaging;

import java.time.LocalDateTime;

public record OcorrenciaCriadaEvento(Long ocorrenciaId, Long usuarioId, String titulo, LocalDateTime dataCriacao) {
}
