package br.com.calculadoraentregador.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import static br.com.calculadoraentregador.domain.Models.*;

/** Cálculos puros; precisão decimal preservada até a apresentação. */
public final class Metrics {
    public static final MathContext MC = MathContext.DECIMAL128;
    private static final CalculadoraLucro CALCULADORA = new CalculadoraLucro();
    private Metrics() {}

    public record Resumo(BigDecimal receita, BigDecimal combustivel, BigDecimal manutencao,
                         BigDecimal fixos, BigDecimal extras, BigDecimal km, long minutos) {
        public BigDecimal custos() { return combustivel.add(manutencao).add(fixos).add(extras); }
        public BigDecimal lucro() { return CALCULADORA.calcularExato(receita, custos()); }
        public BigDecimal custoPorKm() { return ratio(custos(), km); }
        public BigDecimal ganhoPorHora() {
            return ratio(lucro().multiply(BigDecimal.valueOf(60)), BigDecimal.valueOf(minutos));
        }
    }

    public static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return denominator.signum() == 0 ? null : numerator.divide(denominator, MC);
    }

    public static BigDecimal cents(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    public static Resumo jornada(Jornada j, List<Corrida> corridas, List<Despesa> despesas) {
        Checks.require(!j.aberta(), "Feche a jornada para calcular o desempenho.");
        BigDecimal km = j.kmFim().subtract(j.kmInicio());
        BigDecimal receita = corridas.stream().filter(c -> c.jornadaId() == j.id())
                .map(Corrida::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extras = despesas.stream().filter(d -> d.categoria() == CategoriaDespesa.EXTRA
                && d.jornadaId() != null && d.jornadaId() == j.id())
                .map(Despesa::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Resumo(receita, km.multiply(j.combustivelPorKm(), MC),
                km.multiply(j.manutencaoPorKm(), MC), j.fixoDiario(), extras, km,
                Duration.between(j.inicio(), j.fim()).toMinutes());
    }

    public static Resumo somar(List<Resumo> list) {
        BigDecimal receita = BigDecimal.ZERO, fuel = BigDecimal.ZERO, maintenance = BigDecimal.ZERO;
        BigDecimal fixed = BigDecimal.ZERO, extras = BigDecimal.ZERO, km = BigDecimal.ZERO;
        long minutes = 0;
        for (Resumo r : list) {
            receita = receita.add(r.receita()); fuel = fuel.add(r.combustivel());
            maintenance = maintenance.add(r.manutencao()); fixed = fixed.add(r.fixos());
            extras = extras.add(r.extras()); km = km.add(r.km()); minutes += r.minutos();
        }
        return new Resumo(receita, fuel, maintenance, fixed, extras, km, minutes);
    }
}