package br.com.fiap.meubairro.controller;

import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.exception.ArmazenamentoFotoException;
import br.com.fiap.meubairro.exception.RecursoNaoEncontradoException;
import br.com.fiap.meubairro.exception.RequisicaoInvalidaException;
import br.com.fiap.meubairro.service.FotoService.FotoArmazenada;
import br.com.fiap.meubairro.support.IntegracaoTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OcorrenciaControllerTest extends IntegracaoTestBase {

    private static final String URL_FOTO_1 = "https://contateste.blob.core.windows.net/fotos/foto-1.jpg";
    private static final String URL_FOTO_2 = "https://contateste.blob.core.windows.net/fotos/foto-2.jpg";

    @Test
    void criarComMultipartRetorna201NoFormatoDoMobile() throws Exception {
        Usuario cidadao = criarUsuario(Perfil.CIDADAO);

        JsonNode resposta = lerJson(mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias",
                                "Buraco na rua", "Buraco próximo à esquina", "BURACO")
                        .param("latitude", "-23.55")
                        .param("longitude", "-46.63")
                        .header("Authorization", fazerLogin(cidadao)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/ocorrencias/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.titulo").value("Buraco na rua"))
                .andExpect(jsonPath("$.descricao").value("Buraco próximo à esquina"))
                .andExpect(jsonPath("$.categoria").value("BURACO"))
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.endereco").value("Rua das Flores, 120 - Centro"))
                .andExpect(jsonPath("$.latitude").value(-23.55))
                .andExpect(jsonPath("$.longitude").value(-46.63))
                .andExpect(jsonPath("$.fotoUrl").isEmpty())
                .andExpect(jsonPath("$.criadoEm", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.usuarioId").value(cidadao.getId()))
                .andReturn());

        assertThat(nomesDosCampos(resposta)).containsExactly("id", "titulo", "descricao", "categoria", "status",
                "endereco", "latitude", "longitude", "fotoUrl", "criadoEm", "usuarioId");
    }

    @Test
    void criarSemLatitudeELongitudeDevolveNulos() throws Exception {
        mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", "Lixo acumulado", "Lixo acumulado na praça", "LIXO")
                        .header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").isEmpty())
                .andExpect(jsonPath("$.longitude").isEmpty())
                .andExpect(jsonPath("$.fotoUrl").isEmpty());
    }

    @Test
    void criarComFotoGuardaAUrlHttpsDoArmazenamento() throws Exception {
        when(fotoService.salvar(any())).thenReturn(URL_FOTO_1);
        MockMultipartFile foto = new MockMultipartFile("foto", "buraco.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1});
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);

        JsonNode criada = lerJson(mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias",
                                "Buraco com foto", "Buraco grande perto do mercado", "BURACO")
                        .file(foto)
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fotoUrl").value(URL_FOTO_1))
                .andReturn());

        verify(fotoService).salvar(argThat(arquivo -> arquivo != null && "buraco.jpg".equals(arquivo.getOriginalFilename())));
        mockMvc.perform(get("/ocorrencias/" + criada.get("id").asLong()).header("Authorization", token))
                .andExpect(jsonPath("$.fotoUrl").value(URL_FOTO_1));
    }

    @Test
    void fotoRecusadaPeloServicoRetorna400ENaoCriaOcorrencia() throws Exception {
        when(fotoService.salvar(any())).thenThrow(new RequisicaoInvalidaException("Formato de foto inválido. Envie uma imagem JPEG, PNG ou WEBP"));
        MockMultipartFile arquivo = new MockMultipartFile("foto", "documento.pdf", "application/pdf", "%PDF-1.4".getBytes());
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);

        mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", "Foto inválida", "Tentando enviar um PDF como foto", "OUTRO")
                        .file(arquivo)
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Formato de foto inválido. Envie uma imagem JPEG, PNG ou WEBP"));

        mockMvc.perform(get("/ocorrencias").header("Authorization", token))
                .andExpect(jsonPath("$[*].titulo", not(hasItem("Foto inválida"))));
    }

    @Test
    void armazenamentoDeFotosIndisponivelRetorna503() throws Exception {
        when(fotoService.salvar(any())).thenThrow(new ArmazenamentoFotoException("Não foi possível salvar a foto. Tente novamente mais tarde"));
        MockMultipartFile foto = new MockMultipartFile("foto", "poste.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", "Poste com foto", "Poste apagado na esquina", "ILUMINACAO")
                        .file(foto)
                        .header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Não foi possível salvar a foto. Tente novamente mais tarde"));
    }

    @Test
    void fotoGuardadaNoServidorVoltaComUrlCompleta() throws Exception {
        String caminho = "/fotos/5f0c2a8e-1b2c-4d5e-8f90-0123456789ab.jpg";
        when(fotoService.salvar(any())).thenReturn(caminho);
        MockMultipartFile foto = new MockMultipartFile("foto", "foto.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1});
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);

        JsonNode criada = lerJson(mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/api/ocorrencias",
                                "Calçada com foto", "Calçada quebrada perto da escola", "CALCADA")
                        .file(foto)
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fotoUrl").value("http://localhost" + caminho))
                .andReturn());

        mockMvc.perform(get("/ocorrencias/" + criada.get("id").asLong()).header("Authorization", token))
                .andExpect(jsonPath("$.fotoUrl").value("http://localhost" + caminho));
    }

    @Test
    void fotoEhPublicaEVoltaComOTipoDaImagem() throws Exception {
        byte[] imagem = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        when(fotoService.carregar("foto-publica.png")).thenReturn(new FotoArmazenada(imagem, MediaType.IMAGE_PNG));
        when(fotoService.carregar("nao-existe.png")).thenThrow(new RecursoNaoEncontradoException("Foto não encontrada"));

        mockMvc.perform(get("/fotos/foto-publica.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(imagem));
        mockMvc.perform(get("/api/fotos/foto-publica.png"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/fotos/nao-existe.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Foto não encontrada"));
    }

    @Test
    void criarComDadosInvalidosRetorna400ComOsCampos() throws Exception {
        mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", "Oi", "curta", "")
                        .header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Existem campos inválidos na requisição"))
                .andExpect(jsonPath("$.fields[*].field", hasItems("titulo", "descricao", "categoria")));
    }

    @Test
    void criarComCategoriaForaDoContratoRetorna400() throws Exception {
        mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", "Semáforo quebrado", "Semáforo apagado no cruzamento", "SEMAFORO")
                        .header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Categoria inválida: SEMAFORO. Use uma destas: BURACO, ILUMINACAO, LIXO, ALAGAMENTO, CALCADA, OUTRO"));
    }

    @Test
    void criarComLatitudeInvalidaRetorna400() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);

        mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", "Lixo na rua", "Lixo espalhado na rua", "LIXO")
                        .param("latitude", "120")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].message").value("A latitude deve estar entre -90 e 90"));

        mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", "Lixo na rua", "Lixo espalhado na rua", "LIXO")
                        .param("longitude", "abc")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("longitude"))
                .andExpect(jsonPath("$.fields[0].message").value("Valor inválido para o campo 'longitude'"));
    }

    @Test
    void criarComJsonEmVezDeMultipartRetorna415() throws Exception {
        mockMvc.perform(post("/ocorrencias")
                        .header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\": \"Buraco na rua\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message", containsString("multipart/form-data")));
    }

    @Test
    void listarDevolveArrayComOcorrenciasDeTodosOsUsuariosDaMaisRecenteParaAMaisAntiga() throws Exception {
        long idMaria = criarOcorrencia(tokenDeNovoUsuario(Perfil.CIDADAO), "Ocorrência da Maria", "Calçada quebrada em frente à casa").get("id").asLong();
        String tokenJoao = tokenDeNovoUsuario(Perfil.CIDADAO);
        long idJoao = criarOcorrencia(tokenJoao, "Ocorrência do João", "Entulho jogado no terreno baldio").get("id").asLong();

        JsonNode lista = lerJson(mockMvc.perform(get("/ocorrencias").header("Authorization", tokenJoao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].id", hasItems((int) idMaria, (int) idJoao)))
                .andReturn());

        List<Long> ids = new ArrayList<>();
        lista.forEach(item -> ids.add(item.get("id").asLong()));
        assertThat(ids.indexOf(idJoao)).isLessThan(ids.indexOf(idMaria));
    }

    @Test
    void qualquerUsuarioLogadoVeODetalheDeUmaOcorrencia() throws Exception {
        long id = criarOcorrencia(tokenDeNovoUsuario(Perfil.CIDADAO), "Ocorrência do João", "Calçada quebrada na rua").get("id").asLong();

        mockMvc.perform(get("/ocorrencias/" + id).header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.titulo").value("Ocorrência do João"));
    }

    @Test
    void buscarOcorrenciaInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/ocorrencias/999999").header("Authorization", tokenDeNovoUsuario(Perfil.CIDADAO)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Ocorrência 999999 não encontrada"));
    }

    @Test
    void donoAtualizaComMultipartMantendoLocalizacaoEFotoQuandoNaoEnviadas() throws Exception {
        when(fotoService.salvar(any())).thenReturn(URL_FOTO_1, (String) null);
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = lerJson(mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", "Calçada quebrada", "Calçada com buracos na frente do número 10", "CALCADA")
                        .file(new MockMultipartFile("foto", "calcada.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                        .param("latitude", "-23.5")
                        .param("longitude", "-46.6")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        mockMvc.perform(formularioOcorrencia(HttpMethod.PUT, "/ocorrencias/" + id, "Calçada quebrada e sem rampa",
                                "Calçada com buracos e sem rampa de acessibilidade", "CALCADA")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Calçada quebrada e sem rampa"))
                .andExpect(jsonPath("$.latitude").value(-23.5))
                .andExpect(jsonPath("$.longitude").value(-46.6))
                .andExpect(jsonPath("$.fotoUrl").value(URL_FOTO_1));

        verify(fotoService, never()).excluir(any());
        assertThat(tiposDoHistorico(id, token)).contains("CRIACAO", "ATUALIZACAO");
    }

    @Test
    void atualizarComNovaFotoTrocaAUrlEExcluiAFotoAnterior() throws Exception {
        when(fotoService.salvar(any())).thenReturn(URL_FOTO_1, URL_FOTO_2);
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = lerJson(mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", "Poste apagado", "Poste apagado na esquina da escola", "ILUMINACAO")
                        .file(new MockMultipartFile("foto", "poste.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        mockMvc.perform(formularioOcorrencia(HttpMethod.PUT, "/ocorrencias/" + id, "Poste apagado", "Poste apagado na esquina da escola", "ILUMINACAO")
                        .file(new MockMultipartFile("foto", "poste-novo.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(URL_FOTO_2));

        verify(fotoService).excluir(URL_FOTO_1);
    }

    @Test
    void outroCidadaoNaoAtualizaNemExcluiRecebendo403() throws Exception {
        long idDoJoao = criarOcorrencia(tokenDeNovoUsuario(Perfil.CIDADAO), "Ocorrência do João", "Lâmpada queimada na rua").get("id").asLong();
        String tokenMaria = tokenDeNovoUsuario(Perfil.CIDADAO);

        mockMvc.perform(formularioOcorrencia(HttpMethod.PUT, "/ocorrencias/" + idDoJoao, "Alterado por outro", "Tentativa de alterar o que não é meu", "OUTRO")
                        .header("Authorization", tokenMaria))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Somente o cidadão que registrou a ocorrência pode alterá-la"));

        mockMvc.perform(delete("/ocorrencias/" + idDoJoao).header("Authorization", tokenMaria))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Somente o cidadão que registrou a ocorrência pode excluí-la"));
    }

    @Test
    void naoAtualizaOcorrenciaQueJaEstaEmAnalise() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(token, "Entulho na calçada", "Entulho de obra ocupando a calçada").get("id").asLong();
        alterarStatus(id, "EM_ANALISE", tokenDeNovoUsuario(Perfil.GESTOR));

        mockMvc.perform(formularioOcorrencia(HttpMethod.PUT, "/ocorrencias/" + id, "Entulho na calçada", "Nova descrição do entulho", "LIXO")
                        .header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Só é possível alterar ocorrências com status ABERTA"));
    }

    @Test
    void donoExcluiRecebendo204SemCorpoEAFotoTambemEhExcluida() throws Exception {
        when(fotoService.salvar(any())).thenReturn(URL_FOTO_1);
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = lerJson(mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", "Registro duplicado", "Registrei esta ocorrência duas vezes", "OUTRO")
                        .file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        mockMvc.perform(delete("/ocorrencias/" + id).header("Authorization", token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(emptyString()));

        mockMvc.perform(get("/ocorrencias/" + id).header("Authorization", token))
                .andExpect(status().isNotFound());
        verify(fotoService).excluir(URL_FOTO_1);
    }

    @Test
    void cidadaoNaoExcluiOcorrenciaEmAnaliseMasGestorPode() throws Exception {
        String tokenCidadao = tokenDeNovoUsuario(Perfil.CIDADAO);
        String tokenGestor = tokenDeNovoUsuario(Perfil.GESTOR);
        long id = criarOcorrencia(tokenCidadao, "Lixo no canteiro", "Lixo acumulado no canteiro central").get("id").asLong();
        alterarStatus(id, "EM_ANALISE", tokenGestor);

        mockMvc.perform(delete("/ocorrencias/" + id).header("Authorization", tokenCidadao))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/ocorrencias/" + id).header("Authorization", tokenGestor))
                .andExpect(status().isNoContent());
    }

    @Test
    void gestorAlteraStatusComObservacaoQueApareceNoHistorico() throws Exception {
        String tokenCidadao = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(tokenCidadao, "Lixo acumulado", "Lixo acumulado na esquina").get("id").asLong();

        mockMvc.perform(patch("/ocorrencias/" + id + "/status")
                        .header("Authorization", tokenDeNovoUsuario(Perfil.GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"EM_ANALISE\", \"observacao\": \"Equipe de limpeza enviada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANALISE"));

        mockMvc.perform(get("/ocorrencias/" + id + "/historico").header("Authorization", tokenCidadao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("CRIACAO"))
                .andExpect(jsonPath("$[?(@.tipo == 'STATUS')].descricao", hasItems(containsString("Equipe de limpeza enviada"))));
    }

    @Test
    void naoAlteraParaOMesmoStatusNemDepoisDeResolvida() throws Exception {
        String tokenGestor = tokenDeNovoUsuario(Perfil.GESTOR);
        long id = criarOcorrencia(tokenDeNovoUsuario(Perfil.CIDADAO), "Poça de lama", "Poça de lama enorme na calçada").get("id").asLong();

        mockMvc.perform(patch("/ocorrencias/" + id + "/status")
                        .header("Authorization", tokenGestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ABERTA\"}"))
                .andExpect(status().isConflict());

        alterarStatus(id, "RESOLVIDA", tokenGestor);

        mockMvc.perform(patch("/ocorrencias/" + id + "/status")
                        .header("Authorization", tokenGestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"EM_ANALISE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A ocorrência já foi resolvida e não pode mudar de status"));
    }

    @Test
    void gestorDefinePrioridadeManualmente() throws Exception {
        String tokenCidadao = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(tokenCidadao, "Fio solto", "Fio solto pendurado sobre a calçada").get("id").asLong();

        mockMvc.perform(patch("/ocorrencias/" + id + "/prioridade")
                        .header("Authorization", tokenDeNovoUsuario(Perfil.GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prioridade\": \"BAIXA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocorrenciaId").value(id))
                .andExpect(jsonPath("$.prioridade").value("BAIXA"))
                .andExpect(jsonPath("$.prioridadeDefinidaPeloGestor").value(true));

        assertThat(tiposDoHistorico(id, tokenCidadao)).contains("PRIORIDADE");
    }

    @Test
    void triagemMostraPrioridadeEStatusDaAnalise() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long id = criarOcorrencia(token, "Lixo acumulado", "Lixo acumulado na esquina").get("id").asLong();

        mockMvc.perform(get("/ocorrencias/" + id + "/triagem").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocorrenciaId").value(id))
                .andExpect(jsonPath("$.prioridade").isNotEmpty())
                .andExpect(jsonPath("$.statusAnaliseIa").isNotEmpty())
                .andExpect(jsonPath("$.prioridadeDefinidaPeloGestor").value(false));
    }

    @Test
    void paginadoDevolvePaginasComTotais() throws Exception {
        String token = tokenDeNovoUsuario(Perfil.CIDADAO);
        long primeiro = criarComCategoria(token, "Rua alagada 1", "Rua alagada depois da chuva forte", "ALAGAMENTO");
        criarComCategoria(token, "Rua alagada 2", "Rua alagada depois da chuva forte", "ALAGAMENTO");
        long terceiro = criarComCategoria(token, "Rua alagada 3", "Rua alagada depois da chuva forte", "ALAGAMENTO");

        mockMvc.perform(get("/ocorrencias/paginado?categoria=ALAGAMENTO&page=0&size=2&sort=id,asc").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(2))
                .andExpect(jsonPath("$.conteudo[0].id").value(primeiro))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(2))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.ultima").value(false));

        mockMvc.perform(get("/ocorrencias/paginado?categoria=ALAGAMENTO&page=1&size=2&sort=id,asc").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(terceiro))
                .andExpect(jsonPath("$.ultima").value(true));
    }

    @Test
    void paginadoFiltraPorStatus() throws Exception {
        String tokenCidadao = tokenDeNovoUsuario(Perfil.CIDADAO);
        long emAnalise = criarOcorrencia(tokenCidadao, "Mato alto na praça", "O mato da praça está muito alto").get("id").asLong();
        alterarStatus(emAnalise, "EM_ANALISE", tokenDeNovoUsuario(Perfil.GESTOR));

        mockMvc.perform(get("/ocorrencias/paginado?status=EM_ANALISE&size=50&sort=id,desc").header("Authorization", tokenCidadao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].id", hasItem((int) emAnalise)))
                .andExpect(jsonPath("$.conteudo[*].status", everyItem(is("EM_ANALISE"))));
    }

    @Test
    void gestorConsultaResumo() throws Exception {
        criarOcorrencia(tokenDeNovoUsuario(Perfil.CIDADAO), "Lixo na rua", "Lixo espalhado pela rua toda");

        mockMvc.perform(get("/ocorrencias/resumo").header("Authorization", tokenDeNovoUsuario(Perfil.GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.abertas").isNumber())
                .andExpect(jsonPath("$.emAnalise").isNumber())
                .andExpect(jsonPath("$.prioridadeAlta").isNumber());
    }

    private long criarComCategoria(String token, String titulo, String descricao, String categoria) throws Exception {
        return lerJson(mockMvc.perform(formularioOcorrencia(HttpMethod.POST, "/ocorrencias", titulo, descricao, categoria)
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
    }

    private void alterarStatus(long id, String novoStatus, String tokenGestor) throws Exception {
        mockMvc.perform(patch("/ocorrencias/" + id + "/status")
                        .header("Authorization", tokenGestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"" + novoStatus + "\"}"))
                .andExpect(status().isOk());
    }

    private List<String> nomesDosCampos(JsonNode objeto) {
        List<String> nomes = new ArrayList<>();
        objeto.fieldNames().forEachRemaining(nomes::add);
        return nomes;
    }
}
