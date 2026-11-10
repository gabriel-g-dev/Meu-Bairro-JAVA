package br.com.fiap.meubairro.controller;

import br.com.fiap.meubairro.dto.CategoriaRequest;
import br.com.fiap.meubairro.dto.CategoriaResponse;
import br.com.fiap.meubairro.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/categorias")
@Tag(name = "Categorias", description = "Categorias aceitas no campo categoria das ocorrências")
@SecurityRequirement(name = "bearerAuth")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    @Operation(summary = "Lista as categorias com código e nome")
    public List<CategoriaResponse> listar() {
        return categoriaService.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha uma categoria")
    public CategoriaResponse buscarPorId(@PathVariable Long id) {
        return categoriaService.buscarPorId(id);
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Categoria criada")
    @Operation(summary = "Cria uma categoria (GESTOR)")
    public ResponseEntity<CategoriaResponse> criar(@RequestBody @Valid CategoriaRequest dados, UriComponentsBuilder uriBuilder) {
        CategoriaResponse categoria = categoriaService.criar(dados);
        URI uri = uriBuilder.path("/categorias/{id}").buildAndExpand(categoria.id()).toUri();
        return ResponseEntity.created(uri).body(categoria);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma categoria (GESTOR)",
            description = "O código das seis categorias usadas pelo app não pode ser alterado.")
    public CategoriaResponse atualizar(@PathVariable Long id, @RequestBody @Valid CategoriaRequest dados) {
        return categoriaService.atualizar(id, dados);
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "Categoria excluída")
    @Operation(summary = "Exclui uma categoria sem ocorrências (GESTOR)",
            description = "As seis categorias usadas pelo app não podem ser excluídas.")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        categoriaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
