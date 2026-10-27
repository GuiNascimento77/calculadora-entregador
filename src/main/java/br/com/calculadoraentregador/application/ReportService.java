package br.com.calculadoraentregador.application;

import br.com.calculadoraentregador.domain.Metrics;
import br.com.calculadoraentregador.persistence.Repository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import static br.com.calculadoraentregador.domain.Models.*;
import static br.com.calculadoraentregador.domain.Checks.require;

public final class ReportService {
    public enum Periodo { DIARIO, SEMANAL, MENSAL }
    public record Intervalo(LocalDate de, LocalDate ate) {
        public boolean contem(LocalDate date) { return !date.isBefore(de) && !date.isAfter(ate); }
    }
    public record Plataforma(String nome, int corridas, BigDecimal receita, BigDecimal km, long minutos,
                             BigDecimal custoAlocado) {
        public BigDecimal lucroEstimado() { return receita.subtract(custoAlocado); }
        public BigDecimal brutoPorKmEntrega() { return Metrics.ratio(receita,km); }
        public BigDecimal brutoPorHoraAtiva() {
            return Metrics.ratio(receita.multiply(BigDecimal.valueOf(60)),BigDecimal.valueOf(minutos));
        }
    }
    public record Relatorio(Intervalo intervalo, Metrics.Resumo desempenho, int fechadas, int abertas,
                            BigDecimal entradasCaixa, BigDecimal pagamentos, List<Plataforma> plataformas) {
        public BigDecimal saldoCaixa() { return entradasCaixa.subtract(pagamentos); }
    }
    private final Repository repo;
    public ReportService(Repository repo) { this.repo = repo; }

    private static final class Acumulador {
        BigDecimal receita = BigDecimal.ZERO;
        BigDecimal km = BigDecimal.ZERO;
        BigDecimal custos = BigDecimal.ZERO;
        long minutos;
        int quantidade;
        Plataforma resultado(String nome) { return new Plataforma(nome,quantidade,receita,km,minutos,custos); }
    }

    public static Intervalo intervalo(Periodo period, LocalDate date) {
        Objects.requireNonNull(period); Objects.requireNonNull(date);
        return switch (period) {
            case DIARIO -> new Intervalo(date,date);
            case SEMANAL -> {
                LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new Intervalo(monday,monday.plusDays(6));
            }
            case MENSAL -> new Intervalo(date.withDayOfMonth(1),date.with(TemporalAdjusters.lastDayOfMonth()));
        };
    }

    public Relatorio report(Intervalo range, Long vehicle) throws SQLException {
        Objects.requireNonNull(range);
        require(!range.ate().isBefore(range.de()),"Intervalo de relatório inválido.");
        if (vehicle != null) repo.vehicle(vehicle);
        List<Jornada> shifts = repo.shifts().stream().filter(j -> range.contem(j.data())
                && (vehicle == null || j.veiculoId() == vehicle)).toList();
        List<Corrida> rides = repo.rides(); List<Despesa> expenses = repo.expenses();
        Set<Long> selected = new HashSet<>(); shifts.forEach(j -> selected.add(j.id()));
        BigDecimal cashIn = rides.stream().filter(r -> selected.contains(r.jornadaId()))
                .map(Corrida::valor).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal cashOut = expenses.stream().filter(d -> range.contem(d.data())
                && (vehicle == null || d.veiculoId() == vehicle)).map(Despesa::valor).reduce(BigDecimal.ZERO,BigDecimal::add);
        List<Metrics.Resumo> summaries = new ArrayList<>();
        Map<String,Acumulador> totals = new TreeMap<>();
        int open = 0;
        for (Jornada j : shifts) {
            if (j.aberta()) { open++; continue; }
            List<Corrida> jr = rides.stream().filter(r -> r.jornadaId() == j.id()).toList();
            Metrics.Resumo summary = Metrics.jornada(j,jr,expenses); summaries.add(summary);
            Map<String,List<Corrida>> grouped = new TreeMap<>();
            jr.forEach(r -> grouped.computeIfAbsent(r.plataforma(),k -> new ArrayList<>()).add(r));
            BigDecimal rideKm = jr.stream().map(Corrida::km).reduce(BigDecimal.ZERO,BigDecimal::add);
            long minutes = jr.stream().mapToLong(Corrida::minutos).sum();
            if (grouped.isEmpty()) {
                Acumulador t = totals.computeIfAbsent("(SEM CORRIDAS)",k -> new Acumulador());
                t.custos = t.custos.add(summary.custos());
            }
            for (var entry : grouped.entrySet()) {
                List<Corrida> list = entry.getValue();
                BigDecimal revenue = list.stream().map(Corrida::valor).reduce(BigDecimal.ZERO,BigDecimal::add);
                BigDecimal km = list.stream().map(Corrida::km).reduce(BigDecimal.ZERO,BigDecimal::add);
                long active = list.stream().mapToLong(Corrida::minutos).sum();
                BigDecimal weight = rideKm.signum() > 0 ? km.divide(rideKm,Metrics.MC)
                        : minutes > 0 ? BigDecimal.valueOf(active).divide(BigDecimal.valueOf(minutes),Metrics.MC)
                        : BigDecimal.ONE.divide(BigDecimal.valueOf(grouped.size()),Metrics.MC);
                Acumulador t = totals.computeIfAbsent(entry.getKey(),k -> new Acumulador());
                t.receita = t.receita.add(revenue); t.km = t.km.add(km); t.minutos += active;
                t.custos = t.custos.add(summary.custos().multiply(weight,Metrics.MC)); t.quantidade += list.size();
            }
        }
        List<Plataforma> platforms = totals.entrySet().stream().map(e -> e.getValue().resultado(e.getKey())).toList();
        return new Relatorio(range,Metrics.somar(summaries),summaries.size(),open,cashIn,cashOut,platforms);
    }

}