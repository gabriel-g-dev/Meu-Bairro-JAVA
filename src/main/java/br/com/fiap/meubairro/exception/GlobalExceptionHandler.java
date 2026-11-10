package br.com.fiap.meubairro.exception;

import br.com.fiap.meubairro.exception.ErroResposta.CampoErro;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> tratarValidacao(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<CampoErro> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> new CampoErro(erro.getField(), mensagemDoCampo(erro)))
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "Existem campos inválidos na requisição", request, campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> tratarCorpoInvalido(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String mensagem = "O corpo da requisição está ausente ou não é um JSON válido";
        if (ex.getCause() instanceof InvalidFormatException causa) {
            String campo = nomeDoCampo(causa);
            mensagem = "Valor inválido para o campo '" + campo + "'";
            if (causa.getTargetType() != null && causa.getTargetType().isEnum()) {
                mensagem += ". Valores aceitos: " + Arrays.toString(causa.getTargetType().getEnumConstants());
            }
        }
        return responder(HttpStatus.BAD_REQUEST, mensagem, request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> tratarParametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String mensagem = "Valor inválido para o parâmetro '" + ex.getName() + "': " + ex.getValue();
        if (ex.getRequiredType() != null && ex.getRequiredType().isEnum()) {
            mensagem += ". Valores aceitos: " + Arrays.toString(ex.getRequiredType().getEnumConstants());
        }
        return responder(HttpStatus.BAD_REQUEST, mensagem, request, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResposta> tratarArquivoGrande(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return responder(HttpStatus.PAYLOAD_TOO_LARGE, "A foto deve ter no máximo 5 MB", request, List.of());
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErroResposta> tratarMultipartInvalido(MultipartException ex, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "Não foi possível ler os dados do formulário multipart", request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResposta> tratarTipoNaoSuportado(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        String tiposAceitos = ex.getSupportedMediaTypes().stream()
                .map(MediaType::toString)
                .collect(Collectors.joining(", "));
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo não suportado. Envie como: " + tiposAceitos,
                request, List.of());
    }

    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ResponseEntity<ErroResposta> tratarRequisicaoInvalida(RequisicaoInvalidaException ex, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> tratarNaoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest request) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResposta> tratarRegraNegocio(RegraNegocioException ex, HttpServletRequest request) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResposta> tratarAcessoNegado(AcessoNegadoException ex, HttpServletRequest request) {
        return responder(HttpStatus.FORBIDDEN, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResposta> tratarAcessoNegadoSpring(AccessDeniedException ex, HttpServletRequest request) {
        return responder(HttpStatus.FORBIDDEN, "Você não tem permissão para acessar este recurso", request, List.of());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErroResposta> tratarCredenciaisInvalidas(BadCredentialsException ex, HttpServletRequest request) {
        return responder(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos", request, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResposta> tratarFalhaAutenticacao(AuthenticationException ex, HttpServletRequest request) {
        return responder(HttpStatus.UNAUTHORIZED, "Não foi possível autenticar o usuário", request, List.of());
    }

    @ExceptionHandler(AnaliseIaException.class)
    public ResponseEntity<ErroResposta> tratarFalhaAnaliseIa(AnaliseIaException ex, HttpServletRequest request) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ArmazenamentoFotoException.class)
    public ResponseEntity<ErroResposta> tratarFalhaFoto(ArmazenamentoFotoException ex, HttpServletRequest request) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> tratarIntegridade(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violação de integridade em {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT, "A operação conflita com dados já existentes", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> tratarErroGenerico(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse erroSpring) {
            HttpStatus status = HttpStatus.valueOf(erroSpring.getStatusCode().value());
            return responder(status, mensagemPadrao(status, erroSpring), request, List.of());
        }
        log.error("Erro inesperado em {}", request.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor. Tente novamente mais tarde.", request, List.of());
    }

    private String mensagemDoCampo(FieldError erro) {
        if (erro.isBindingFailure()) {
            return "Valor inválido para o campo '" + erro.getField() + "'";
        }
        return erro.getDefaultMessage();
    }

    private String mensagemPadrao(HttpStatus status, ErrorResponse erroSpring) {
        return switch (status) {
            case NOT_FOUND -> "Recurso não encontrado";
            case METHOD_NOT_ALLOWED -> "Método HTTP não suportado nesta rota";
            default -> erroSpring.getBody().getDetail();
        };
    }

    private String nomeDoCampo(JsonMappingException causa) {
        if (causa.getPath().isEmpty()) {
            return "desconhecido";
        }
        return causa.getPath().get(causa.getPath().size() - 1).getFieldName();
    }

    private ResponseEntity<ErroResposta> responder(HttpStatus status, String mensagem, HttpServletRequest request, List<CampoErro> campos) {
        return ResponseEntity.status(status).body(new ErroResposta(status, mensagem, request.getRequestURI(), campos));
    }
}
