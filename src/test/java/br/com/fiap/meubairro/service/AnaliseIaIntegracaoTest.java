package br.com.fiap.meubairro.service;

import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.support.ChatModelFalso;
import br.com.fiap.meubairro.support.IntegracaoTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnaliseIaIntegracaoTest extends IntegracaoTestBase {

    @Test
    void analiseDevolveSugestaoDaIaEAplicaAPrioridade() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(token, "Poste apagado", "O poste da esquina está apagado e a rua fica escura").get("id").asLong();

        mockMvc.perform(post("/ocorrencias/" + id + "/analise-ia").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocorrenciaId").value(id))
                .andExpect(jsonPath("$.prioridade").value("ALTA"))
                .andExpect(jsonPath("$.statusAnaliseIa").value("CONCLUIDA"))
                .andExpect(jsonPath("$.categoriaSugerida").value("ILUMINACAO"))
                .andExpect(jsonPath("$.prioridadeSugerida").value("ALTA"))
                .andExpect(jsonPath("$.justificativa", containsString("Poste apagado")))
                .andExpect(jsonPath("$.dataAnalise").isNotEmpty());

        mockMvc.perform(get("/ocorrencias/" + id).header("Authorization", token))
                .andExpect(jsonPath("$.categoria").value("OUTRO"));
        assertThat(tiposDoHistorico(id, token)).contains("ANALISE_IA");
    }

    @Test
    void gestorTambemPodeSolicitarAnalise() throws Exception {
        long id = criarOcorrencia(tokenDeNovoUsuario(Perfil.CIDADAO), "Buraco na avenida", "Buraco grande na faixa da direita").get("id").asLong();

        mockMvc.perform(post("/ocorrencias/" + id + "/analise-ia").header("Authorization", tokenDeNovoUsuario(Perfil.GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaSugerida").value("BURACO"));
    }

    @Test
    void outroCidadaoNaoSolicitaAnalise() throws Exception {
        long idDoJoao = criarOcorrencia(tokenDeNovoUsuario(Perfil.CIDADAO), "Poste apagado", "Poste apagado na rua do João").get("id").asLong();

        mockMvc.perform(post("/ocorrencias/" + idDoJoao + "/analise-ia").header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void iaForaDoArDevolve503EMantemAOcorrencia() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(token, "Problema na rua", "Relato para teste " + ChatModelFalso.SIMULAR_IA_FORA_DO_AR).get("id").asLong();

        mockMvc.perform(post("/ocorrencias/" + id + "/analise-ia").header("Authorization", token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message", containsString("indisponível")));

        mockMvc.perform(get("/ocorrencias/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABERTA"));
        mockMvc.perform(get("/ocorrencias/" + id + "/triagem").header("Authorization", token))
                .andExpect(jsonPath("$.prioridade").value("MEDIA"))
                .andExpect(jsonPath("$.statusAnaliseIa").value("FALHA"));
    }

    @Test
    void respostaDaIaForaDoPadraoEhRecusada() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(token, "Problema na rua", "Relato para teste " + ChatModelFalso.SIMULAR_RESPOSTA_INVALIDA).get("id").asLong();

        mockMvc.perform(post("/ocorrencias/" + id + "/analise-ia").header("Authorization", token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("fora do padrão")));

        mockMvc.perform(get("/ocorrencias/" + id + "/triagem").header("Authorization", token))
                .andExpect(jsonPath("$.statusAnaliseIa").value("FALHA"))
                .andExpect(jsonPath("$.categoriaSugerida").isEmpty());
    }

    @Test
    void respostaDaIaQueNaoEhJsonEhRecusada() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(token, "Problema na rua", "Relato para teste " + ChatModelFalso.SIMULAR_JSON_QUEBRADO).get("id").asLong();

        mockMvc.perform(post("/ocorrencias/" + id + "/analise-ia").header("Authorization", token))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void cadastroFuncionaComIaForaDoArETriagemAutomaticaRegistraFalha() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(token, "Problema sem IA", "Relato para teste " + ChatModelFalso.SIMULAR_IA_FORA_DO_AR).get("id").asLong();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                mockMvc.perform(get("/ocorrencias/" + id + "/triagem").header("Authorization", token))
                        .andExpect(jsonPath("$.statusAnaliseIa").value("FALHA")));
        mockMvc.perform(get("/ocorrencias/" + id).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("ABERTA"));
    }

    @Test
    void iaNaoSobrescrevePrioridadeDefinidaPeloGestor() throws Exception {
        String tokenCidadao = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(tokenCidadao, "Poste piscando", "O poste da praça fica piscando a noite toda").get("id").asLong();

        mockMvc.perform(patch("/ocorrencias/" + id + "/prioridade")
                        .header("Authorization", tokenDeNovoUsuario(Perfil.GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prioridade\": \"BAIXA\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/ocorrencias/" + id + "/analise-ia").header("Authorization", tokenCidadao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prioridade").value("BAIXA"))
                .andExpect(jsonPath("$.prioridadeSugerida").value("ALTA"));
    }

    @Test
    void analiseDeOcorrenciaInexistenteRetorna404() throws Exception {
        mockMvc.perform(post("/ocorrencias/999999/analise-ia").header("Authorization", tokenDeNovoUsuario(Perfil.GESTOR)))
                .andExpect(status().isNotFound());
    }
}
