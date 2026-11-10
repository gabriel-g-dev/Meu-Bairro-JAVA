package br.com.fiap.meubairro.service;

import br.com.fiap.meubairro.dto.AtualizacaoPrioridadeRequest;
import br.com.fiap.meubairro.dto.AtualizacaoStatusRequest;
import br.com.fiap.meubairro.dto.HistoricoResponse;
import br.com.fiap.meubairro.dto.OcorrenciaForm;
import br.com.fiap.meubairro.dto.OcorrenciaResponse;
import br.com.fiap.meubairro.dto.PaginaResponse;
import br.com.fiap.meubairro.dto.ResultadoAnaliseIa;
import br.com.fiap.meubairro.dto.ResumoOcorrenciasResponse;
import br.com.fiap.meubairro.dto.TriagemResponse;
import br.com.fiap.meubairro.entity.Categoria;
import br.com.fiap.meubairro.entity.Ocorrencia;
import br.com.fiap.meubairro.entity.Prioridade;
import br.com.fiap.meubairro.entity.StatusOcorrencia;
import br.com.fiap.meubairro.entity.TipoHistorico;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.exception.AcessoNegadoException;
import br.com.fiap.meubairro.exception.RecursoNaoEncontradoException;
import br.com.fiap.meubairro.exception.RegraNegocioException;
import br.com.fiap.meubairro.exception.RequisicaoInvalidaException;
import br.com.fiap.meubairro.messaging.OcorrenciaEventoPublisher;
import br.com.fiap.meubairro.repository.OcorrenciaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class OcorrenciaService {

    private static final Set<String> CAMPOS_ORDENACAO = Set.of("id", "titulo", "status", "dataCriacao", "dataAtualizacao");

    private final OcorrenciaRepository ocorrenciaRepository;
    private final CategoriaService categoriaService;
    private final HistoricoService historicoService;
    private final FotoService fotoService;
    private final OcorrenciaEventoPublisher eventoPublisher;

    public OcorrenciaService(OcorrenciaRepository ocorrenciaRepository,
                             CategoriaService categoriaService,
                             HistoricoService historicoService,
                             FotoService fotoService,
                             OcorrenciaEventoPublisher eventoPublisher) {
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.categoriaService = categoriaService;
        this.historicoService = historicoService;
        this.fotoService = fotoService;
        this.eventoPublisher = eventoPublisher;
    }

    public OcorrenciaResponse criar(OcorrenciaForm dados, Usuario cidadao) {
        Categoria categoria = categoriaService.buscarPorCodigo(dados.categoria());
        String fotoUrl = fotoService.salvar(dados.foto());
        Ocorrencia ocorrencia = new Ocorrencia(dados.titulo().trim(), dados.descricao().trim(), dados.endereco().trim(),
                dados.latitude(), dados.longitude(), fotoUrl, categoria, cidadao);
        ocorrenciaRepository.save(ocorrencia);
        historicoService.registrar(ocorrencia, TipoHistorico.CRIACAO, "Ocorrência registrada pelo cidadão");

        boolean eventoPublicado = eventoPublisher.publicarOcorrenciaCriada(ocorrencia);
        if (!eventoPublicado) {
            historicoService.registrar(ocorrencia, TipoHistorico.FILA,
                    "Não foi possível enviar a ocorrência para a fila de triagem. A análise por IA pode ser solicitada manualmente");
        }
        return new OcorrenciaResponse(ocorrencia);
    }

    public List<OcorrenciaResponse> listar() {
        return ocorrenciaRepository.findAllByOrderByDataCriacaoDescIdDesc().stream()
                .map(OcorrenciaResponse::new)
                .toList();
    }

    public PaginaResponse<OcorrenciaResponse> listarPaginado(StatusOcorrencia status, String categoria,
                                                             Prioridade prioridade, Pageable pageable) {
        validarOrdenacao(pageable.getSort());
        String codigoCategoria = null;
        if (categoria != null && !categoria.isBlank()) {
            codigoCategoria = categoriaService.buscarPorCodigo(categoria).getCodigo();
        }
        return new PaginaResponse<>(ocorrenciaRepository.buscar(status, codigoCategoria, prioridade, pageable)
                .map(OcorrenciaResponse::new));
    }

    public OcorrenciaResponse buscarPorId(Long id) {
        return new OcorrenciaResponse(buscarEntidade(id));
    }

    public TriagemResponse buscarTriagem(Long id) {
        return new TriagemResponse(buscarEntidade(id));
    }

    @Transactional
    public OcorrenciaResponse atualizar(Long id, OcorrenciaForm dados, Usuario usuario) {
        Ocorrencia ocorrencia = buscarParaAtualizacao(id);
        if (!ocorrencia.pertenceA(usuario)) {
            throw new AcessoNegadoException("Somente o cidadão que registrou a ocorrência pode alterá-la");
        }
        if (!ocorrencia.estaAberta()) {
            throw new RegraNegocioException("Só é possível alterar ocorrências com status ABERTA");
        }
        Categoria categoria = categoriaService.buscarPorCodigo(dados.categoria());
        Double latitude = dados.latitude() != null ? dados.latitude() : ocorrencia.getLatitude();
        Double longitude = dados.longitude() != null ? dados.longitude() : ocorrencia.getLongitude();
        ocorrencia.atualizarDados(dados.titulo().trim(), dados.descricao().trim(), dados.endereco().trim(),
                latitude, longitude, categoria);

        String fotoAnterior = ocorrencia.getFotoUrl();
        String novaFoto = fotoService.salvar(dados.foto());
        if (novaFoto != null) {
            ocorrencia.alterarFoto(novaFoto);
        }
        historicoService.registrar(ocorrencia, TipoHistorico.ATUALIZACAO, "Dados da ocorrência atualizados pelo cidadão");
        if (novaFoto != null) {
            fotoService.excluir(fotoAnterior);
        }
        return new OcorrenciaResponse(ocorrencia);
    }

    @Transactional
    public void excluir(Long id, Usuario usuario) {
        Ocorrencia ocorrencia = buscarParaAtualizacao(id);
        if (!usuario.isGestor()) {
            if (!ocorrencia.pertenceA(usuario)) {
                throw new AcessoNegadoException("Somente o cidadão que registrou a ocorrência pode excluí-la");
            }
            if (!ocorrencia.estaAberta()) {
                throw new RegraNegocioException("Só é possível excluir ocorrências com status ABERTA");
            }
        }
        ocorrenciaRepository.delete(ocorrencia);
        ocorrenciaRepository.flush();
        fotoService.excluir(ocorrencia.getFotoUrl());
    }

    @Transactional
    public OcorrenciaResponse alterarStatus(Long id, AtualizacaoStatusRequest dados) {
        Ocorrencia ocorrencia = buscarParaAtualizacao(id);
        StatusOcorrencia statusAnterior = ocorrencia.getStatus();
        if (statusAnterior == StatusOcorrencia.RESOLVIDA) {
            throw new RegraNegocioException("A ocorrência já foi resolvida e não pode mudar de status");
        }
        if (statusAnterior == dados.status()) {
            throw new RegraNegocioException("A ocorrência já está com o status " + statusAnterior);
        }
        ocorrencia.alterarStatus(dados.status());

        String descricao = "Status alterado de " + statusAnterior + " para " + dados.status();
        if (dados.observacao() != null && !dados.observacao().isBlank()) {
            descricao += ". Observação do gestor: " + dados.observacao().trim();
        }
        historicoService.registrar(ocorrencia, TipoHistorico.STATUS, descricao);
        return new OcorrenciaResponse(ocorrencia);
    }

    @Transactional
    public TriagemResponse alterarPrioridade(Long id, AtualizacaoPrioridadeRequest dados) {
        Ocorrencia ocorrencia = buscarParaAtualizacao(id);
        Prioridade prioridadeAnterior = ocorrencia.getPrioridade();
        ocorrencia.definirPrioridadeManual(dados.prioridade());
        historicoService.registrar(ocorrencia, TipoHistorico.PRIORIDADE,
                "Prioridade definida pelo gestor: de " + prioridadeAnterior + " para " + dados.prioridade());
        return new TriagemResponse(ocorrencia);
    }

    public List<HistoricoResponse> listarHistorico(Long id) {
        buscarEntidade(id);
        return historicoService.listar(id);
    }

    public ResumoOcorrenciasResponse resumir() {
        return new ResumoOcorrenciasResponse(
                ocorrenciaRepository.count(),
                ocorrenciaRepository.countByStatus(StatusOcorrencia.ABERTA),
                ocorrenciaRepository.countByStatus(StatusOcorrencia.EM_ANALISE),
                ocorrenciaRepository.countByStatus(StatusOcorrencia.RESOLVIDA),
                ocorrenciaRepository.countByPrioridade(Prioridade.ALTA),
                ocorrenciaRepository.countByPrioridade(Prioridade.MEDIA),
                ocorrenciaRepository.countByPrioridade(Prioridade.BAIXA));
    }

    @Transactional
    public Ocorrencia registrarResultadoAnalise(Long id, ResultadoAnaliseIa resultado) {
        Ocorrencia ocorrencia = buscarParaAtualizacao(id);
        boolean prioridadeAplicada = ocorrencia.registrarAnaliseIa(
                resultado.categoria(), resultado.prioridade(), resultado.justificativa());
        String descricao = "Análise da IA concluída: categoria sugerida " + resultado.categoria().getCodigo()
                + ", prioridade sugerida " + resultado.prioridade()
                + (prioridadeAplicada ? " (aplicada à ocorrência)" : " (prioridade atual mantida)");
        historicoService.registrar(ocorrencia, TipoHistorico.ANALISE_IA, descricao);
        return ocorrencia;
    }

    @Transactional
    public void registrarFalhaAnalise(Long id, String motivo) {
        Ocorrencia ocorrencia = buscarParaAtualizacao(id);
        ocorrencia.registrarFalhaAnaliseIa();
        historicoService.registrar(ocorrencia, TipoHistorico.ANALISE_IA, "Análise da IA não concluída: " + motivo);
    }

    public Ocorrencia buscarEntidade(Long id) {
        return ocorrenciaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ocorrência " + id + " não encontrada"));
    }

    public void verificarDonoOuGestor(Ocorrencia ocorrencia, Usuario usuario) {
        if (!usuario.isGestor() && !ocorrencia.pertenceA(usuario)) {
            throw new AcessoNegadoException("Somente o cidadão que registrou a ocorrência ou um gestor pode fazer esta operação");
        }
    }

    private Ocorrencia buscarParaAtualizacao(Long id) {
        return ocorrenciaRepository.buscarParaAtualizacao(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ocorrência " + id + " não encontrada"));
    }

    private void validarOrdenacao(Sort ordenacao) {
        for (Sort.Order ordem : ordenacao) {
            if (!CAMPOS_ORDENACAO.contains(ordem.getProperty())) {
                throw new RequisicaoInvalidaException("Campo de ordenação inválido: " + ordem.getProperty()
                        + ". Use um destes: " + String.join(", ", CAMPOS_ORDENACAO.stream().sorted().toList()));
            }
        }
    }
}
