package br.com.fiap.meubairro.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Locale;

public class ChatModelFalso implements ChatModel {

    public static final String SIMULAR_IA_FORA_DO_AR = "[simular-ia-fora-do-ar]";
    public static final String SIMULAR_RESPOSTA_INVALIDA = "[simular-resposta-invalida]";
    public static final String SIMULAR_JSON_QUEBRADO = "[simular-json-quebrado]";

    @Override
    public ChatResponse call(Prompt prompt) {
        String relato = prompt.getUserMessage().getText().toLowerCase(Locale.ROOT);

        if (relato.contains(SIMULAR_IA_FORA_DO_AR)) {
            throw new IllegalStateException("Connection refused (simulação de teste)");
        }
        if (relato.contains(SIMULAR_RESPOSTA_INVALIDA)) {
            return responder("{\"categoria\": \"SEMAFORO\", \"prioridade\": \"URGENTE\", \"justificativa\": \"Teste\"}");
        }
        if (relato.contains(SIMULAR_JSON_QUEBRADO)) {
            return responder("isto não é um JSON");
        }
        if (relato.contains("poste")) {
            return responder("{\"categoria\": \"ILUMINACAO\", \"prioridade\": \"ALTA\", "
                    + "\"justificativa\": \"Poste apagado deixa a rua escura e aumenta o risco para pedestres.\"}");
        }
        if (relato.contains("buraco")) {
            return responder("{\"categoria\": \"buraco\", \"prioridade\": \"alta\", "
                    + "\"justificativa\": \"Buraco em via movimentada pode causar acidentes.\"}");
        }
        return responder("{\"categoria\": \"OUTRO\", \"prioridade\": \"MEDIA\", "
                + "\"justificativa\": \"Relato sem indicação de risco imediato.\"}");
    }

    private ChatResponse responder(String texto) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(texto))));
    }
}
