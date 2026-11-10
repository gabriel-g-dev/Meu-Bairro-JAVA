package br.com.fiap.meubairro.exception;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ErroPadraoAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> atributosOriginais = super.getErrorAttributes(webRequest, options);
        HttpStatus status = HttpStatus.resolve((int) atributosOriginais.getOrDefault("status", 500));
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("timestamp", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        resposta.put("status", status.value());
        resposta.put("error", status.getReasonPhrase());
        resposta.put("message", mensagemPara(status));
        resposta.put("path", atributosOriginais.get("path"));
        resposta.put("fields", List.of());
        return resposta;
    }

    private String mensagemPara(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Requisição inválida";
            case UNAUTHORIZED -> "Autenticação necessária";
            case FORBIDDEN -> "Você não tem permissão para acessar este recurso";
            case NOT_FOUND -> "Recurso não encontrado";
            case METHOD_NOT_ALLOWED -> "Método HTTP não suportado nesta rota";
            case INTERNAL_SERVER_ERROR -> "Erro interno no servidor. Tente novamente mais tarde.";
            default -> status.getReasonPhrase();
        };
    }
}
