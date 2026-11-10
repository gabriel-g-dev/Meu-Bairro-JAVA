package br.com.fiap.meubairro.service;

import br.com.fiap.meubairro.dto.HistoricoResponse;
import br.com.fiap.meubairro.entity.HistoricoOcorrencia;
import br.com.fiap.meubairro.entity.Ocorrencia;
import br.com.fiap.meubairro.entity.TipoHistorico;
import br.com.fiap.meubairro.repository.HistoricoOcorrenciaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoricoService {

    private static final int TAMANHO_MAXIMO = 500;

    private final HistoricoOcorrenciaRepository historicoRepository;

    public HistoricoService(HistoricoOcorrenciaRepository historicoRepository) {
        this.historicoRepository = historicoRepository;
    }

    public void registrar(Ocorrencia ocorrencia, TipoHistorico tipo, String descricao) {
        historicoRepository.save(new HistoricoOcorrencia(ocorrencia, tipo, limitarTamanho(descricao)));
    }

    public List<HistoricoResponse> listar(Long ocorrenciaId) {
        return historicoRepository.findByOcorrenciaIdOrderByIdAsc(ocorrenciaId).stream()
                .map(HistoricoResponse::new)
                .toList();
    }

    private String limitarTamanho(String texto) {
        if (texto.length() <= TAMANHO_MAXIMO) {
            return texto;
        }
        return texto.substring(0, TAMANHO_MAXIMO - 3) + "...";
    }
}
