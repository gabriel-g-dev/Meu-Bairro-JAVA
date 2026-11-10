package br.com.fiap.meubairro.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class IaConfig {

    private static final String CHAVE_NAO_CONFIGURADA = "chave-nao-configurada";

    @Bean
    public Client clienteGemini(Environment ambiente) {
        String chaveApi = ambiente.getProperty("spring.ai.google.genai.api-key", "").trim();
        HttpOptions opcoesHttp = HttpOptions.builder()
                .timeout(30_000)
                .retryOptions(HttpRetryOptions.builder().attempts(2).build())
                .build();
        return Client.builder()
                .apiKey(chaveApi.isBlank() ? CHAVE_NAO_CONFIGURADA : chaveApi)
                .httpOptions(opcoesHttp)
                .build();
    }
}
