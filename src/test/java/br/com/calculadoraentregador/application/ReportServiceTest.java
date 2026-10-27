package br.com.calculadoraentregador.application;

import br.com.calculadoraentregador.domain.Metrics;
import br.com.calculadoraentregador.persistence.Database;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static br.com.calculadoraentregador.domain.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {
    @TempDir Path dir; Database db; DeliveryService s; ReportService reports; long vehicle;
    private BigDecimal b(String n) { return new BigDecimal(n); }
    private LocalDate d(String n) { return LocalDate.parse(n); }
    @BeforeEach void setup() throws Exception {
        db = new Database(dir.resolve("report.db")); s = new DeliveryService(db); reports = new ReportService(s.repository());
        vehicle = s.addVehicle("Moto",TipoVeiculo.MOTO,b("10000"),b("30"),b("6"),b("20"));
    }
    @AfterEach void close() throws Exception { db.close(); }
    private long open(String date, String km) throws Exception {
        return s.open(vehicle,LocalDateTime.parse(date + "T08:00"),b(km));
    }
    private void close(long shift, String date, String km) throws Exception {
        s.close(shift,LocalDateTime.parse(date + "T12:00"),b(km));
    }
    private ReportService.Relatorio day(String date) throws Exception {
        return reports.report(ReportService.intervalo(ReportService.Periodo.DIARIO,d(date)),vehicle);
    }
    @Test void separatesCashAndProfitAndIncludesUnpaidDistance() throws Exception {
        s.addPlan(vehicle,"Pneu",b("10000"),b("200"),b("9000"));
        long j = open("2026-10-19","10000");
        s.addRide(j,"A",b("120"),b("40"),80); s.addRide(j,"B",b("80"),b("20"),40);
        s.addExpense(vehicle,null,d("2026-10-19"),CategoriaDespesa.COMBUSTIVEL,b("60"),"Tanque");
        s.addExpense(vehicle,j,d("2026-10-19"),CategoriaDespesa.EXTRA,b("5"),"Pedágio");
        close(j,"2026-10-19","10100"); var r = day("2026-10-19");
        assertEquals(b("153.00"),Metrics.cents(r.desempenho().lucro()));
        assertEquals(b("135.00"),Metrics.cents(r.saldoCaixa())); assertEquals(2,r.plataformas().size());
        assertEquals(b("31.33"),Metrics.cents(r.plataformas().getFirst().custoAlocado()));
        assertEquals(b("88.67"),Metrics.cents(r.plataformas().getFirst().lucroEstimado()));
        BigDecimal allocated = r.plataformas().stream().map(ReportService.Plataforma::custoAlocado).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertEquals(Metrics.cents(r.desempenho().custos()),Metrics.cents(allocated));
    }
    @Test void excludesOpenShiftFromPerformanceButNotCash() throws Exception {
        long j = open("2026-10-19","10000"); s.addRide(j,"A",b("50"),b("10"),10);
        var r = day("2026-10-19"); assertEquals(1,r.abertas()); assertEquals(0,r.fechadas());
        assertEquals(b("50"),r.entradasCaixa()); assertEquals(BigDecimal.ZERO,r.desempenho().receita());
        assertTrue(r.plataformas().isEmpty());
    }
    @Test void platformFallbackUsesTimeWhenAllRideKmAreZero() throws Exception {
        long j = open("2026-10-19","10000");
        s.addRide(j,"A",b("10"),b("0"),30); s.addRide(j,"B",b("10"),b("0"),90);
        close(j,"2026-10-19","10000"); var r = day("2026-10-19");
        assertEquals(b("5.00"),Metrics.cents(r.plataformas().getFirst().custoAlocado()));
        assertEquals(b("15.00"),Metrics.cents(r.plataformas().getLast().custoAlocado()));
        assertNull(r.plataformas().getFirst().brutoPorKmEntrega());
    }
    @Test void emptyShiftCostsRemainInComparison() throws Exception {
        long j = open("2026-10-19","10000"); close(j,"2026-10-19","10000");
        var r = day("2026-10-19"); assertEquals("(SEM CORRIDAS)",r.plataformas().getFirst().nome());
        assertEquals(b("-20.00"),Metrics.cents(r.plataformas().getFirst().lucroEstimado()));
    }
    @Test void weekAndMonthBoundariesIncludingLeapYear() {
        assertEquals(new ReportService.Intervalo(d("2026-10-19"),d("2026-10-25")),
                ReportService.intervalo(ReportService.Periodo.SEMANAL,d("2026-10-25")));
        assertEquals(new ReportService.Intervalo(d("2024-02-01"),d("2024-02-29")),
                ReportService.intervalo(ReportService.Periodo.MENSAL,d("2024-02-10")));
        assertEquals(new ReportService.Intervalo(d("2025-12-29"),d("2026-01-04")),
                ReportService.intervalo(ReportService.Periodo.SEMANAL,d("2026-01-01")));
    }
    @Test void inclusivePeriodFiltersVehiclesAndExpenseDates() throws Exception {
        long j = open("2026-10-19","10000"); s.addRide(j,"A",b("100"),b("10"),10); close(j,"2026-10-19","10010");
        long j2 = open("2026-10-20","10010"); s.addRide(j2,"A",b("50"),b("10"),10); close(j2,"2026-10-20","10020");
        s.addExpense(vehicle,null,d("2026-10-21"),CategoriaDespesa.FIXO,b("600"),"Aluguel");
        long bike = s.addVehicle("Bike",TipoVeiculo.BIKE,b("0"),b("0"),b("0"),b("0"));
        long bshift = s.open(bike,LocalDateTime.parse("2026-10-19T08:00"),b("0"));
        s.addRide(bshift,"C",b("999"),b("1"),1); s.close(bshift,LocalDateTime.parse("2026-10-19T09:00"),b("1"));
        var r = reports.report(new ReportService.Intervalo(d("2026-10-19"),d("2026-10-20")),vehicle);
        assertEquals(b("150"),r.desempenho().receita()); assertEquals(2,r.fechadas()); assertEquals(BigDecimal.ZERO,r.pagamentos());
        assertEquals(b("600"),day("2026-10-21").pagamentos());
        assertEquals(b("1099"),reports.report(new ReportService.Intervalo(d("2026-10-19"),d("2026-10-19")),null).entradasCaixa());
    }
    @Test void rejectsInvertedPeriodAndMissingVehicle() {
        assertThrows(IllegalArgumentException.class,() -> reports.report(new ReportService.Intervalo(d("2026-10-20"),d("2026-10-19")),vehicle));
        assertThrows(IllegalArgumentException.class,() -> reports.report(new ReportService.Intervalo(d("2026-10-19"),d("2026-10-19")),999L));
    }
}