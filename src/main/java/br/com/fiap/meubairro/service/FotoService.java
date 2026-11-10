package br.com.fiap.meubairro.service;

import br.com.fiap.meubairro.exception.ArmazenamentoFotoException;
import br.com.fiap.meubairro.exception.RecursoNaoEncontradoException;
import br.com.fiap.meubairro.exception.RequisicaoInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FotoService {

    public static final String CAMINHO_FOTOS = "/fotos/";

    private static final Logger log = LoggerFactory.getLogger(FotoService.class);
    private static final long TAMANHO_MAXIMO = 5 * 1024 * 1024;
    private static final String VERSAO_API_AZURE = "2023-11-03";
    private static final Pattern NOME_DE_FOTO = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");

    private final RestClient restClient;
    private final String urlContainer;
    private final String tokenSas;
    private final Path pasta;

    public FotoService(RestClient.Builder restClientBuilder,
                       @Value("${meubairro.fotos.azure.container-url:}") String urlContainer,
                       @Value("${meubairro.fotos.azure.sas-token:}") String tokenSas,
                       @Value("${meubairro.fotos.pasta:./data/fotos}") String pasta) {
        this.restClient = restClientBuilder.build();
        this.urlContainer = removerBarraFinal(urlContainer.trim());
        this.tokenSas = tokenSas.trim().startsWith("?") ? tokenSas.trim().substring(1) : tokenSas.trim();
        this.pasta = Path.of(pasta).toAbsolutePath().normalize();
        if (!this.urlContainer.isEmpty() && !this.urlContainer.startsWith("https://")) {
            throw new IllegalStateException("FOTOS_AZURE_CONTAINER_URL precisa começar com https://");
        }
    }

    public String salvar(MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            return null;
        }
        if (foto.getSize() > TAMANHO_MAXIMO) {
            throw new RequisicaoInvalidaException("A foto deve ter no máximo 5 MB");
        }
        byte[] conteudo = lerConteudo(foto);
        String tipo = identificarTipo(conteudo);
        if (tipo == null) {
            throw new RequisicaoInvalidaException("Formato de foto inválido. Envie uma imagem JPEG, PNG ou WEBP");
        }

        String nomeArquivo = UUID.randomUUID() + extensao(tipo);
        if (azureConfigurado()) {
            enviarParaAzure(nomeArquivo, tipo, conteudo);
            return urlContainer + "/" + nomeArquivo;
        }
        salvarNaPasta(nomeArquivo, conteudo);
        return CAMINHO_FOTOS + nomeArquivo;
    }

    public FotoArmazenada carregar(String nomeArquivo) {
        if (nomeArquivo == null || !NOME_DE_FOTO.matcher(nomeArquivo).matches()) {
            throw new RecursoNaoEncontradoException("Foto não encontrada");
        }
        Path arquivo = pasta.resolve(nomeArquivo);
        if (!Files.isRegularFile(arquivo)) {
            throw new RecursoNaoEncontradoException("Foto não encontrada");
        }
        try {
            return new FotoArmazenada(Files.readAllBytes(arquivo), tipoPelaExtensao(nomeArquivo));
        } catch (IOException e) {
            throw new RecursoNaoEncontradoException("Foto não encontrada");
        }
    }

    public void excluir(String fotoUrl) {
        if (fotoUrl == null) {
            return;
        }
        if (fotoUrl.startsWith(CAMINHO_FOTOS)) {
            excluirDaPasta(fotoUrl.substring(CAMINHO_FOTOS.length()));
            return;
        }
        if (!azureConfigurado() || !fotoUrl.startsWith(urlContainer + "/")) {
            return;
        }
        try {
            restClient.delete()
                    .uri(URI.create(fotoUrl + "?" + tokenSas))
                    .header("x-ms-version", VERSAO_API_AZURE)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Não foi possível excluir a foto {} ({})", fotoUrl, e.getClass().getSimpleName());
        }
    }

    private void enviarParaAzure(String nomeArquivo, String tipo, byte[] conteudo) {
        try {
            restClient.put()
                    .uri(URI.create(urlContainer + "/" + nomeArquivo + "?" + tokenSas))
                    .header("x-ms-blob-type", "BlockBlob")
                    .header("x-ms-version", VERSAO_API_AZURE)
                    .contentType(MediaType.parseMediaType(tipo))
                    .body(conteudo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Falha ao enviar a foto para o Azure Blob Storage ({})", e.getClass().getSimpleName());
            throw new ArmazenamentoFotoException("Não foi possível salvar a foto. Tente novamente mais tarde");
        }
    }

    private void salvarNaPasta(String nomeArquivo, byte[] conteudo) {
        try {
            Files.createDirectories(pasta);
            Files.write(pasta.resolve(nomeArquivo), conteudo);
        } catch (IOException e) {
            log.warn("Falha ao gravar a foto na pasta {} ({})", pasta, e.getClass().getSimpleName());
            throw new ArmazenamentoFotoException("Não foi possível salvar a foto. Tente novamente mais tarde");
        }
    }

    private void excluirDaPasta(String nomeArquivo) {
        if (!NOME_DE_FOTO.matcher(nomeArquivo).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(pasta.resolve(nomeArquivo));
        } catch (IOException e) {
            log.warn("Não foi possível excluir a foto {} ({})", nomeArquivo, e.getClass().getSimpleName());
        }
    }

    private boolean azureConfigurado() {
        return !urlContainer.isEmpty() && !tokenSas.isEmpty();
    }

    private byte[] lerConteudo(MultipartFile foto) {
        try {
            return foto.getBytes();
        } catch (IOException e) {
            throw new RequisicaoInvalidaException("Não foi possível ler a foto enviada");
        }
    }

    private String identificarTipo(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private String extensao(String tipo) {
        return switch (tipo) {
            case MediaType.IMAGE_JPEG_VALUE -> ".jpg";
            case MediaType.IMAGE_PNG_VALUE -> ".png";
            default -> ".webp";
        };
    }

    private MediaType tipoPelaExtensao(String nomeArquivo) {
        if (nomeArquivo.endsWith(".jpg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (nomeArquivo.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        return MediaType.parseMediaType("image/webp");
    }

    private String removerBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record FotoArmazenada(byte[] conteudo, MediaType tipo) {
    }
}
