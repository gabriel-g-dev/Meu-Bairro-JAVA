package br.com.fiap.meubairro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_OCORRENCIAS = "meubairro.ocorrencias";
    public static final String FILA_OCORRENCIAS_CRIADAS = "meubairro.ocorrencias.criadas";
    public static final String ROTA_OCORRENCIA_CRIADA = "ocorrencia.criada";

    @Bean
    public DirectExchange ocorrenciasExchange() {
        return new DirectExchange(EXCHANGE_OCORRENCIAS);
    }

    @Bean
    public Queue filaOcorrenciasCriadas() {
        return QueueBuilder.durable(FILA_OCORRENCIAS_CRIADAS).build();
    }

    @Bean
    public Binding ligacaoOcorrenciaCriada(Queue filaOcorrenciasCriadas, DirectExchange ocorrenciasExchange) {
        return BindingBuilder.bind(filaOcorrenciasCriadas).to(ocorrenciasExchange).with(ROTA_OCORRENCIA_CRIADA);
    }

    @Bean
    public MessageConverter conversorJson(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
