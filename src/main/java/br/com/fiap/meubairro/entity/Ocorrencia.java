package br.com.fiap.meubairro.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "ocorrencias")
public class Ocorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 1000)
    private String descricao;

    @Column(nullable = false, length = 200)
    private String endereco;

    private Double latitude;

    private Double longitude;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusOcorrencia status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridade prioridade;

    @Column(name = "prioridade_manual", nullable = false)
    private boolean prioridadeManual;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_analise_ia", nullable = false, length = 20)
    private StatusAnaliseIa statusAnaliseIa;

    @ManyToOne
    @JoinColumn(name = "categoria_sugerida_id")
    private Categoria categoriaSugerida;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade_sugerida", length = 10)
    private Prioridade prioridadeSugerida;

    @Column(name = "justificativa_ia", length = 500)
    private String justificativaIa;

    @Column(name = "data_analise_ia")
    private LocalDateTime dataAnaliseIa;

    protected Ocorrencia() {
    }

    public Ocorrencia(String titulo, String descricao, String endereco, Double latitude, Double longitude,
                      String fotoUrl, Categoria categoria, Usuario usuario) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.endereco = endereco;
        this.latitude = latitude;
        this.longitude = longitude;
        this.fotoUrl = fotoUrl;
        this.categoria = categoria;
        this.usuario = usuario;
        this.status = StatusOcorrencia.ABERTA;
        this.prioridade = Prioridade.MEDIA;
        this.prioridadeManual = false;
        this.statusAnaliseIa = StatusAnaliseIa.PENDENTE;
        this.dataCriacao = agora();
    }

    public boolean pertenceA(Usuario outroUsuario) {
        return usuario.getId().equals(outroUsuario.getId());
    }

    public boolean estaAberta() {
        return status == StatusOcorrencia.ABERTA;
    }

    public void atualizarDados(String titulo, String descricao, String endereco, Double latitude, Double longitude,
                               Categoria categoria) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.endereco = endereco;
        this.latitude = latitude;
        this.longitude = longitude;
        this.categoria = categoria;
        this.dataAtualizacao = agora();
    }

    public void alterarFoto(String fotoUrl) {
        this.fotoUrl = fotoUrl;
        this.dataAtualizacao = agora();
    }

    public void alterarStatus(StatusOcorrencia novoStatus) {
        this.status = novoStatus;
        this.dataAtualizacao = agora();
    }

    public void definirPrioridadeManual(Prioridade novaPrioridade) {
        this.prioridade = novaPrioridade;
        this.prioridadeManual = true;
        this.dataAtualizacao = agora();
    }

    public boolean registrarAnaliseIa(Categoria categoriaSugerida, Prioridade prioridadeSugerida, String justificativa) {
        this.categoriaSugerida = categoriaSugerida;
        this.prioridadeSugerida = prioridadeSugerida;
        this.justificativaIa = justificativa;
        this.statusAnaliseIa = StatusAnaliseIa.CONCLUIDA;
        this.dataAnaliseIa = agora();
        if (estaAberta() && !prioridadeManual) {
            this.prioridade = prioridadeSugerida;
            return true;
        }
        return false;
    }

    public void registrarFalhaAnaliseIa() {
        this.statusAnaliseIa = StatusAnaliseIa.FALHA;
        this.dataAnaliseIa = agora();
    }

    private LocalDateTime agora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getEndereco() {
        return endereco;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public StatusOcorrencia getStatus() {
        return status;
    }

    public Prioridade getPrioridade() {
        return prioridade;
    }

    public boolean isPrioridadeManual() {
        return prioridadeManual;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public LocalDateTime getDataAtualizacao() {
        return dataAtualizacao;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public StatusAnaliseIa getStatusAnaliseIa() {
        return statusAnaliseIa;
    }

    public Categoria getCategoriaSugerida() {
        return categoriaSugerida;
    }

    public Prioridade getPrioridadeSugerida() {
        return prioridadeSugerida;
    }

    public String getJustificativaIa() {
        return justificativaIa;
    }

    public LocalDateTime getDataAnaliseIa() {
        return dataAnaliseIa;
    }
}
