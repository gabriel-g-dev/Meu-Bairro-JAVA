package br.com.fiap.meubairro.security;

import br.com.fiap.meubairro.dto.CadastroUsuarioRequest;
import br.com.fiap.meubairro.dto.LoginRequest;
import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.support.IntegracaoTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SegurancaTest extends IntegracaoTestBase {

    @Value("${meubairro.jwt.secret}")
    private String segredoJwt;

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/ocorrencias"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message", containsString("Autenticação necessária")));
    }

    @Test
    void tokenMalformadoRetorna401() throws Exception {
        mockMvc.perform(get("/ocorrencias").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token inválido ou expirado"));
    }

    @Test
    void tokenAssinadoPorOutraChaveRetorna401() throws Exception {
        Usuario usuario = criarUsuario(Perfil.CIDADAO);
        TokenService emissorDesconhecido = new TokenService("uma-chave-diferente-da-api-com-mais-de-32-caracteres", 120);

        mockMvc.perform(get("/ocorrencias").header("Authorization", "Bearer " + emissorDesconhecido.gerarToken(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token inválido ou expirado"));
    }

    @Test
    void tokenExpiradoRetorna401() throws Exception {
        Usuario usuario = criarUsuario(Perfil.CIDADAO);
        TokenService emissorComTokenVencido = new TokenService(segredoJwt, -5);

        mockMvc.perform(get("/ocorrencias").header("Authorization", "Bearer " + emissorComTokenVencido.gerarToken(usuario)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token inválido ou expirado"));
    }

    @Test
    void tokenDeUsuarioExcluidoRetorna401() throws Exception {
        Usuario usuario = criarUsuario(Perfil.CIDADAO);
        String token = fazerLogin(usuario);
        usuarioRepository.delete(usuario);

        mockMvc.perform(get("/usuarios/me").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("O usuário do token não existe mais"));
    }

    @Test
    void tokenValidoAcessaRotaProtegida() throws Exception {
        Usuario usuario = criarUsuario(Perfil.CIDADAO);

        mockMvc.perform(get("/usuarios/me").header("Authorization", fazerLogin(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(usuario.getEmail()))
                .andExpect(jsonPath("$.perfil").value("CIDADAO"));
    }

    @Test
    void cidadaoNaoAlteraStatusNemPrioridade() throws Exception {
        String tokenCidadao = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(tokenCidadao, "Lixo na calçada", "Sacos de lixo acumulados na calçada").get("id").asLong();

        mockMvc.perform(patch("/ocorrencias/" + id + "/status")
                        .header("Authorization", tokenCidadao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVIDA\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Você não tem permissão para acessar este recurso"));

        mockMvc.perform(patch("/ocorrencias/" + id + "/prioridade")
                        .header("Authorization", tokenCidadao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prioridade\": \"ALTA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cidadaoNaoCadastraGestorNemVeResumo() throws Exception {
        String tokenCidadao = tokenDeNovoUsuario(Perfil.CIDADAO);

        mockMvc.perform(post("/usuarios/gestores")
                        .header("Authorization", tokenCidadao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CadastroUsuarioRequest("Novo Gestor", emailUnico("gestor"), "123456"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/ocorrencias/resumo").header("Authorization", tokenCidadao))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestorNaoRegistraOcorrencia() throws Exception {
        mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", "Teste do gestor", "Gestor tentando registrar", "OUTRO")
                        .header("Authorization", tokenDeNovoUsuario(Perfil.GESTOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestorCadastraOutroGestorQueAcessaRotasDeGestor() throws Exception {
        String emailNovoGestor = emailUnico("novo-gestor");

        mockMvc.perform(post("/usuarios/gestores")
                        .header("Authorization", tokenDeNovoUsuario(Perfil.GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CadastroUsuarioRequest("Novo Gestor", emailNovoGestor, "123456"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("GESTOR"));

        String respostaLogin = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(emailNovoGestor, "123456"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + objectMapper.readTree(respostaLogin).get("token").asText();

        mockMvc.perform(get("/ocorrencias/resumo").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    void corsAceitaOrigemConfiguradaERecusaOrigemDesconhecida() throws Exception {
        mockMvc.perform(options("/ocorrencias")
                        .header("Origin", "http://localhost:8081")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"));

        mockMvc.perform(options("/ocorrencias")
                        .header("Origin", "https://site-desconhecido.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void documentacaoSwaggerEhPublica() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/ocorrencias/{id}/analise-ia")))
                .andExpect(content().string(containsString("/auth/register")))
                .andExpect(content().string(containsString("bearerAuth")));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void rotasComPrefixoApiSeguemAsMesmasRegrasDeSeguranca() throws Exception {
        Usuario cidadao = criarUsuario(Perfil.CIDADAO);
        String tokenGestor = tokenDeNovoUsuario(Perfil.GESTOR);

        String resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(cidadao.getEmail(), SENHA_PADRAO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value(cidadao.getEmail()))
                .andReturn().getResponse().getContentAsString();
        String tokenCidadao = "Bearer " + objectMapper.readTree(resposta).get("token").asText();

        mockMvc.perform(get("/api/ocorrencias").header("Authorization", tokenCidadao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/api/ocorrencias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Autenticação necessária")));
        mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/api/ocorrencias", "Buraco na rua", "Buraco grande perto da esquina", "BURACO")
                        .header("Authorization", tokenGestor))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ocorrencias/resumo").header("Authorization", tokenCidadao))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/categorias").header("Authorization", tokenCidadao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"SINALIZACAO\",\"nome\":\"Sinalização\"}"))
                .andExpect(status().isForbidden());
    }
}
