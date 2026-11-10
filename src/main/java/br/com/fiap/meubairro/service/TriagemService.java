package br.com.fiap.meubairro.service;

import br.com.fiap.meubairro.dto.ResultadoAnaliseIa;
import br.com.fiap.meubairro.dto.TriagemResponse;
import br.com.fiap.meubairro.entity.Ocorrencia;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.exception.AnaliseIaException;
import org.springframework.stereotype.Service;

@Service
public class TriagemService {

    private final OcorrenciaService ocorrenciaService;
    private final AnaliseIaService analiseIaService;

    public TriagemService(OcorrenciaService ocorrenciaService, AnaliseIaService analiseIaService) {
        this.ocorrenciaService = ocorrenciaService;
        this.analiseIaService = analiseIaService;
    }

    public TriagemResponse solicitarAnalise(Long ocorrenciaId, Usuario usuario) {
        Ocorrencia ocorrencia = ocorrenciaService.buscarEntidade(ocorrenciaId);
        ocorrenciaService.verificarDonoOuGestor(ocorrencia, usuario);
        return executar(ocorrencia);
    }

    public TriagemResponse executar(Long ocorrenciaId) {
        return executar(ocorrenciaService.buscarEntidade(ocorrenciaId));
    }

    private TriagemResponse executar(Ocorrencia ocorrencia) {
        ResultadoAnaliseIa resultado;
        try {
            resultado = analiseIaService.analisar(ocorrencia.getTitulo(), ocorrencia.getDescricao());
        } catch (AnaliseIaException e) {
            ocorrenciaService.registrarFalhaAnalise(ocorrencia.getId(), e.getMessage());
            throw e;
        }
        Ocorrencia atualizada = ocorrenciaService.registrarResultadoAnalise(ocorrencia.getId(), resultado);
        return new TriagemResponse(atualizada);
    }
}
