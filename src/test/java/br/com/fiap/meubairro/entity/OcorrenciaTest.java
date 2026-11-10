package br.com.fiap.meubairro.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class OcorrenciaTest {

    private final Categoria iluminacao = new Categoria("ILUMINACAO", "Iluminação pública", "Postes apagados");

    @Test
    void novaOcorrenciaComecaAbertaComPrioridadeMediaEAnalisePendente() {
        Ocorrencia ocorrencia = novaOcorrencia(1L);

        assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.ABERTA);
        assertThat(ocorrencia.getPrioridade()).isEqualTo(Prioridade.MEDIA);
        assertThat(ocorrencia.getStatusAnaliseIa()).isEqualTo(StatusAnaliseIa.PENDENTE);
        assertThat(ocorrencia.getDataCriacao()).isNotNull();
        assertThat(ocorrencia.getLatitude()).isEqualTo(-23.5);
        assertThat(ocorrencia.getFotoUrl()).isNull();
    }

    @Test
    void analiseDaIaAplicaPrioridadeQuandoOcorrenciaEstaAberta() {
        Ocorrencia ocorrencia = novaOcorrencia(1L);

        boolean aplicada = ocorrencia.registrarAnaliseIa(iluminacao, Prioridade.ALTA, "Rua escura");

        assertThat(aplicada).isTrue();
        assertThat(ocorrencia.getPrioridade()).isEqualTo(Prioridade.ALTA);
        assertThat(ocorrencia.getStatusAnaliseIa()).isEqualTo(StatusAnaliseIa.CONCLUIDA);
        assertThat(ocorrencia.getCategoriaSugerida()).isEqualTo(iluminacao);
        assertThat(ocorrencia.getJustificativaIa()).isEqualTo("Rua escura");
    }

    @Test
    void analiseDaIaNaoSobrescrevePrioridadeDefinidaPeloGestor() {
        Ocorrencia ocorrencia = novaOcorrencia(1L);
        ocorrencia.definirPrioridadeManual(Prioridade.BAIXA);

        boolean aplicada = ocorrencia.registrarAnaliseIa(iluminacao, Prioridade.ALTA, "Rua escura");

        assertThat(aplicada).isFalse();
        assertThat(ocorrencia.getPrioridade()).isEqualTo(Prioridade.BAIXA);
        assertThat(ocorrencia.getPrioridadeSugerida()).isEqualTo(Prioridade.ALTA);
    }

    @Test
    void analiseDaIaNaoAlteraPrioridadeDeOcorrenciaEmAnalise() {
        Ocorrencia ocorrencia = novaOcorrencia(1L);
        ocorrencia.alterarStatus(StatusOcorrencia.EM_ANALISE);

        boolean aplicada = ocorrencia.registrarAnaliseIa(iluminacao, Prioridade.ALTA, "Rua escura");

        assertThat(aplicada).isFalse();
        assertThat(ocorrencia.getPrioridade()).isEqualTo(Prioridade.MEDIA);
    }

    @Test
    void falhaNaAnaliseMarcaStatusFalha() {
        Ocorrencia ocorrencia = novaOcorrencia(1L);

        ocorrencia.registrarFalhaAnaliseIa();

        assertThat(ocorrencia.getStatusAnaliseIa()).isEqualTo(StatusAnaliseIa.FALHA);
        assertThat(ocorrencia.getPrioridade()).isEqualTo(Prioridade.MEDIA);
    }

    @Test
    void alterarFotoTrocaAUrl() {
        Ocorrencia ocorrencia = novaOcorrencia(1L);

        ocorrencia.alterarFoto("https://conta.blob.core.windows.net/fotos/nova.jpg");

        assertThat(ocorrencia.getFotoUrl()).isEqualTo("https://conta.blob.core.windows.net/fotos/nova.jpg");
        assertThat(ocorrencia.getDataAtualizacao()).isNotNull();
    }

    @Test
    void pertenceAComparaPeloIdDoUsuario() {
        Ocorrencia ocorrencia = novaOcorrencia(1L);

        assertThat(ocorrencia.pertenceA(usuario(1L))).isTrue();
        assertThat(ocorrencia.pertenceA(usuario(2L))).isFalse();
    }

    private Ocorrencia novaOcorrencia(Long idDono) {
        return new Ocorrencia("Poste apagado", "Poste apagado na esquina", "Rua A, 10", -23.5, -46.6, null, iluminacao, usuario(idDono));
    }

    private Usuario usuario(Long id) {
        Usuario usuario = new Usuario("Usuário", "usuario" + id + "@teste.com", "senha", Perfil.CIDADAO);
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }
}
