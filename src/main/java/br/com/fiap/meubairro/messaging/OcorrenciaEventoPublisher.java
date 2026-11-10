package br.com.fiap.meubairro.messaging;

import br.com.fiap.meubairro.config.RabbitConfig;
import br.com.fiap.meubairro.entity.Ocorrencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OcorrenciaEventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(OcorrenciaEventoPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public OcorrenciaEventoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public boolean publicarOcorrenciaCriada(Ocorrencia ocorrencia) {
        OcorrenciaCriadaEvento evento = new OcorrenciaCriadaEvento(
                ocorrencia.getId(),
                ocorrencia.getUsuario().getId(),
                ocorrencia.getTitulo(),
                ocorrencia.getDataCriacao());
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_OCORRENCIAS, RabbitConfig.ROTA_OCORRENCIA_CRIADA, evento);
            log.info("Evento OcorrenciaCriada publicado: ocorrência {}", ocorrencia.getId());
            return true;
        } catch (AmqpException e) {
            log.warn("Não foi possível publicar o evento da ocorrência {}: {}", ocorrencia.getId(), e.getMessage());
            return false;
        }
    }
}
