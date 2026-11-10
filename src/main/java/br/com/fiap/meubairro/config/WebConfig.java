package br.com.fiap.meubairro.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> conversores) {
        for (HttpMessageConverter<?> conversor : conversores) {
            if (conversor instanceof MappingJackson2HttpMessageConverter conversorJson) {
                conversorJson.setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
    }
}
