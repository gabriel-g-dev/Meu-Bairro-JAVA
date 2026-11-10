package br.com.fiap.meubairro.repository;

import br.com.fiap.meubairro.entity.Categoria;
import br.com.fiap.meubairro.entity.HistoricoOcorrencia;
import br.com.fiap.meubairro.entity.Ocorrencia;
import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.entity.Prioridade;
import br.com.fiap.meubairro.entity.StatusOcorrencia;
import br.com.fiap.meubairro.entity.TipoHistorico;
import br.com.fiap.meubairro.entity.Usuario;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class OcorrenciaRepositoryTest {

    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private HistoricoOcorrenciaRepository historicoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Usuario maria;
    private Usuario joao;
    private Categoria lixo;
    private Categoria buraco;

    @BeforeEach
    void prepararDados() {
        maria = usuarioRepository.save(new Usuario("Maria", "maria@teste.com", "hash", Perfil.CIDADAO));
        joao = usuarioRepository.save(new Usuario("João", "joao@teste.com", "hash", Perfil.CIDADAO));
        lixo = categoriaRepository.save(new Categoria("LIXO", "Lixo e entulho", "Lixo acumulado"));
        buraco = categoriaRepository.save(new Categoria("BURACO", "Buraco na via", "Buracos no asfalto"));
    }

    @Test
    void listagemCompletaVemDaMaisRecenteParaAMaisAntiga() {
        Ocorrencia primeira = salvar("Primeira", maria, lixo);
        Ocorrencia segunda = salvar("Segunda", joao, buraco);
        entityManager.flush();
        entityManager.clear();

        List<Ocorrencia> todas = ocorrenciaRepository.findAllByOrderByDataCriacaoDescIdDesc();

        assertThat(todas).extracting(Ocorrencia::getId).containsExactly(segunda.getId(), primeira.getId());
        assertThat(todas.get(0).getCategoria().getCodigo()).isEqualTo("BURACO");
        assertThat(todas.get(0).getUsuario().getNome()).isEqualTo("João");
    }

    @Test
    void buscaPaginadaFiltraPorStatusCategoriaEPrioridade() {
        salvar("Ocorrência 1", maria, lixo);
        salvar("Ocorrência 2", maria, lixo);
        Ocorrencia emAnalise = salvar("Ocorrência 3", maria, lixo);
        emAnalise.alterarStatus(StatusOcorrencia.EM_ANALISE);
        salvar("Ocorrência do João", joao, buraco);
        entityManager.flush();
        entityManager.clear();

        Page<Ocorrencia> paginaDeLixo = ocorrenciaRepository.buscar(null, "LIXO", null, PageRequest.of(0, 2, Sort.by("id")));
        assertThat(paginaDeLixo.getTotalElements()).isEqualTo(3);
        assertThat(paginaDeLixo.getTotalPages()).isEqualTo(2);
        assertThat(paginaDeLixo.getContent()).extracting(Ocorrencia::getTitulo).containsExactly("Ocorrência 1", "Ocorrência 2");

        Page<Ocorrencia> somenteEmAnalise = ocorrenciaRepository.buscar(StatusOcorrencia.EM_ANALISE, null, null, PageRequest.of(0, 10));
        assertThat(somenteEmAnalise.getContent()).extracting(Ocorrencia::getTitulo).containsExactly("Ocorrência 3");

        Page<Ocorrencia> todasDePrioridadeMedia = ocorrenciaRepository.buscar(null, null, Prioridade.MEDIA, PageRequest.of(0, 10));
        assertThat(todasDePrioridadeMedia.getTotalElements()).isEqualTo(4);
    }

    @Test
    void localizacaoEFotoSaoPersistidas() {
        Ocorrencia ocorrencia = ocorrenciaRepository.save(new Ocorrencia("Buraco", "Buraco na esquina", "Rua A, 10",
                -23.55, -46.63, "https://conta.blob.core.windows.net/fotos/a.jpg", buraco, maria));
        entityManager.flush();
        entityManager.clear();

        Ocorrencia lida = ocorrenciaRepository.findById(ocorrencia.getId()).orElseThrow();

        assertThat(lida.getLatitude()).isEqualTo(-23.55);
        assertThat(lida.getLongitude()).isEqualTo(-46.63);
        assertThat(lida.getFotoUrl()).isEqualTo("https://conta.blob.core.windows.net/fotos/a.jpg");
    }

    @Test
    void contaOcorrenciasPorStatusEPrioridade() {
        salvar("Ocorrência 1", maria, lixo);
        Ocorrencia resolvida = salvar("Ocorrência 2", joao, lixo);
        resolvida.alterarStatus(StatusOcorrencia.RESOLVIDA);
        resolvida.definirPrioridadeManual(Prioridade.ALTA);
        entityManager.flush();

        assertThat(ocorrenciaRepository.countByStatus(StatusOcorrencia.ABERTA)).isEqualTo(1);
        assertThat(ocorrenciaRepository.countByStatus(StatusOcorrencia.RESOLVIDA)).isEqualTo(1);
        assertThat(ocorrenciaRepository.countByPrioridade(Prioridade.ALTA)).isEqualTo(1);
    }

    @Test
    void excluirOcorrenciaRemoveOHistoricoPeloBanco() {
        Ocorrencia ocorrencia = salvar("Ocorrência", maria, lixo);
        historicoRepository.save(new HistoricoOcorrencia(ocorrencia, TipoHistorico.CRIACAO, "Ocorrência registrada"));
        historicoRepository.save(new HistoricoOcorrencia(ocorrencia, TipoHistorico.FILA, "Evento recebido"));
        entityManager.flush();
        entityManager.clear();

        ocorrenciaRepository.deleteById(ocorrencia.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(ocorrenciaRepository.findById(ocorrencia.getId())).isEmpty();
        assertThat(historicoRepository.findByOcorrenciaIdOrderByIdAsc(ocorrencia.getId())).isEmpty();
    }

    @Test
    void buscaParaAtualizacaoBloqueiaALinhaDaOcorrencia() {
        Ocorrencia ocorrencia = salvar("Ocorrência", maria, lixo);
        entityManager.flush();
        entityManager.clear();

        Ocorrencia bloqueada = ocorrenciaRepository.buscarParaAtualizacao(ocorrencia.getId()).orElseThrow();

        assertThat(entityManager.getEntityManager().getLockMode(bloqueada)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void emailDeUsuarioEhUnicoNoBanco() {
        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(new Usuario("Outra Maria", "maria@teste.com", "hash", Perfil.CIDADAO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void codigoDeCategoriaEhUnicoEBuscadoSemDiferenciarMaiusculas() {
        assertThat(categoriaRepository.findByCodigoIgnoreCase("lixo")).contains(lixo);
        assertThatThrownBy(() -> categoriaRepository.saveAndFlush(new Categoria("LIXO", "Outro lixo", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Ocorrencia salvar(String titulo, Usuario dono, Categoria categoria) {
        return ocorrenciaRepository.save(new Ocorrencia(titulo, "Descrição de " + titulo, "Rua A, 10", null, null, null, categoria, dono));
    }
}
