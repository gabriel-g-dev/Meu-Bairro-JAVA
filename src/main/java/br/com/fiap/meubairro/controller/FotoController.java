package br.com.fiap.meubairro.controller;

import br.com.fiap.meubairro.service.FotoService;
import br.com.fiap.meubairro.service.FotoService.FotoArmazenada;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Fotos", description = "Imagens enviadas junto com as ocorrências")
public class FotoController {

    private final FotoService fotoService;

    public FotoController(FotoService fotoService) {
        this.fotoService = fotoService;
    }

    @GetMapping("/fotos/{arquivo}")
    @Operation(summary = "Devolve a foto de uma ocorrência",
            description = "Rota pública usada pela fotoUrl, para o app exibir a imagem sem enviar o token.")
    public ResponseEntity<byte[]> buscar(@PathVariable String arquivo) {
        FotoArmazenada foto = fotoService.carregar(arquivo);
        return ResponseEntity.ok().contentType(foto.tipo()).body(foto.conteudo());
    }
}
