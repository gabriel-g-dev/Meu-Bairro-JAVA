package br.com.fiap.meubairro;

import br.com.fiap.meubairro.repository.CategoriaRepository;
import br.com.fiap.meubairro.support.IntegracaoTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MeuBairroApplicationTests extends IntegracaoTestBase {

    @Autowired
    private CategoriaRepository repositorio;

    @Test
    void deveSubirAplicacaoECadastrarCategoriasPadrao() {
        assertThat(repositorio.findAllByOrderByIdAsc())
                .extracting("codigo")
                .containsExactly("BURACO", "ILUMINACAO", "LIXO", "ALAGAMENTO", "CALCADA", "OUTRO");
    }
}
