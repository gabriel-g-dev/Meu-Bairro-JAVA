package br.com.fiap.meubairro.repository;

import br.com.fiap.meubairro.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findAllByOrderByIdAsc();

    Optional<Categoria> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigo(String codigo);
}
