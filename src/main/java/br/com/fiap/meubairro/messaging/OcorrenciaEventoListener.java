package br.com.fiap.meubairro.messaging;

import br.com.fiap.meubairro.config.RabbitConfig;
import br.com.fiap.meubairro.dto.TriagemResponse;
import br.com.fiap.meubairro.entity.Ocorrencia;
import br.com.fiap.meubairro.entity.TipoHistorico;
import br.com.fiap.meubairro.exception.AnaliseIaException;
import br.com.fiap.meubairro.exception.RecursoNaoEncontradoException;
import br.com.fiap.meubairro.service.HistoricoService;
import br.com.fiap.meubairro.service.OcorrenciaService;
import br.com.fiap.meubairro.service.TriagemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OcorrenciaEventoListener {

    private static final Logger log = LoggerFactory.getLogger(OcorrenciaEventoListener.class);

    private final OcorrenciaService ocorrenciaService;
    private final HistoricoService historicoService;
    private final TriagemService triagemService;

    public OcorrenciaEventoListener(OcorrenciaService ocorrenciaService,
                                    HistoricoService historicoService,
                                    TriagemService triagemService) {
        this.ocorrenciaService = ocorrenciaService;
        this.historicoService = historicoService;
        this.triagemService = triagemService;
    }

    @RabbitListener(queues = RabbitConfig.FILA_OCORRENCIAS_CRIADAS)
    public void processarOcorrenciaCriada(OcorrenciaCriadaEvento evento) {
        log.info("Evento recebido da fila {}: ocorrência {}", RabbitConfig.FILA_OCORRENCIAS_CRIADAS, evento.ocorrenciaId());
        try {
            Ocorrencia ocorrencia = ocorrenciaService.buscarEntidade(evento.ocorrenciaId());
            historicoService.registrar(ocorrencia, TipoHistorico.FILA,
                    "Evento recebido pelo consumidor da fila de triagem (RabbitMQ)");
            TriagemResponse resultado = triagemService.executar(evento.ocorrenciaId());
            log.info("Triagem automática concluída: ocorrência {} com prioridade {}", resultado.ocorrenciaId(), resultado.prioridade());
        } catch (RecursoNaoEncontradoException e) {
            log.warn("Ocorrência {} não existe mais. Evento descartado", evento.ocorrenciaId());
        } catch (AnaliseIaException e) {
            log.warn("Triagem automática da ocorrência {} não concluída: {}", evento.ocorrenciaId(), e.getMessage());
        }
    }
}
