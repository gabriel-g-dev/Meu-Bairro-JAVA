package br.com.fiap.meubairro.controller;

import br.com.fiap.meubairro.dto.CategoriaRequest;
import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.support.IntegracaoTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoriaControllerTest extends IntegracaoTestBase {

    @Test
    void listaAsSeisCategoriasDoContratoNaOrdem() throws Exception {
        mockMvc.perform(get("/categorias").header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$[*].codigo", contains("BURACO", "ILUMINACAO", "LIXO", "ALAGAMENTO", "CALCADA", "OUTRO")))
                .andExpect(jsonPath("$[1].nome").value("Iluminação pública"))
                .andExpect(jsonPath("$[0].descricao").isNotEmpty());
    }

    @Test
    void listarCategoriasExigeLogin() throws Exception {
        mockMvc.perform(get("/categorias"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gestorCriaConsultaAtualizaEExcluiUmaCategoria() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.GESTOR);

        JsonNode criada = criarCategoria(token, new CategoriaRequest("SINALIZACAO", "Sinalização", "Placas de trânsito danificadas"));
        long id = criada.get("id").asLong();

        mockMvc.perform(get("/categorias/" + id).header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("SINALIZACAO"))
                .andExpect(jsonPath("$.nome").value("Sinalização"));

        mockMvc.perform(put("/categorias/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoriaRequest("SINALIZACAO_VIARIA", "Sinalização viária", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("SINALIZACAO_VIARIA"))
                .andExpect(jsonPath("$.descricao").isEmpty());

        mockMvc.perform(delete("/categorias/" + id).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/categorias/" + id).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Categoria " + id + " não encontrada"));
    }

    @Test
    void cidadaoNaoCriaNemAlteraNemExcluiCategorias() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long idBuraco = categoriaRepository.findByCodigoIgnoreCase("BURACO").orElseThrow().getId();
        String corpo = json(new CategoriaRequest("PRACA", "Praças", null));

        mockMvc.perform(post("/categorias").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/categorias/" + idBuraco).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/categorias/" + idBuraco).header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void categoriasDoAppNaoSaoExcluidasNemMudamDeCodigo() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.GESTOR);
        long idBuraco = categoriaRepository.findByCodigoIgnoreCase("BURACO").orElseThrow().getId();

        mockMvc.perform(delete("/categorias/" + idBuraco).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("As categorias usadas pelo app não podem ser excluídas"));
        mockMvc.perform(put("/categorias/" + idBuraco).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoriaRequest("BURACOS", "Buracos", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O código das categorias usadas pelo app não pode ser alterado"));
    }

    @Test
    void codigoRepetidoOuInvalidoEhRecusado() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.GESTOR);

        mockMvc.perform(post("/categorias").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoriaRequest("BURACO", "Outro buraco", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe uma categoria com o código BURACO"));
        mockMvc.perform(post("/categorias").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoriaRequest("praça", "", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[*].field", containsInAnyOrder("codigo", "nome")));
    }

    @Test
    void categoriaComOcorrenciasNaoPodeSerExcluida() throws Exception {
        String tokenGestor = tokenDeNovoUsuario(Perfil.GESTOR);
        String tokenCidadao = tokenDeNovoUsuario(Perfil.CIDADAO);
        long idCategoria = criarCategoria(tokenGestor, new CategoriaRequest("ARVORE", "Árvores", "Galhos e árvores caídas")).get("id").asLong();

        JsonNode ocorrencia = lerJson(mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias",
                                "Galho caído na rua", "Um galho grande caiu e bloqueou a rua", "ARVORE")
                        .header("Authorization", tokenCidadao))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria").value("ARVORE"))
                .andReturn());

        mockMvc.perform(delete("/categorias/" + idCategoria).header("Authorization", tokenGestor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A categoria tem ocorrências vinculadas e não pode ser excluída"));

        mockMvc.perform(delete("/ocorrencias/" + ocorrencia.get("id").asLong()).header("Authorization", tokenCidadao))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/categorias/" + idCategoria).header("Authorization", tokenGestor))
                .andExpect(status().isNoContent());
    }

    private JsonNode criarCategoria(String token, CategoriaRequest dados) throws Exception {
        return lerJson(mockMvc.perform(post("/categorias").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dados)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/categorias/\\d+")))
                .andReturn());
    }
}
