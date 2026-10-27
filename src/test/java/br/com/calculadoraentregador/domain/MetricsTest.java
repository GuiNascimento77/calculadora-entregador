package br.com.calculadoraentregador.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static br.com.calculadoraentregador.domain.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class MetricsTest {
    private BigDecimal b(String s) { return new BigDecimal(s); }
    private Jornada j(String startKm, String endKm) {
        return new Jornada(1,1,LocalDateTime.parse("2026-10-19T08:00"),LocalDateTime.parse("2026-10-19T12:00"),
                b(startKm),b(endKm),b("0.2"),b("0.02"),b("20"));
    }
    private Corrida ride() { return new Corrida(1,1,"IFOOD",b("200"),b("60"),120); }

    @Test void includesUnpaidDistanceAndWaitingTime() {
        var r = Metrics.jornada(j("10000","10100"),List.of(ride()),List.of());
        assertEquals(b("100"),r.km()); assertEquals(240,r.minutos());
        assertEquals(b("42.00"),Metrics.cents(r.custos()));
        assertEquals(b("158.00"),Metrics.cents(r.lucro()));
        assertEquals(b("0.42"),Metrics.cents(r.custoPorKm()));
        assertEquals(b("39.50"),Metrics.cents(r.ganhoPorHora()));
    }
    @Test void fuelMaintenanceAndFixedPaymentsAreNotCountedTwice() {
        var date = LocalDate.parse("2026-10-19");
        var expenses = List.of(new Despesa(1,1,1L,date,CategoriaDespesa.COMBUSTIVEL,b("100"),"Tanque"),
                new Despesa(2,1,1L,date,CategoriaDespesa.MANUTENCAO,b("200"),"Pneu"),
                new Despesa(3,1,1L,date,CategoriaDespesa.FIXO,b("600"),"Aluguel"),
                new Despesa(4,1,1L,date,CategoriaDespesa.EXTRA,b("5"),"Pedágio"),
                new Despesa(5,2,2L,date,CategoriaDespesa.EXTRA,b("99"),"Outra jornada"));
        var r = Metrics.jornada(j("10000","10100"),List.of(ride()),expenses);
        assertEquals(b("47.00"),Metrics.cents(r.custos())); assertEquals(b("153.00"),Metrics.cents(r.lucro()));
    }
    @Test void zeroKmDoesNotDivideByZero() {
        var r = Metrics.jornada(j("10000","10000"),List.of(),List.of());
        assertNull(r.custoPorKm()); assertEquals(b("-20.00"),Metrics.cents(r.lucro()));
    }
    @Test void emptyPeriodHasUndefinedRatios() {
        var r = Metrics.somar(List.of());
        assertNull(r.custoPorKm()); assertNull(r.ganhoPorHora()); assertEquals(BigDecimal.ZERO,r.lucro());
    }
    @Test void aggregatesBeforeComputingRatios() {
        var a = new Metrics.Resumo(b("100"),b("10"),b("0"),b("0"),b("0"),b("10"),60);
        var c = new Metrics.Resumo(b("200"),b("20"),b("0"),b("0"),b("0"),b("100"),120);
        var r = Metrics.somar(List.of(a,c));
        assertEquals(b("90.00"),Metrics.cents(r.ganhoPorHora()));
        assertEquals(b("0.27"),Metrics.cents(r.custoPorKm()));
    }
    @Test void openShiftCannotBeCalculated() {
        var open = new Jornada(1,1,LocalDateTime.parse("2026-10-19T08:00"),null,b("1"),null,b("0"),b("0"),b("0"));
        assertThrows(IllegalArgumentException.class,() -> Metrics.jornada(open,List.of(),List.of()));
    }
    @Test void decimalMathKeepsPrecisionUntilRounding() {
        assertEquals(b("0.01"),Metrics.cents(b("0.014").subtract(b("0.006"))));
        assertEquals(b("-0.01"),Metrics.cents(b("-0.005")));
    }
    @Test void foreignRidesAreExcluded() {
        var r = Metrics.jornada(j("10000","10100"),List.of(new Corrida(9,8,"OTHER",b("999"),b("1"),1)),List.of());
        assertEquals(BigDecimal.ZERO,r.receita());
    }
}