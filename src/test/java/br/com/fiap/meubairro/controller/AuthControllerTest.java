package br.com.fiap.meubairro.controller;

import br.com.fiap.meubairro.dto.CadastroUsuarioRequest;
import br.com.fiap.meubairro.dto.LoginRequest;
import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.support.IntegracaoTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends IntegracaoTestBase {

    @Test
    void registerCriaCidadaoComSenhaCriptografada() throws Exception {
        String email = emailUnico("gabriel");

        JsonNode resposta = lerJson(mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CadastroUsuarioRequest("Gabriel", email, "123456"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Gabriel"))
                .andExpect(jsonPath("$.email").value(email))
                .andReturn());

        assertThat(nomesDosCampos(resposta)).containsExactly("id", "nome", "email");
        Usuario salvo = usuarioRepository.findByEmail(email).orElseThrow();
        assertThat(salvo.getPerfil()).isEqualTo(Perfil.CIDADAO);
        assertThat(salvo.getPassword()).isNotEqualTo("123456").startsWith("$2a$");
        assertThat(passwordEncoder.matches("123456", salvo.getPassword())).isTrue();
    }

    @Test
    void registerSempreCriaCidadaoMesmoQuandoOJsonPedeGestor() throws Exception {
        String email = emailUnico("tentativa");
        String corpo = """
                {"nome": "Tentativa", "email": "%s", "senha": "123456", "perfil": "GESTOR"}
                """.formatted(email);

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());

        assertThat(usuarioRepository.findByEmail(email).orElseThrow().getPerfil()).isEqualTo(Perfil.CIDADAO);
    }

    @Test
    void registerGuardaEmailEmMinusculas() throws Exception {
        String email = emailUnico("JOAO").toUpperCase();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CadastroUsuarioRequest("João", email, "123456"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()));
    }

    @Test
    void registerComEmailDuplicadoRetorna409ComMessage() throws Exception {
        String corpo = json(new CadastroUsuarioRequest("Maria", emailUnico("repetido"), "123456"));
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um usuário cadastrado com este e-mail"));
    }

    @Test
    void registerComEmailInvalidoRetorna400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CadastroUsuarioRequest("Maria", "email-sem-arroba", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Existem campos inválidos na requisição"))
                .andExpect(jsonPath("$.path").value("/auth/register"))
                .andExpect(jsonPath("$.fields[0].field").value("email"))
                .andExpect(jsonPath("$.fields[0].message").value("O e-mail informado é inválido"));
    }

    @Test
    void registerSemCamposObrigatoriosRetorna400() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[*].field", containsInAnyOrder("nome", "email", "senha")));
    }

    @Test
    void registerComSenhaCurtaRetorna400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CadastroUsuarioRequest("Maria", emailUnico("maria"), "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("senha"));
    }

    @Test
    void loginRetornaTokenEUsuarioNoFormatoDoMobile() throws Exception {
        Usuario usuario = criarUsuario(Perfil.CIDADAO);

        JsonNode resposta = lerJson(mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(usuario.getEmail(), SENHA_PADRAO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.usuario.id").value(usuario.getId()))
                .andExpect(jsonPath("$.usuario.nome").value(usuario.getNome()))
                .andExpect(jsonPath("$.usuario.email").value(usuario.getEmail()))
                .andReturn());

        assertThat(nomesDosCampos(resposta)).containsExactly("token", "usuario");
        assertThat(nomesDosCampos(resposta.get("usuario"))).containsExactly("id", "nome", "email");
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        Usuario usuario = criarUsuario(Perfil.CIDADAO);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(usuario.getEmail(), "senha-errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    void loginDeEmailInexistenteRetorna401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(emailUnico("ninguem"), "123456"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    void loginValidaCamposObrigatorios() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\", \"senha\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[*].field", containsInAnyOrder("email", "senha")));
    }

    private List<String> nomesDosCampos(JsonNode objeto) {
        List<String> nomes = new ArrayList<>();
        objeto.fieldNames().forEachRemaining(nomes::add);
        return nomes;
    }
}
