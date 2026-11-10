package br.com.fiap.meubairro.messaging;

import br.com.fiap.meubairro.config.RabbitConfig;
import br.com.fiap.meubairro.entity.HistoricoOcorrencia;
import br.com.fiap.meubairro.entity.Ocorrencia;
import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.entity.Prioridade;
import br.com.fiap.meubairro.entity.StatusAnaliseIa;
import br.com.fiap.meubairro.entity.TipoHistorico;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.repository.HistoricoOcorrenciaRepository;
import br.com.fiap.meubairro.repository.OcorrenciaRepository;
import br.com.fiap.meubairro.support.IntegracaoTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MensageriaIntegracaoTest extends IntegracaoTestBase {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;

    @Autowired
    private HistoricoOcorrenciaRepository historicoRepository;

    @Test
    void filaDeTriagemEhDeclaradaNoBroker() {
        assertThat(amqpAdmin.getQueueProperties(RabbitConfig.FILA_OCORRENCIAS_CRIADAS)).isNotNull();
    }

    @Test
    void cadastroPublicaEventoQueEhConsumidoEDisparaATriagemDaIa() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(token, "Buraco enorme", "Buraco enorme no meio da avenida, carros desviando").get("id").asLong();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            JsonNode triagem = buscarJson("/ocorrencias/" + id + "/triagem", token);
            assertThat(triagem.get("statusAnaliseIa").asText()).isEqualTo("CONCLUIDA");
            assertThat(triagem.get("categoriaSugerida").asText()).isEqualTo("BURACO");
            assertThat(triagem.get("prioridade").asText()).isEqualTo("ALTA");
        });

        List<String> descricoes = new ArrayList<>();
        buscarJson("/ocorrencias/" + id + "/historico", token).forEach(item -> descricoes.add(item.get("descricao").asText()));
        assertThat(tiposDoHistorico(id, token)).containsSubsequence("CRIACAO", "FILA", "ANALISE_IA");
        assertThat(descricoes).anyMatch(descricao -> descricao.contains("Evento recebido pelo consumidor"));
    }

    @Test
    void consumidorProcessaEventoPublicadoDiretamenteNaFila() {
        Usuario cidadao = criarUsuario(Perfil.CIDADAO);
        Ocorrencia ocorrencia = ocorrenciaRepository.save(new Ocorrencia("Poste apagado", "Poste apagado em frente à escola",
                "Rua da Escola, 50", null, null, null, categoriaRepository.findByCodigoIgnoreCase("OUTRO").orElseThrow(), cidadao));

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_OCORRENCIAS, RabbitConfig.ROTA_OCORRENCIA_CRIADA,
                new OcorrenciaCriadaEvento(ocorrencia.getId(), cidadao.getId(), ocorrencia.getTitulo(), ocorrencia.getDataCriacao()));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Ocorrencia atualizada = ocorrenciaRepository.findById(ocorrencia.getId()).orElseThrow();
            assertThat(atualizada.getStatusAnaliseIa()).isEqualTo(StatusAnaliseIa.CONCLUIDA);
            assertThat(atualizada.getPrioridade()).isEqualTo(Prioridade.ALTA);
        });
        assertThat(historicoRepository.findByOcorrenciaIdOrderByIdAsc(ocorrencia.getId()))
                .extracting(HistoricoOcorrencia::getTipo)
                .containsSubsequence(TipoHistorico.FILA, TipoHistorico.ANALISE_IA);
    }

    @Test
    void eventoDeOcorrenciaInexistenteEhDescartadoSemDerrubarOConsumidor() throws Exception {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_OCORRENCIAS, RabbitConfig.ROTA_OCORRENCIA_CRIADA,
                new OcorrenciaCriadaEvento(999999L, 1L, "Ocorrência que não existe", LocalDateTime.now()));

        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(token, "Lixo no córrego", "Lixo jogado na margem do córrego").get("id").asLong();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(buscarJson("/ocorrencias/" + id + "/triagem", token).get("statusAnaliseIa").asText())
                        .isEqualTo("CONCLUIDA"));
    }

    @Test
    void eventoTrafegaComoJson() {
        OcorrenciaCriadaEvento evento = new OcorrenciaCriadaEvento(10L, 20L, "Poste apagado", LocalDateTime.of(2026, 11, 9, 10, 30));

        Message mensagem = rabbitTemplate.getMessageConverter().toMessage(evento, new MessageProperties());
        String corpo = new String(mensagem.getBody(), StandardCharsets.UTF_8);

        assertThat(mensagem.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(corpo).contains("\"ocorrenciaId\":10", "\"usuarioId\":20", "\"titulo\":\"Poste apagado\"", "2026-11-09T10:30:00");
    }
}
