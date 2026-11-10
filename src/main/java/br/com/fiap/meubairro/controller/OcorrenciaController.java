package br.com.fiap.meubairro.controller;

import br.com.fiap.meubairro.dto.AtualizacaoPrioridadeRequest;
import br.com.fiap.meubairro.dto.AtualizacaoStatusRequest;
import br.com.fiap.meubairro.dto.HistoricoResponse;
import br.com.fiap.meubairro.dto.OcorrenciaForm;
import br.com.fiap.meubairro.dto.OcorrenciaResponse;
import br.com.fiap.meubairro.dto.PaginaResponse;
import br.com.fiap.meubairro.dto.ResumoOcorrenciasResponse;
import br.com.fiap.meubairro.dto.TriagemResponse;
import br.com.fiap.meubairro.entity.Prioridade;
import br.com.fiap.meubairro.entity.StatusOcorrencia;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.service.OcorrenciaService;
import br.com.fiap.meubairro.service.TriagemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/ocorrencias")
@Tag(name = "Ocorrências", description = "Registro e acompanhamento dos problemas do bairro")
@SecurityRequirement(name = "bearerAuth")
public class OcorrenciaController {

    private final OcorrenciaService ocorrenciaService;
    private final TriagemService triagemService;

    public OcorrenciaController(OcorrenciaService ocorrenciaService, TriagemService triagemService) {
        this.ocorrenciaService = ocorrenciaService;
        this.triagemService = triagemService;
    }

    @GetMapping
    @Operation(summary = "Lista todas as ocorrências, das mais recentes para as mais antigas")
    public List<OcorrenciaResponse> listar() {
        return ocorrenciaService.listar();
    }

    @GetMapping("/paginado")
    @Operation(summary = "Lista as ocorrências com paginação",
            description = "Filtros opcionais: status, categoria e prioridade.")
    public PaginaResponse<OcorrenciaResponse> listarPaginado(
            @RequestParam(required = false) StatusOcorrencia status,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Prioridade prioridade,
            @ParameterObject @PageableDefault(size = 10, sort = "dataCriacao", direction = Sort.Direction.DESC) Pageable pageable) {
        return ocorrenciaService.listarPaginado(status, categoria, prioridade, pageable);
    }

    @GetMapping("/resumo")
    @Operation(summary = "Totais de ocorrências por status e prioridade (GESTOR)")
    public ResumoOcorrenciasResponse resumir() {
        return ocorrenciaService.resumir();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha uma ocorrência")
    public OcorrenciaResponse buscarPorId(@PathVariable Long id) {
        return ocorrenciaService.buscarPorId(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "201", description = "Ocorrência registrada")
    @Operation(summary = "Registra uma ocorrência (CIDADAO)",
            description = "Recebe multipart/form-data. Salva a ocorrência com status ABERTA e publica um evento no RabbitMQ "
                    + "para a triagem da IA.")
    public ResponseEntity<OcorrenciaResponse> criar(@Valid @ModelAttribute OcorrenciaForm dados,
                                                    @AuthenticationPrincipal Usuario usuario,
                                                    UriComponentsBuilder uriBuilder) {
        OcorrenciaResponse ocorrencia = ocorrenciaService.criar(dados, usuario);
        URI uri = uriBuilder.path("/ocorrencias/{id}").buildAndExpand(ocorrencia.id()).toUri();
        return ResponseEntity.created(uri).body(ocorrencia);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Atualiza uma ocorrência própria com status ABERTA",
            description = "Recebe multipart/form-data. Latitude, longitude e foto não enviadas mantêm os valores atuais.")
    public OcorrenciaResponse atualizar(@PathVariable Long id,
                                        @Valid @ModelAttribute OcorrenciaForm dados,
                                        @AuthenticationPrincipal Usuario usuario) {
        return ocorrenciaService.atualizar(id, dados, usuario);
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204", description = "Ocorrência excluída")
    @Operation(summary = "Exclui uma ocorrência",
            description = "O cidadão exclui as próprias ocorrências com status ABERTA; o gestor pode excluir qualquer uma.")
    public ResponseEntity<Void> excluir(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        ocorrenciaService.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Altera o status da ocorrência (GESTOR)")
    public OcorrenciaResponse alterarStatus(@PathVariable Long id, @RequestBody @Valid AtualizacaoStatusRequest dados) {
        return ocorrenciaService.alterarStatus(id, dados);
    }

    @PatchMapping("/{id}/prioridade")
    @Operation(summary = "Define a prioridade manualmente (GESTOR)",
            description = "Depois do ajuste manual, a triagem da IA não altera mais a prioridade desta ocorrência.")
    public TriagemResponse alterarPrioridade(@PathVariable Long id, @RequestBody @Valid AtualizacaoPrioridadeRequest dados) {
        return ocorrenciaService.alterarPrioridade(id, dados);
    }

    @GetMapping("/{id}/triagem")
    @Operation(summary = "Mostra a prioridade e a última análise da IA da ocorrência")
    public TriagemResponse buscarTriagem(@PathVariable Long id) {
        return ocorrenciaService.buscarTriagem(id);
    }

    @PostMapping("/{id}/analise-ia")
    @Operation(summary = "Pede uma nova análise da IA (dono da ocorrência ou GESTOR)",
            description = "A IA sugere categoria, prioridade e justificativa a partir do título e da descrição.")
    public TriagemResponse analisarComIa(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        return triagemService.solicitarAnalise(id, usuario);
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Lista o histórico de andamento da ocorrência")
    public List<HistoricoResponse> listarHistorico(@PathVariable Long id) {
        return ocorrenciaService.listarHistorico(id);
    }
}
