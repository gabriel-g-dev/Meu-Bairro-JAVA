package br.com.fiap.meubairro.exception;

import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.support.IntegracaoTestBase;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrosApiTest extends IntegracaoTestBase {

    @Test
    void jsonMalformadoRetornaErroPadronizado() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("O corpo da requisição está ausente ou não é um JSON válido"))
                .andExpect(jsonPath("$.path").value("/auth/login"))
                .andExpect(jsonPath("$.fields", hasSize(0)));
    }

    @Test
    void corpoAusenteRetorna400() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O corpo da requisição está ausente ou não é um JSON válido"));
    }

    @Test
    void valorDeEnumInvalidoInformaOsValoresAceitos() throws Exception {
        mockMvc.perform(patch("/ocorrencias/1/status")
                        .header("Authorization", tokenDeNovoUsuario(Perfil.GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"EM_ANDAMENTO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Valor inválido para o campo 'status'")))
                .andExpect(jsonPath("$.message", containsString("ABERTA, EM_ANALISE, RESOLVIDA")));
    }

    @Test
    void idNaoNumericoNaUrlRetorna400() throws Exception {
        mockMvc.perform(get("/ocorrencias/abc").header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor inválido para o parâmetro 'id': abc"));
    }

    @Test
    void filtroDeStatusInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/ocorrencias/paginado?status=FECHADA").header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Valores aceitos")));
    }

    @Test
    void ordenacaoPorCampoNaoPermitidoRetorna400() throws Exception {
        mockMvc.perform(get("/ocorrencias/paginado?sort=usuario.senha").header("Authorization", tokenDeNovoUsuario(Perfil.GESTOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Campo de ordenação inválido")));
    }

    @Test
    void rotaInexistenteRetorna404Padronizado() throws Exception {
        mockMvc.perform(get("/rota-que-nao-existe").header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Recurso não encontrado"));
    }

    @Test
    void metodoNaoSuportadoRetorna405() throws Exception {
        mockMvc.perform(delete("/categorias").header("Authorization", tokenDeNovoUsuario(Perfil.GESTOR)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Método HTTP não suportado nesta rota"));
    }

    @Test
    void tipoDeConteudoNaoSuportadoRetorna415() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.TEXT_PLAIN).content("email=a@b.com"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message", containsString("application/json")));
    }

    @Test
    void errosTratadosForaDosControllersTambemTemMessage() throws Exception {
        mockMvc.perform(get("/error")
                        .accept(MediaType.APPLICATION_JSON)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/ocorrencias//historico"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Requisição inválida"))
                .andExpect(jsonPath("$.path").value("/ocorrencias//historico"))
                .andExpect(jsonPath("$.fields", hasSize(0)));
    }
}
