package br.com.fiap.meubairro.repository;

import br.com.fiap.meubairro.entity.Ocorrencia;
import br.com.fiap.meubairro.entity.Prioridade;
import br.com.fiap.meubairro.entity.StatusOcorrencia;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {

    @EntityGraph(attributePaths = {"usuario", "categoria"})
    List<Ocorrencia> findAllByOrderByDataCriacaoDescIdDesc();

    @EntityGraph(attributePaths = {"usuario", "categoria"})
    @Query("""
            select o from Ocorrencia o
            where (:status is null or o.status = :status)
              and (:categoria is null or o.categoria.codigo = :categoria)
              and (:prioridade is null or o.prioridade = :prioridade)
            """)
    Page<Ocorrencia> buscar(@Param("status") StatusOcorrencia status,
                            @Param("categoria") String categoria,
                            @Param("prioridade") Prioridade prioridade,
                            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Ocorrencia o where o.id = :id")
    Optional<Ocorrencia> buscarParaAtualizacao(@Param("id") Long id);

    boolean existsByCategoriaIdOrCategoriaSugeridaId(Long categoriaId, Long categoriaSugeridaId);

    long countByStatus(StatusOcorrencia status);

    long countByPrioridade(Prioridade prioridade);
}
