package br.com.fiap.meubairro.config;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.model.chat=google-genai",
        "meubairro.teste.chave-gemini=chave-definida-pelo-teste",
        "spring.ai.google.genai.api-key=${meubairro.teste.chave-gemini}",
        "spring.datasource.url=jdbc:h2:mem:meubairro_ia_config;DB_CLOSE_DELAY=-1",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class IaConfigTest {

    @Autowired
    private Client clienteGemini;

    @Test
    void clienteDoGeminiRecebeAChaveDaConfiguracaoJaResolvida() {
        assertThat(clienteGemini.apiKey()).isEqualTo("chave-definida-pelo-teste");
    }
}
