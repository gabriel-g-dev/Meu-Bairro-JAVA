package br.com.fiap.meubairro.dto;

import br.com.fiap.meubairro.entity.Ocorrencia;
import br.com.fiap.meubairro.entity.StatusOcorrencia;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;

public record OcorrenciaResponse(
        Long id,
        String titulo,
        String descricao,
        String categoria,
        StatusOcorrencia status,
        String endereco,
        Double latitude,
        Double longitude,
        String fotoUrl,
        LocalDateTime criadoEm,
        Long usuarioId) {

    public OcorrenciaResponse(Ocorrencia ocorrencia) {
        this(ocorrencia.getId(),
                ocorrencia.getTitulo(),
                ocorrencia.getDescricao(),
                ocorrencia.getCategoria().getCodigo(),
                ocorrencia.getStatus(),
                ocorrencia.getEndereco(),
                ocorrencia.getLatitude(),
                ocorrencia.getLongitude(),
                urlCompleta(ocorrencia.getFotoUrl()),
                ocorrencia.getDataCriacao(),
                ocorrencia.getUsuario().getId());
    }

    private static String urlCompleta(String fotoUrl) {
        if (fotoUrl == null || !fotoUrl.startsWith("/") || RequestContextHolder.getRequestAttributes() == null) {
            return fotoUrl;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(fotoUrl).toUriString();
    }
}
