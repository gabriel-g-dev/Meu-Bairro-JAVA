package br.com.fiap.meubairro.config;

import br.com.fiap.meubairro.entity.Categoria;
import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.repository.CategoriaRepository;
import br.com.fiap.meubairro.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CargaInicialDados implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CargaInicialDados.class);

    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean criarUsuariosTeste;
    private final String senhaUsuariosTeste;

    public CargaInicialDados(CategoriaRepository categoriaRepository,
                             UsuarioRepository usuarioRepository,
                             PasswordEncoder passwordEncoder,
                             @Value("${meubairro.usuarios-teste.habilitado:false}") boolean criarUsuariosTeste,
                             @Value("${meubairro.usuarios-teste.senha:}") String senhaUsuariosTeste) {
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.criarUsuariosTeste = criarUsuariosTeste;
        this.senhaUsuariosTeste = senhaUsuariosTeste;
    }

    @Override
    public void run(String... args) {
        criarCategoriasPadrao();
        if (criarUsuariosTeste) {
            criarUsuariosTeste();
        }
    }

    private void criarCategoriasPadrao() {
        List<Categoria> categorias = List.of(
                new Categoria("BURACO", "Buraco na via", "Buracos, afundamentos e desníveis no asfalto"),
                new Categoria("ILUMINACAO", "Iluminação pública", "Postes apagados, lâmpadas queimadas ou piscando"),
                new Categoria("LIXO", "Lixo e entulho", "Lixo acumulado, entulho e descarte irregular"),
                new Categoria("ALAGAMENTO", "Alagamento", "Ruas alagadas, bueiros entupidos e problemas de drenagem"),
                new Categoria("CALCADA", "Calçada danificada", "Calçadas quebradas, com degraus ou sem acessibilidade"),
                new Categoria("OUTRO", "Outros", "Problemas que não se encaixam nas demais categorias"));
        for (Categoria categoria : categorias) {
            if (!categoriaRepository.existsByCodigo(categoria.getCodigo())) {
                categoriaRepository.save(categoria);
                log.info("Categoria cadastrada: {}", categoria.getCodigo());
            }
        }
    }

    private void criarUsuariosTeste() {
        if (senhaUsuariosTeste == null || senhaUsuariosTeste.isBlank()) {
            log.warn("Usuários de teste não criados: defina a propriedade meubairro.usuarios-teste.senha");
            return;
        }
        criarSeNaoExistir("Cidadão de Teste", "cidadao@meubairro.com", Perfil.CIDADAO);
        criarSeNaoExistir("Gestor de Teste", "gestor@meubairro.com", Perfil.GESTOR);
    }

    private void criarSeNaoExistir(String nome, String email, Perfil perfil) {
        if (usuarioRepository.existsByEmail(email)) {
            return;
        }
        usuarioRepository.save(new Usuario(nome, email, passwordEncoder.encode(senhaUsuariosTeste), perfil));
        log.info("Usuário de teste criado: {} ({})", email, perfil);
    }
}
