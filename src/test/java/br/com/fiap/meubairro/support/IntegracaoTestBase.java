package br.com.fiap.meubairro.support;

import br.com.fiap.meubairro.dto.LoginRequest;
import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.repository.CategoriaRepository;
import br.com.fiap.meubairro.repository.UsuarioRepository;
import br.com.fiap.meubairro.service.FotoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IaTesteConfig.class)
public abstract class IntegracaoTestBase {

    protected static final String SENHA_PADRAO = "senha123";

    private static final boolean RABBITMQ_REAL = "true".equals(System.getenv("RABBITMQ_TESTE_REAL"));
    private static final BrokerAmqpEmbarcado BROKER = RABBITMQ_REAL ? null : BrokerAmqpEmbarcado.iniciar();

    @DynamicPropertySource
    static void configurarRabbitMq(DynamicPropertyRegistry registry) {
        if (BROKER != null) {
            registry.add("spring.rabbitmq.host", () -> "localhost");
            registry.add("spring.rabbitmq.port", BROKER::getPorta);
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected CategoriaRepository categoriaRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @MockitoBean
    protected FotoService fotoService;

    protected String emailUnico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID() + "@teste.com";
    }

    protected Usuario criarUsuario(Perfil perfil) {
        Usuario usuario = new Usuario("Usuário " + perfil, emailUnico(perfil.name().toLowerCase()),
                passwordEncoder.encode(SENHA_PADRAO), perfil);
        return usuarioRepository.save(usuario);
    }

    protected String fazerLogin(Usuario usuario) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(usuario.getEmail(), SENHA_PADRAO))))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + lerJson(resultado).get("token").asText();
    }

    protected String tokenDeNovoUsuario(Perfil perfil) throws Exception {
        return fazerLogin(criarUsuario(perfil));
    }

    protected MockMultipartHttpServletRequestBuilder formularioOcorrencia(HttpMethod metodo, String url,
                                                                         String titulo, String descricao, String categoria) {
        MockMultipartHttpServletRequestBuilder requisicao = multipart(metodo, url);
        requisicao.param("titulo", titulo)
                .param("descricao", descricao)
                .param("categoria", categoria)
                .param("endereco", "Rua das Flores, 120 - Centro");
        return requisicao;
    }

    protected JsonNode criarOcorrencia(String token, String titulo, String descricao) throws Exception {
        MvcResult resultado = mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", titulo, descricao, "OUTRO")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn();
        return lerJson(resultado);
    }

    protected JsonNode buscarJson(String url, String token) throws Exception {
        MvcResult resultado = mockMvc.perform(get(url).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();
        return lerJson(resultado);
    }

    protected List<String> tiposDoHistorico(Long ocorrenciaId, String token) throws Exception {
        List<String> tipos = new ArrayList<>();
        for (JsonNode item : buscarJson("/ocorrencias/" + ocorrenciaId + "/historico", token)) {
            tipos.add(item.get("tipo").asText());
        }
        return tipos;
    }

    protected String json(Object objeto) throws Exception {
        return objectMapper.writeValueAsString(objeto);
    }

    protected JsonNode lerJson(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }
}
