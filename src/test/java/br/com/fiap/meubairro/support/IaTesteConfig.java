package br.com.fiap.meubairro.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class IaTesteConfig {

    @Bean
    public ChatModelFalso chatModelFalso() {
        return new ChatModelFalso();
    }
}
