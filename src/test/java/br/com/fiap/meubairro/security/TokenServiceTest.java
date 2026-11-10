package br.com.fiap.meubairro.security;

import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.entity.Usuario;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String SEGREDO = "segredo-do-teste-unitario-com-mais-de-32-caracteres";

    private Usuario usuario(Long id, String email) {
        Usuario usuario = new Usuario("Usuário " + id, email, "senha-criptografada", Perfil.CIDADAO);
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    @Test
    void deveGerarTokenEValidarDevolvendoOEmail() {
        TokenService tokenService = new TokenService(SEGREDO, 120);

        String token = tokenService.gerarToken(usuario(1L, "maria@teste.com"));

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(tokenService.validarToken(token)).isEqualTo("maria@teste.com");
    }

    @Test
    void deveRejeitarTokenComPayloadAdulterado() {
        TokenService tokenService = new TokenService(SEGREDO, 120);
        String[] tokenMaria = tokenService.gerarToken(usuario(1L, "maria@teste.com")).split("\\.");
        String[] tokenJoao = tokenService.gerarToken(usuario(2L, "joao@teste.com")).split("\\.");

        String tokenAdulterado = tokenMaria[0] + "." + tokenJoao[1] + "." + tokenMaria[2];

        assertThatThrownBy(() -> tokenService.validarToken(tokenAdulterado)).isInstanceOf(JwtException.class);
    }

    @Test
    void deveRejeitarTokenAssinadoComOutraChave() {
        TokenService emissorDesconhecido = new TokenService("outra-chave-secreta-com-mais-de-32-caracteres", 120);
        TokenService tokenService = new TokenService(SEGREDO, 120);

        String token = emissorDesconhecido.gerarToken(usuario(1L, "maria@teste.com"));

        assertThatThrownBy(() -> tokenService.validarToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void deveRejeitarTokenExpirado() {
        TokenService tokenService = new TokenService(SEGREDO, -1);

        String token = tokenService.gerarToken(usuario(1L, "maria@teste.com"));

        assertThatThrownBy(() -> tokenService.validarToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void deveRejeitarTextoQueNaoEhToken() {
        TokenService tokenService = new TokenService(SEGREDO, 120);

        assertThatThrownBy(() -> tokenService.validarToken("nao-e-um-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void deveExigirSegredoComPeloMenos32Caracteres() {
        assertThatThrownBy(() -> new TokenService("curto", 120))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 caracteres");
    }

    @Test
    void deveUsarChaveAleatoriaQuandoSegredoNaoForInformado() {
        TokenService primeiraInstancia = new TokenService("", 120);
        TokenService segundaInstancia = new TokenService("", 120);

        String token = primeiraInstancia.gerarToken(usuario(1L, "maria@teste.com"));

        assertThat(primeiraInstancia.validarToken(token)).isEqualTo("maria@teste.com");
        assertThatThrownBy(() -> segundaInstancia.validarToken(token)).isInstanceOf(JwtException.class);
    }
}
