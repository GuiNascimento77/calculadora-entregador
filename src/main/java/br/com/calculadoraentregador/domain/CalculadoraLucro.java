package br.com.calculadoraentregador.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Primeira regra de domínio: receita menos custos atribuídos ao trabalho. */
public final class CalculadoraLucro {

    public BigDecimal calcular(BigDecimal receita, BigDecimal custos) {
        return calcularExato(receita, custos).setScale(2, RoundingMode.HALF_UP);
    }

    /** Usado pelos indicadores para não arredondar antes de calcular taxas. */
    public BigDecimal calcularExato(BigDecimal receita, BigDecimal custos) {
        Objects.requireNonNull(receita, "Receita é obrigatória.");
        Objects.requireNonNull(custos, "Custos são obrigatórios.");

        if (receita.signum() < 0 || custos.signum() < 0) {
            throw new IllegalArgumentException("Receita e custos não podem ser negativos.");
        }

        return receita.subtract(custos);
    }
}