package br.com.fiap.meubairro.dto;

import br.com.fiap.meubairro.entity.Ocorrencia;
import br.com.fiap.meubairro.entity.Prioridade;
import br.com.fiap.meubairro.entity.StatusAnaliseIa;

import java.time.LocalDateTime;

public record TriagemResponse(
        Long ocorrenciaId,
        Prioridade prioridade,
        boolean prioridadeDefinidaPeloGestor,
        StatusAnaliseIa statusAnaliseIa,
        String categoriaSugerida,
        Prioridade prioridadeSugerida,
        String justificativa,
        LocalDateTime dataAnalise) {

    public TriagemResponse(Ocorrencia ocorrencia) {
        this(ocorrencia.getId(),
                ocorrencia.getPrioridade(),
                ocorrencia.isPrioridadeManual(),
                ocorrencia.getStatusAnaliseIa(),
                ocorrencia.getCategoriaSugerida() != null ? ocorrencia.getCategoriaSugerida().getCodigo() : null,
                ocorrencia.getPrioridadeSugerida(),
                ocorrencia.getJustificativaIa(),
                ocorrencia.getDataAnaliseIa());
    }
}
