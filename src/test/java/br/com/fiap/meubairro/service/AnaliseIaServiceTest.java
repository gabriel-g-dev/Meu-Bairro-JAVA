package br.com.fiap.meubairro.service;

import br.com.fiap.meubairro.dto.ResultadoAnaliseIa;
import br.com.fiap.meubairro.entity.Categoria;
import br.com.fiap.meubairro.entity.Prioridade;
import br.com.fiap.meubairro.exception.AnaliseIaException;
import br.com.fiap.meubairro.repository.CategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AnaliseIaServiceTest {

    private static final String CHAVE_FALSA = "chave-falsa-do-teste";

    @Mock
    private CategoriaRepository categoriaRepository;

    @BeforeEach
    void configurarCategorias() {
        lenient().when(categoriaRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                categoria(1L, "BURACO", "Buraco na via", "Buracos no asfalto"),
                categoria(2L, "ILUMINACAO", "Iluminação pública", "Postes apagados"),
                categoria(3L, "ALAGAMENTO", "Alagamento", "Ruas alagadas"),
                categoria(4L, "OUTRO", "Outros", null)));
    }

    @Test
    void converteRespostaValidaDaIa() {
        AnaliseIaService servico = servicoRespondendo("""
                {"categoria": "ILUMINACAO", "prioridade": "ALTA", "justificativa": "Rua escura perto da escola."}
                """);

        ResultadoAnaliseIa resultado = servico.analisar("Poste apagado", "O poste está apagado há dias");

        assertThat(resultado.categoria().getCodigo()).isEqualTo("ILUMINACAO");
        assertThat(resultado.prioridade()).isEqualTo(Prioridade.ALTA);
        assertThat(resultado.justificativa()).isEqualTo("Rua escura perto da escola.");
    }

    @Test
    void aceitaCodigoEmMinusculasNomeComAcentoEPrioridadeComAcento() {
        AnaliseIaService porCodigo = servicoRespondendo("""
                {"categoria": " alagamento ", "prioridade": "média", "justificativa": "Rua alaga com chuva forte."}
                """);
        AnaliseIaService porNome = servicoRespondendo("""
                {"categoria": "iluminacao publica", "prioridade": "baixa", "justificativa": "Lâmpada acesa de dia."}
                """);

        assertThat(porCodigo.analisar("Rua alagada", "A rua alaga quando chove").categoria().getCodigo()).isEqualTo("ALAGAMENTO");
        assertThat(porCodigo.analisar("Rua alagada", "A rua alaga quando chove").prioridade()).isEqualTo(Prioridade.MEDIA);
        assertThat(porNome.analisar("Lâmpada acesa", "Lâmpada acesa o dia todo").categoria().getCodigo()).isEqualTo("ILUMINACAO");
    }

    @Test
    void recusaCategoriaQueNaoExiste() {
        AnaliseIaService servico = servicoRespondendo("""
                {"categoria": "SEMAFORO", "prioridade": "ALTA", "justificativa": "Semáforo quebrado."}
                """);

        assertThatThrownBy(() -> servico.analisar("Semáforo", "Semáforo quebrado no cruzamento"))
                .isInstanceOf(AnaliseIaException.class)
                .hasMessageContaining("fora do padrão");
    }

    @Test
    void recusaPrioridadeInvalida() {
        AnaliseIaService servico = servicoRespondendo("""
                {"categoria": "OUTRO", "prioridade": "URGENTE", "justificativa": "Teste."}
                """);

        assertThatThrownBy(() -> servico.analisar("Título", "Descrição qualquer"))
                .isInstanceOf(AnaliseIaException.class);
    }

    @Test
    void recusaJustificativaVazia() {
        AnaliseIaService servico = servicoRespondendo("""
                {"categoria": "OUTRO", "prioridade": "BAIXA", "justificativa": "   "}
                """);

        assertThatThrownBy(() -> servico.analisar("Título", "Descrição qualquer"))
                .isInstanceOf(AnaliseIaException.class);
    }

    @Test
    void limitaJustificativaMuitoLonga() {
        AnaliseIaService servico = servicoRespondendo("""
                {"categoria": "OUTRO", "prioridade": "BAIXA", "justificativa": "%s"}
                """.formatted("a".repeat(800)));

        ResultadoAnaliseIa resultado = servico.analisar("Título", "Descrição qualquer");

        assertThat(resultado.justificativa()).hasSize(500).endsWith("...");
    }

    @Test
    void trataRespostaQueNaoEhJson() {
        AnaliseIaService servico = servicoRespondendo("Desculpe, não consegui entender.");

        assertThatThrownBy(() -> servico.analisar("Título", "Descrição qualquer"))
                .isInstanceOf(AnaliseIaException.class)
                .hasMessageContaining("indisponível");
    }

    @Test
    void trataFalhaDeConexaoComOModelo() {
        ChatModel modeloForaDoAr = prompt -> {
            throw new IllegalStateException("Connection refused");
        };
        AnaliseIaService servico = new AnaliseIaService(ChatClient.builder(modeloForaDoAr), categoriaRepository, true, CHAVE_FALSA);

        assertThatThrownBy(() -> servico.analisar("Título", "Descrição qualquer"))
                .isInstanceOf(AnaliseIaException.class)
                .hasMessageContaining("indisponível");
    }

    @Test
    void naoChamaOModeloQuandoIaEstaDesabilitadaOuSemChave() {
        AtomicInteger chamadas = new AtomicInteger();
        ChatModel modelo = prompt -> {
            chamadas.incrementAndGet();
            return resposta("{}");
        };
        AnaliseIaService desabilitada = new AnaliseIaService(ChatClient.builder(modelo), categoriaRepository, false, CHAVE_FALSA);
        AnaliseIaService semChave = new AnaliseIaService(ChatClient.builder(modelo), categoriaRepository, true, " ");

        assertThatThrownBy(() -> desabilitada.analisar("Título", "Descrição qualquer"))
                .isInstanceOf(AnaliseIaException.class)
                .hasMessageContaining("desabilitada");
        assertThatThrownBy(() -> semChave.analisar("Título", "Descrição qualquer"))
                .isInstanceOf(AnaliseIaException.class)
                .hasMessageContaining("GEMINI_API_KEY");
        assertThat(chamadas.get()).isZero();
    }

    @Test
    void enviaSoTituloEDescricaoSemDadosPessoaisEComAsCategoriasDoBanco() {
        AtomicReference<Prompt> promptRecebido = new AtomicReference<>();
        ChatModel modelo = prompt -> {
            promptRecebido.set(prompt);
            return resposta("{\"categoria\": \"OUTRO\", \"prioridade\": \"BAIXA\", \"justificativa\": \"Ok.\"}");
        };
        AnaliseIaService servico = new AnaliseIaService(ChatClient.builder(modelo), categoriaRepository, true, CHAVE_FALSA);

        servico.analisar("Banco quebrado", "O banco da praça está {quebrado}. Falar com joao.silva@email.com "
                + "ou (11) 91234-5678, CPF 123.456.789-09");

        String instrucoes = promptRecebido.get().getSystemMessage().getText();
        String relato = promptRecebido.get().getUserMessage().getText();
        assertThat(instrucoes).contains("BURACO", "ILUMINACAO", "ALAGAMENTO", "OUTRO", "ALTA", "MEDIA", "BAIXA");
        assertThat(relato).contains("Título: Banco quebrado", "{quebrado}", "[e-mail removido]",
                "[telefone removido]", "[documento removido]");
        assertThat(relato).doesNotContain("joao.silva@email.com", "91234-5678", "123.456.789-09", "Endereço");
    }

    private AnaliseIaService servicoRespondendo(String json) {
        ChatModel modelo = prompt -> resposta(json);
        return new AnaliseIaService(ChatClient.builder(modelo), categoriaRepository, true, CHAVE_FALSA);
    }

    private ChatResponse resposta(String texto) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(texto))));
    }

    private Categoria categoria(Long id, String codigo, String nome, String descricao) {
        Categoria categoria = new Categoria(codigo, nome, descricao);
        ReflectionTestUtils.setField(categoria, "id", id);
        return categoria;
    }
}
