package br.com.fiap.meubairro.dto;

public record ResumoOcorrenciasResponse(
        long total,
        long abertas,
        long emAnalise,
        long resolvidas,
        long prioridadeAlta,
        long prioridadeMedia,
        long prioridadeBaixa) {
}
