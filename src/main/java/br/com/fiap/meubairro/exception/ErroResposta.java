package br.com.fiap.meubairro.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record ErroResposta(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<CampoErro> fields) {

    public ErroResposta(HttpStatus status, String message, String path, List<CampoErro> fields) {
        this(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), status.value(), status.getReasonPhrase(), message, path, fields);
    }

    public record CampoErro(String field, String message) {
    }
}
