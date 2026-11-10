package br.com.fiap.meubairro.dto;

import br.com.fiap.meubairro.entity.HistoricoOcorrencia;
import br.com.fiap.meubairro.entity.TipoHistorico;

import java.time.LocalDateTime;

public record HistoricoResponse(Long id, TipoHistorico tipo, String descricao, LocalDateTime dataRegistro) {

    public HistoricoResponse(HistoricoOcorrencia historico) {
        this(historico.getId(), historico.getTipo(), historico.getDescricao(), historico.getDataRegistro());
    }
}
