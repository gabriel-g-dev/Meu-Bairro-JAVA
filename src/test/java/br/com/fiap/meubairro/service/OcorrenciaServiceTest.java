package br.com.fiap.meubairro.service;

import br.com.fiap.meubairro.dto.AtualizacaoStatusRequest;
import br.com.fiap.meubairro.dto.OcorrenciaForm;
import br.com.fiap.meubairro.dto.OcorrenciaResponse;
import br.com.fiap.meubairro.entity.Categoria;
import br.com.fiap.meubairro.entity.Ocorrencia;
import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.entity.StatusOcorrencia;
import br.com.fiap.meubairro.entity.TipoHistorico;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.exception.AcessoNegadoException;
import br.com.fiap.meubairro.exception.ArmazenamentoFotoException;
import br.com.fiap.meubairro.exception.RecursoNaoEncontradoException;
import br.com.fiap.meubairro.exception.RegraNegocioException;
import br.com.fiap.meubairro.exception.RequisicaoInvalidaException;
import br.com.fiap.meubairro.messaging.OcorrenciaEventoPublisher;
import br.com.fiap.meubairro.repository.OcorrenciaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcorrenciaServiceTest {

    @Mock
    private OcorrenciaRepository ocorrenciaRepository;

    @Mock
    private CategoriaService categoriaService;

    @Mock
    private HistoricoService historicoService;

    @Mock
    private FotoService fotoService;

    @Mock
    private OcorrenciaEventoPublisher eventoPublisher;

    @InjectMocks
    private OcorrenciaService ocorrenciaService;

    private final Usuario cidadao = usuario(1L, Perfil.CIDADAO);
    private final Usuario outroCidadao = usuario(2L, Perfil.CIDADAO);
    private final Usuario gestor = usuario(3L, Perfil.GESTOR);
    private final Categoria categoria = categoria(10L);

    @Test
    void criarSalvaRegistraHistoricoEPublicaEvento() {
        when(categoriaService.buscarPorCodigo("LIXO")).thenReturn(categoria);
        when(eventoPublisher.publicarOcorrenciaCriada(any())).thenReturn(true);

        OcorrenciaResponse resposta = ocorrenciaService.criar(formulario(null), cidadao);

        assertThat(resposta.status()).isEqualTo(StatusOcorrencia.ABERTA);
        assertThat(resposta.categoria()).isEqualTo("LIXO");
        assertThat(resposta.usuarioId()).isEqualTo(1L);
        assertThat(resposta.latitude()).isEqualTo(-23.55);
        verify(ocorrenciaRepository).save(any(Ocorrencia.class));
        verify(historicoService).registrar(any(), eq(TipoHistorico.CRIACAO), anyString());
        verify(eventoPublisher).publicarOcorrenciaCriada(any(Ocorrencia.class));
        verify(historicoService, never()).registrar(any(), eq(TipoHistorico.FILA), anyString());
    }

    @Test
    void criarComFotoGuardaAUrlDevolvidaPeloArmazenamento() {
        MockMultipartFile foto = new MockMultipartFile("foto", "f.jpg", "image/jpeg", new byte[]{1});
        when(categoriaService.buscarPorCodigo("LIXO")).thenReturn(categoria);
        when(fotoService.salvar(foto)).thenReturn("https://conta.blob.core.windows.net/fotos/f.jpg");

        OcorrenciaResponse resposta = ocorrenciaService.criar(formulario(foto), cidadao);

        assertThat(resposta.fotoUrl()).isEqualTo("https://conta.blob.core.windows.net/fotos/f.jpg");
    }

    @Test
    void criarNaoSalvaNadaQuandoAFotoFalha() {
        MockMultipartFile foto = new MockMultipartFile("foto", "f.jpg", "image/jpeg", new byte[]{1});
        when(categoriaService.buscarPorCodigo("LIXO")).thenReturn(categoria);
        when(fotoService.salvar(foto)).thenThrow(new ArmazenamentoFotoException("indisponível"));

        assertThatThrownBy(() -> ocorrenciaService.criar(formulario(foto), cidadao))
                .isInstanceOf(ArmazenamentoFotoException.class);
        verify(ocorrenciaRepository, never()).save(any());
        verify(eventoPublisher, never()).publicarOcorrenciaCriada(any());
    }

    @Test
    void criarContinuaFuncionandoQuandoAFilaEstaIndisponivel() {
        when(categoriaService.buscarPorCodigo("LIXO")).thenReturn(categoria);
        when(eventoPublisher.publicarOcorrenciaCriada(any())).thenReturn(false);

        OcorrenciaResponse resposta = ocorrenciaService.criar(formulario(null), cidadao);

        assertThat(resposta).isNotNull();
        verify(historicoService).registrar(any(), eq(TipoHistorico.FILA), anyString());
    }

    @Test
    void cidadaoNaoAtualizaOcorrenciaDeOutroCidadao() {
        when(ocorrenciaRepository.buscarParaAtualizacao(5L)).thenReturn(Optional.of(ocorrencia(5L, outroCidadao)));

        assertThatThrownBy(() -> ocorrenciaService.atualizar(5L, formulario(null), cidadao))
                .isInstanceOf(AcessoNegadoException.class);
        verify(fotoService, never()).salvar(any());
        verify(historicoService, never()).registrar(any(), any(), anyString());
    }

    @Test
    void naoAtualizaOcorrenciaQueNaoEstaAberta() {
        Ocorrencia ocorrencia = ocorrencia(5L, cidadao);
        ocorrencia.alterarStatus(StatusOcorrencia.EM_ANALISE);
        when(ocorrenciaRepository.buscarParaAtualizacao(5L)).thenReturn(Optional.of(ocorrencia));

        assertThatThrownBy(() -> ocorrenciaService.atualizar(5L, formulario(null), cidadao))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ABERTA");
    }

    @Test
    void atualizarSemLocalizacaoMantemOsValoresAtuais() {
        Ocorrencia ocorrencia = new Ocorrencia("Lixo", "Lixo acumulado na esquina", "Rua A", -10.0, -20.0, null, categoria, cidadao);
        ReflectionTestUtils.setField(ocorrencia, "id", 5L);
        when(ocorrenciaRepository.buscarParaAtualizacao(5L)).thenReturn(Optional.of(ocorrencia));
        when(categoriaService.buscarPorCodigo("LIXO")).thenReturn(categoria);
        OcorrenciaForm semLocalizacao = new OcorrenciaForm("Lixo acumulado", "Lixo acumulado na esquina há dias",
                "LIXO", "Rua A, 10", null, null, null);

        OcorrenciaResponse resposta = ocorrenciaService.atualizar(5L, semLocalizacao, cidadao);

        assertThat(resposta.latitude()).isEqualTo(-10.0);
        assertThat(resposta.longitude()).isEqualTo(-20.0);
        assertThat(resposta.titulo()).isEqualTo("Lixo acumulado");
    }

    @Test
    void cidadaoNaoExcluiOcorrenciaEmAnalise() {
        Ocorrencia ocorrencia = ocorrencia(5L, cidadao);
        ocorrencia.alterarStatus(StatusOcorrencia.EM_ANALISE);
        when(ocorrenciaRepository.buscarParaAtualizacao(5L)).thenReturn(Optional.of(ocorrencia));

        assertThatThrownBy(() -> ocorrenciaService.excluir(5L, cidadao)).isInstanceOf(RegraNegocioException.class);
        verify(ocorrenciaRepository, never()).delete(any());
    }

    @Test
    void cidadaoNaoExcluiOcorrenciaDeOutroCidadao() {
        when(ocorrenciaRepository.buscarParaAtualizacao(5L)).thenReturn(Optional.of(ocorrencia(5L, outroCidadao)));

        assertThatThrownBy(() -> ocorrenciaService.excluir(5L, cidadao)).isInstanceOf(AcessoNegadoException.class);
        verify(ocorrenciaRepository, never()).delete(any());
    }

    @Test
    void gestorExcluiOcorrenciaEmAnaliseEAFotoTambem() {
        Ocorrencia ocorrencia = new Ocorrencia("Lixo", "Lixo acumulado", "Rua A", null, null,
                "https://conta.blob.core.windows.net/fotos/x.jpg", categoria, cidadao);
        ocorrencia.alterarStatus(StatusOcorrencia.EM_ANALISE);
        when(ocorrenciaRepository.buscarParaAtualizacao(5L)).thenReturn(Optional.of(ocorrencia));

        ocorrenciaService.excluir(5L, gestor);

        verify(ocorrenciaRepository).delete(ocorrencia);
        verify(fotoService).excluir("https://conta.blob.core.windows.net/fotos/x.jpg");
    }

    @Test
    void naoAlteraStatusDeOcorrenciaResolvida() {
        Ocorrencia ocorrencia = ocorrencia(5L, cidadao);
        ocorrencia.alterarStatus(StatusOcorrencia.RESOLVIDA);
        when(ocorrenciaRepository.buscarParaAtualizacao(5L)).thenReturn(Optional.of(ocorrencia));

        assertThatThrownBy(() -> ocorrenciaService.alterarStatus(5L, new AtualizacaoStatusRequest(StatusOcorrencia.ABERTA, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("resolvida");
    }

    @Test
    void naoAlteraParaOMesmoStatus() {
        when(ocorrenciaRepository.buscarParaAtualizacao(5L)).thenReturn(Optional.of(ocorrencia(5L, cidadao)));

        assertThatThrownBy(() -> ocorrenciaService.alterarStatus(5L, new AtualizacaoStatusRequest(StatusOcorrencia.ABERTA, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void alterarStatusRegistraHistoricoComObservacaoDoGestor() {
        when(ocorrenciaRepository.buscarParaAtualizacao(5L)).thenReturn(Optional.of(ocorrencia(5L, cidadao)));

        OcorrenciaResponse resposta = ocorrenciaService.alterarStatus(5L,
                new AtualizacaoStatusRequest(StatusOcorrencia.EM_ANALISE, "Equipe enviada ao local"));

        ArgumentCaptor<String> descricao = ArgumentCaptor.forClass(String.class);
        verify(historicoService).registrar(any(), eq(TipoHistorico.STATUS), descricao.capture());
        assertThat(resposta.status()).isEqualTo(StatusOcorrencia.EM_ANALISE);
        assertThat(descricao.getValue()).contains("ABERTA", "EM_ANALISE", "Equipe enviada ao local");
    }

    @Test
    void listarDevolveTodasAsOcorrencias() {
        when(ocorrenciaRepository.findAllByOrderByDataCriacaoDescIdDesc())
                .thenReturn(List.of(ocorrencia(6L, outroCidadao), ocorrencia(5L, cidadao)));

        List<OcorrenciaResponse> lista = ocorrenciaService.listar();

        assertThat(lista).extracting(OcorrenciaResponse::id).containsExactly(6L, 5L);
    }

    @Test
    void listarPaginadoRepassaOsFiltros() {
        Pageable pagina = PageRequest.of(0, 10, Sort.by("dataCriacao"));
        when(categoriaService.buscarPorCodigo("lixo")).thenReturn(categoria);
        when(ocorrenciaRepository.buscar(StatusOcorrencia.ABERTA, "LIXO", null, pagina)).thenReturn(Page.empty(pagina));

        ocorrenciaService.listarPaginado(StatusOcorrencia.ABERTA, "lixo", null, pagina);

        verify(ocorrenciaRepository).buscar(eq(StatusOcorrencia.ABERTA), eq("LIXO"), isNull(), eq(pagina));
    }

    @Test
    void listarPaginadoComCampoDeOrdenacaoInvalidoFalha() {
        Pageable pagina = PageRequest.of(0, 10, Sort.by("usuario.senha"));

        assertThatThrownBy(() -> ocorrenciaService.listarPaginado(null, null, null, pagina))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("usuario.senha");
    }

    @Test
    void apenasDonoOuGestorPodeSolicitarOperacoesRestritas() {
        Ocorrencia ocorrencia = ocorrencia(5L, cidadao);

        ocorrenciaService.verificarDonoOuGestor(ocorrencia, cidadao);
        ocorrenciaService.verificarDonoOuGestor(ocorrencia, gestor);
        assertThatThrownBy(() -> ocorrenciaService.verificarDonoOuGestor(ocorrencia, outroCidadao))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void buscarOcorrenciaInexistenteFalha() {
        when(ocorrenciaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ocorrenciaService.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private OcorrenciaForm formulario(MockMultipartFile foto) {
        return new OcorrenciaForm("Lixo acumulado", "Lixo acumulado na esquina há uma semana", "LIXO",
                "Rua A, 10", -23.55, -46.63, foto);
    }

    private Ocorrencia ocorrencia(Long id, Usuario dono) {
        Ocorrencia ocorrencia = new Ocorrencia("Lixo acumulado", "Lixo acumulado na esquina", "Rua A, 10",
                null, null, null, categoria, dono);
        ReflectionTestUtils.setField(ocorrencia, "id", id);
        return ocorrencia;
    }

    private static Usuario usuario(Long id, Perfil perfil) {
        Usuario usuario = new Usuario("Usuário " + id, "usuario" + id + "@teste.com", "senha", perfil);
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    private static Categoria categoria(Long id) {
        Categoria categoria = new Categoria("LIXO", "Lixo e entulho", "Lixo acumulado");
        ReflectionTestUtils.setField(categoria, "id", id);
        return categoria;
    }
}
