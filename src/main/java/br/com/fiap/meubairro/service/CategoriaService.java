package br.com.fiap.meubairro.service;

import br.com.fiap.meubairro.dto.CategoriaRequest;
import br.com.fiap.meubairro.dto.CategoriaResponse;
import br.com.fiap.meubairro.entity.Categoria;
import br.com.fiap.meubairro.exception.RecursoNaoEncontradoException;
import br.com.fiap.meubairro.exception.RegraNegocioException;
import br.com.fiap.meubairro.exception.RequisicaoInvalidaException;
import br.com.fiap.meubairro.repository.CategoriaRepository;
import br.com.fiap.meubairro.repository.OcorrenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoriaService {

    public static final List<String> CODIGOS_DO_APP = List.of("BURACO", "ILUMINACAO", "LIXO", "ALAGAMENTO", "CALCADA", "OUTRO");

    private final CategoriaRepository categoriaRepository;
    private final OcorrenciaRepository ocorrenciaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository, OcorrenciaRepository ocorrenciaRepository) {
        this.categoriaRepository = categoriaRepository;
        this.ocorrenciaRepository = ocorrenciaRepository;
    }

    public List<CategoriaResponse> listar() {
        return categoriaRepository.findAllByOrderByIdAsc().stream()
                .map(CategoriaResponse::new)
                .toList();
    }

    public CategoriaResponse buscarPorId(Long id) {
        return new CategoriaResponse(buscarEntidade(id));
    }

    public Categoria buscarPorCodigo(String codigo) {
        return categoriaRepository.findByCodigoIgnoreCase(codigo.trim())
                .orElseThrow(() -> new RequisicaoInvalidaException("Categoria inválida: " + codigo
                        + ". Use uma destas: " + codigosDisponiveis()));
    }

    @Transactional
    public CategoriaResponse criar(CategoriaRequest dados) {
        if (categoriaRepository.existsByCodigo(dados.codigo())) {
            throw new RegraNegocioException("Já existe uma categoria com o código " + dados.codigo());
        }
        Categoria categoria = new Categoria(dados.codigo(), dados.nome().trim(), limpar(dados.descricao()));
        return new CategoriaResponse(categoriaRepository.save(categoria));
    }

    @Transactional
    public CategoriaResponse atualizar(Long id, CategoriaRequest dados) {
        Categoria categoria = buscarEntidade(id);
        if (!categoria.getCodigo().equals(dados.codigo())) {
            if (CODIGOS_DO_APP.contains(categoria.getCodigo())) {
                throw new RegraNegocioException("O código das categorias usadas pelo app não pode ser alterado");
            }
            if (categoriaRepository.existsByCodigo(dados.codigo())) {
                throw new RegraNegocioException("Já existe uma categoria com o código " + dados.codigo());
            }
        }
        categoria.atualizar(dados.codigo(), dados.nome().trim(), limpar(dados.descricao()));
        return new CategoriaResponse(categoria);
    }

    @Transactional
    public void excluir(Long id) {
        Categoria categoria = buscarEntidade(id);
        if (CODIGOS_DO_APP.contains(categoria.getCodigo())) {
            throw new RegraNegocioException("As categorias usadas pelo app não podem ser excluídas");
        }
        if (ocorrenciaRepository.existsByCategoriaIdOrCategoriaSugeridaId(id, id)) {
            throw new RegraNegocioException("A categoria tem ocorrências vinculadas e não pode ser excluída");
        }
        categoriaRepository.delete(categoria);
    }

    private Categoria buscarEntidade(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria " + id + " não encontrada"));
    }

    private String limpar(String descricao) {
        return descricao == null || descricao.isBlank() ? null : descricao.trim();
    }

    private String codigosDisponiveis() {
        return categoriaRepository.findAllByOrderByIdAsc().stream()
                .map(Categoria::getCodigo)
                .collect(Collectors.joining(", "));
    }
}
