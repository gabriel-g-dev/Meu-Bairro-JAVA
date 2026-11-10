package br.com.fiap.meubairro.service;

import br.com.fiap.meubairro.exception.ArmazenamentoFotoException;
import br.com.fiap.meubairro.exception.RecursoNaoEncontradoException;
import br.com.fiap.meubairro.exception.RequisicaoInvalidaException;
import br.com.fiap.meubairro.service.FotoService.FotoArmazenada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class FotoServiceTest {

    private static final String CONTAINER = "https://contateste.blob.core.windows.net/fotos";
    private static final String SAS = "sv=2024-11-04&sp=cwd&sig=assinatura-de-teste";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1};

    @TempDir
    Path pasta;

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer servidorAzure;

    @BeforeEach
    void prepararServidorFalso() {
        restClientBuilder = RestClient.builder();
        servidorAzure = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    private FotoService servicoComAzure() {
        return new FotoService(restClientBuilder, CONTAINER, SAS, pasta.toString());
    }

    private FotoService servicoLocal() {
        return new FotoService(restClientBuilder, "", "", pasta.toString());
    }

    @Test
    void semFotoNaoGuardaNada() {
        FotoService fotoService = servicoComAzure();

        assertThat(fotoService.salvar(null)).isNull();
        assertThat(fotoService.salvar(new MockMultipartFile("foto", new byte[0]))).isNull();
        servidorAzure.verify();
    }

    @Test
    void semAzureGuardaAFotoNaPastaDoServidor() throws Exception {
        String caminho = servicoLocal().salvar(new MockMultipartFile("foto", "../minha foto.jpg", "image/jpeg", JPEG));

        assertThat(caminho).matches("/fotos/[0-9a-f-]{36}\\.jpg");
        Path arquivo = pasta.resolve(caminho.substring("/fotos/".length()));
        assertThat(Files.readAllBytes(arquivo)).isEqualTo(JPEG);
        servidorAzure.verify();
    }

    @Test
    void carregaAFotoGuardadaComOTipoDaImagem() {
        FotoService fotoService = servicoLocal();
        String caminho = fotoService.salvar(new MockMultipartFile("foto", "poste.png", "image/png", PNG));

        FotoArmazenada foto = fotoService.carregar(caminho.substring("/fotos/".length()));

        assertThat(foto.conteudo()).isEqualTo(PNG);
        assertThat(foto.tipo()).isEqualTo(MediaType.IMAGE_PNG);
    }

    @Test
    void naoCarregaArquivoForaDoPadraoNemFotoInexistente() throws Exception {
        Files.writeString(pasta.resolve("segredo.txt"), "não pode ser lido");
        FotoService fotoService = servicoLocal();

        assertThatThrownBy(() -> fotoService.carregar("segredo.txt")).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> fotoService.carregar("../application.properties")).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> fotoService.carregar("5f0c2a8e-1b2c-4d5e-8f90-0123456789ab.jpg"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Foto não encontrada");
    }

    @Test
    void excluiAFotoDaPastaDoServidor() {
        FotoService fotoService = servicoLocal();
        String caminho = fotoService.salvar(new MockMultipartFile("foto", "f.jpg", "image/jpeg", JPEG));

        fotoService.excluir(caminho);

        assertThat(pasta.resolve(caminho.substring("/fotos/".length()))).doesNotExist();
        fotoService.excluir("/fotos/../segredo.txt");
    }

    @Test
    void enviaJpegComSasEDevolveUrlHttpsSemOToken() {
        servidorAzure.expect(requestTo(startsWith(CONTAINER + "/")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(queryParam("sig", "assinatura-de-teste"))
                .andExpect(header("x-ms-blob-type", "BlockBlob"))
                .andExpect(header("x-ms-version", "2023-11-03"))
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG))
                .andRespond(withStatus(HttpStatus.CREATED));

        String url = servicoComAzure().salvar(new MockMultipartFile("foto", "../minha foto.jpg", "image/jpeg", JPEG));

        servidorAzure.verify();
        assertThat(url).matches("https://contateste\\.blob\\.core\\.windows\\.net/fotos/[0-9a-f-]{36}\\.jpg");
        assertThat(pasta).isEmptyDirectory();
    }

    @Test
    void identificaPngEWebpPeloConteudo() {
        servidorAzure.expect(requestTo(containsString(".png?")))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andRespond(withStatus(HttpStatus.CREATED));
        servidorAzure.expect(requestTo(containsString(".webp?")))
                .andExpect(content().contentType("image/webp"))
                .andRespond(withStatus(HttpStatus.CREATED));
        FotoService fotoService = servicoComAzure();

        assertThat(fotoService.salvar(new MockMultipartFile("foto", "a.png", "image/png", PNG))).endsWith(".png");
        assertThat(fotoService.salvar(new MockMultipartFile("foto", "b.webp", "image/webp", WEBP))).endsWith(".webp");
        servidorAzure.verify();
    }

    @Test
    void recusaArquivoQueNaoEhImagemMesmoDeclarandoImageJpeg() {
        MockMultipartFile falso = new MockMultipartFile("foto", "foto.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> servicoComAzure().salvar(falso))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessage("Formato de foto inválido. Envie uma imagem JPEG, PNG ou WEBP");
        assertThatThrownBy(() -> servicoLocal().salvar(falso)).isInstanceOf(RequisicaoInvalidaException.class);
        assertThat(pasta).isEmptyDirectory();
        servidorAzure.verify();
    }

    @Test
    void recusaFotoMaiorQueCincoMegabytes() {
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;

        assertThatThrownBy(() -> servicoLocal().salvar(new MockMultipartFile("foto", "grande.jpg", "image/jpeg", grande)))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessage("A foto deve ter no máximo 5 MB");
    }

    @Test
    void erroDoAzureViraErroControlado() {
        servidorAzure.expect(method(HttpMethod.PUT)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> servicoComAzure().salvar(new MockMultipartFile("foto", "f.jpg", "image/jpeg", JPEG)))
                .isInstanceOf(ArmazenamentoFotoException.class)
                .hasMessage("Não foi possível salvar a foto. Tente novamente mais tarde");
    }

    @Test
    void urlDoContainerSemHttpsEhRecusadaAoIniciar() {
        assertThatThrownBy(() -> new FotoService(restClientBuilder, "http://contateste.blob.core.windows.net/fotos", SAS, pasta.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https://");
    }

    @Test
    void aceitaSasComInterrogacaoEUrlComBarraNoFinal() {
        servidorAzure.expect(requestTo(startsWith(CONTAINER + "/")))
                .andExpect(queryParam("sig", "assinatura-de-teste"))
                .andRespond(withStatus(HttpStatus.CREATED));
        FotoService fotoService = new FotoService(restClientBuilder, CONTAINER + "/", "?" + SAS, pasta.toString());

        String url = fotoService.salvar(new MockMultipartFile("foto", "f.jpg", "image/jpeg", JPEG));

        assertThat(url).startsWith(CONTAINER + "/");
        assertThat(url.substring("https://".length())).doesNotContain("//");
    }

    @Test
    void excluiFotoDoProprioContainer() {
        servidorAzure.expect(requestTo(CONTAINER + "/abc.jpg?" + SAS))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        servicoComAzure().excluir(CONTAINER + "/abc.jpg");

        servidorAzure.verify();
    }

    @Test
    void naoTentaExcluirUrlNulaNemDeOutroSite() {
        FotoService fotoService = servicoComAzure();

        fotoService.excluir(null);
        fotoService.excluir("https://outro-site.com/foto.jpg");

        servidorAzure.verify();
    }

    @Test
    void falhaAoExcluirNoAzureNaoInterrompeAOperacao() {
        servidorAzure.expect(method(HttpMethod.DELETE)).andRespond(withServerError());

        servicoComAzure().excluir(CONTAINER + "/abc.jpg");

        servidorAzure.verify();
    }
}
