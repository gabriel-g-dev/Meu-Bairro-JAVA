package br.com.fiap.meubairro.repository;

import br.com.fiap.meubairro.entity.HistoricoOcorrencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoOcorrenciaRepository extends JpaRepository<HistoricoOcorrencia, Long> {

    List<HistoricoOcorrencia> findByOcorrenciaIdOrderByIdAsc(Long ocorrenciaId);
}
