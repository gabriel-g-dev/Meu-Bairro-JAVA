package br.com.fiap.meubairro.service;

import br.com.fiap.meubairro.dto.CadastroUsuarioRequest;
import br.com.fiap.meubairro.dto.UsuarioDetalheResponse;
import br.com.fiap.meubairro.dto.UsuarioResponse;
import br.com.fiap.meubairro.entity.Perfil;
import br.com.fiap.meubairro.entity.Usuario;
import br.com.fiap.meubairro.exception.RegraNegocioException;
import br.com.fiap.meubairro.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UsuarioResponse cadastrarCidadao(CadastroUsuarioRequest dados) {
        return new UsuarioResponse(cadastrar(dados, Perfil.CIDADAO));
    }

    public UsuarioDetalheResponse cadastrarGestor(CadastroUsuarioRequest dados) {
        return new UsuarioDetalheResponse(cadastrar(dados, Perfil.GESTOR));
    }

    private Usuario cadastrar(CadastroUsuarioRequest dados, Perfil perfil) {
        String email = dados.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmail(email)) {
            throw new RegraNegocioException("Já existe um usuário cadastrado com este e-mail");
        }
        Usuario usuario = new Usuario(dados.nome().trim(), email, passwordEncoder.encode(dados.senha()), perfil);
        return usuarioRepository.save(usuario);
    }
}
