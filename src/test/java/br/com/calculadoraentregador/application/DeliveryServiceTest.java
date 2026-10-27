package br.com.calculadoraentregador.application;

import br.com.calculadoraentregador.persistence.Database;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static br.com.calculadoraentregador.domain.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryServiceTest {
    @TempDir Path dir;
    Database db; DeliveryService s; long vehicle;
    private BigDecimal b(String n) { return new BigDecimal(n); }
    private LocalDateTime time(String n) { return LocalDateTime.parse(n); }
    private LocalDate date(String n) { return LocalDate.parse(n); }
    @BeforeEach void setup() throws Exception {
        db = new Database(dir.resolve("test.db")); s = new DeliveryService(db);
        vehicle = s.addVehicle("Moto",TipoVeiculo.MOTO,b("10000"),b("30"),b("6"),b("20"));
    }
    @AfterEach void close() throws Exception { db.close(); }
    private long open() throws Exception { return s.open(vehicle,time("2026-10-19T08:00"),b("10000")); }
    @Test void completeShiftPersistsExactDecimalInputs() throws Exception {
        long id = open(); s.addRide(id," iFood ",b("200.10"),b("60.123456"),120);
        s.close(id,time("2026-10-19T12:00"),b("10100"));
        assertFalse(s.repository().shift(id).aberta());
        assertEquals("IFOOD",s.repository().rides().getFirst().plataforma());
        assertEquals(b("60.123456"),s.repository().rides().getFirst().km());
        assertEquals(b("10100"),s.repository().vehicle(vehicle).odometro());
    }
    @Test void preventsMultipleOpenShiftsAndSameDayDuplicate() throws Exception {
        long id = open();
        assertThrows(IllegalArgumentException.class,() -> s.open(vehicle,time("2026-10-20T08:00"),b("10000")));
        s.close(id,time("2026-10-19T12:00"),b("10000"));
        assertThrows(IllegalArgumentException.class,() -> s.open(vehicle,time("2026-10-19T13:00"),b("10000")));
    }
    @Test void chronologicalAndOdometerChecks() throws Exception {
        long id = open(); s.close(id,time("2026-10-19T12:00"),b("10100"));
        assertThrows(IllegalArgumentException.class,() -> s.open(vehicle,time("2026-10-18T08:00"),b("10100")));
        assertThrows(IllegalArgumentException.class,() -> s.open(vehicle,time("2026-10-20T08:00"),b("10099")));
    }
    @Test void failedClosingIsRolledBack() throws Exception {
        long id = open(); s.addRide(id,"A",b("10"),b("60"),120);
        assertThrows(IllegalArgumentException.class,() -> s.close(id,time("2026-10-19T12:00"),b("10050")));
        assertTrue(s.repository().shift(id).aberta()); assertEquals(b("10000"),s.repository().vehicle(vehicle).odometro());
        assertThrows(IllegalArgumentException.class,() -> s.close(id,time("2026-10-19T09:00"),b("10100")));
        assertTrue(s.repository().shift(id).aberta());
    }
    @Test void rejectsInvalidEndTimeOrOdometer() throws Exception {
        long id = open();
        assertThrows(IllegalArgumentException.class,() -> s.close(id,time("2026-10-19T08:00"),b("10000")));
        assertThrows(IllegalArgumentException.class,() -> s.close(id,time("2026-10-20T01:00"),b("10000")));
        assertThrows(IllegalArgumentException.class,() -> s.close(id,time("2026-10-19T09:00"),b("9999")));
    }
    @Test void closedShiftIsImmutable() throws Exception {
        long id = open(); s.close(id,time("2026-10-19T12:00"),b("10000"));
        assertThrows(IllegalArgumentException.class,() -> s.addRide(id,"A",b("10"),b("0"),1));
        assertThrows(IllegalArgumentException.class,() -> s.close(id,time("2026-10-19T13:00"),b("10000")));
        assertThrows(IllegalArgumentException.class,() -> s.addExpense(vehicle,id,date("2026-10-19"),CategoriaDespesa.EXTRA,b("1"),"Extra"));
    }
    @Test void expensesMustMatchVehicleDateAndOpenShiftForExtras() throws Exception {
        long id = open(); long other = s.addVehicle("Bike",TipoVeiculo.BIKE,b("0"),b("0"),b("0"),b("0"));
        assertThrows(IllegalArgumentException.class,() -> s.addExpense(vehicle,null,date("2026-10-19"),CategoriaDespesa.EXTRA,b("1"),"Extra"));
        assertThrows(IllegalArgumentException.class,() -> s.addExpense(other,id,date("2026-10-19"),CategoriaDespesa.EXTRA,b("1"),"Extra"));
        assertThrows(IllegalArgumentException.class,() -> s.addExpense(vehicle,id,date("2026-10-20"),CategoriaDespesa.EXTRA,b("1"),"Extra"));
        assertTrue(s.repository().expenses().isEmpty());
    }
    @Test void snapshotDoesNotChangeAfterNewSettingsAndPlans() throws Exception {
        s.addPlan(vehicle,"Pneu",b("10000"),b("200"),b("9000"));
        long id = open(); var snapshot = s.repository().shift(id);
        assertEquals(0,snapshot.combustivelPorKm().compareTo(b("0.2")));
        assertEquals(0,snapshot.manutencaoPorKm().compareTo(b("0.02")));
        assertThrows(IllegalArgumentException.class,() -> s.configure(vehicle,b("30"),b("9"),b("50")));
        s.close(id,time("2026-10-19T12:00"),b("10100")); s.configure(vehicle,b("30"),b("9"),b("50"));
        s.addPlan(vehicle,"Óleo",b("1000"),b("30"),b("10000"));
        assertEquals(snapshot.combustivelPorKm(),s.repository().shift(id).combustivelPorKm());
        long next = s.open(vehicle,time("2026-10-20T08:00"),b("10100"));
        assertEquals(0,s.repository().shift(next).manutencaoPorKm().compareTo(b("0.05")));
        assertEquals(b("50"),s.repository().shift(next).fixoDiario());
    }
    @Test void maintenanceCreatesOnePaymentAndResetsDueKm() throws Exception {
        long p = s.addPlan(vehicle,"Pneu",b("10000"),b("200"),b("0"));
        s.maintain(p,date("2026-10-19"),b("10000"),b("180"));
        assertEquals(1,s.repository().expenses().size()); assertEquals(1,s.repository().history().size());
        assertEquals(CategoriaDespesa.MANUTENCAO,s.repository().expenses().getFirst().categoria());
        assertEquals(b("10000"),s.repository().plan(p).ultimoKm());
        assertThrows(IllegalArgumentException.class,() -> s.maintain(p,date("2026-10-18"),b("10001"),b("5")));
        assertEquals(1,s.repository().expenses().size());
    }
    @Test void maintenanceCannotRunDuringShift() throws Exception {
        long p = s.addPlan(vehicle,"Óleo",b("1000"),b("30"),b("9000")); open();
        assertThrows(IllegalArgumentException.class,() -> s.maintain(p,date("2026-10-19"),b("10000"),b("30")));
        assertThrows(IllegalArgumentException.class,() -> s.addPlan(vehicle,"Pneu",b("1000"),b("30"),b("9000")));
        assertTrue(s.repository().history().isEmpty()); assertTrue(s.repository().expenses().isEmpty());
    }
    @Test void validatesPlans() {
        assertThrows(IllegalArgumentException.class,() -> s.addPlan(vehicle,"Pneu",b("0"),b("200"),b("9000")));
        assertThrows(IllegalArgumentException.class,() -> s.addPlan(vehicle,"Pneu",b("1000"),b("200"),b("10001")));
    }
    @Test void goalUpsertAndPositiveValidation() throws Exception {
        s.goal(vehicle,date("2026-10-19"),b("100")); s.goal(vehicle,date("2026-10-19"),b("150"));
        assertEquals(b("150"),s.repository().goal(vehicle,date("2026-10-19")).orElseThrow().valor());
        assertThrows(IllegalArgumentException.class,() -> s.goal(vehicle,date("2026-10-19"),b("0")));
    }
    @Test void bikeHasNoFuelAndMotorRequiresPositiveConsumption() throws Exception {
        long id = s.addVehicle("Bike",TipoVeiculo.BIKE,b("0"),b("0"),b("0"),b("2"));
        long j = s.open(id,time("2026-10-19T08:00"),b("0"));
        assertEquals(BigDecimal.ZERO,s.repository().shift(j).combustivelPorKm());
        assertThrows(IllegalArgumentException.class,() -> s.addVehicle("Carro",TipoVeiculo.CARRO,b("0"),b("0"),b("6"),b("0")));
        assertThrows(IllegalArgumentException.class,() -> s.addVehicle("Bike",TipoVeiculo.BIKE,b("0"),b("1"),b("0"),b("0")));
    }
    @Test void rejectsInvalidRideInputs() throws Exception {
        long id = open();
        assertThrows(IllegalArgumentException.class,() -> s.addRide(id," ",b("1"),b("0"),1));
        assertThrows(IllegalArgumentException.class,() -> s.addRide(id,"A",b("-1"),b("0"),1));
        assertThrows(IllegalArgumentException.class,() -> s.addRide(id,"A",b("1.001"),b("0"),1));
        assertThrows(IllegalArgumentException.class,() -> s.addRide(id,"A",b("1"),b("-1"),1));
        assertThrows(IllegalArgumentException.class,() -> s.addRide(id,"A",b("1"),b("0"),0));
        assertTrue(s.repository().rides().isEmpty());
    }
    @Test void rejectsSecondsAndHugeDecimals() {
        assertThrows(IllegalArgumentException.class,() -> s.open(vehicle,time("2026-10-19T08:00:01"),b("10000")));
        assertThrows(IllegalArgumentException.class,() -> s.addVehicle("Moto",TipoVeiculo.MOTO,b("1000000000001"),b("1"),b("1"),b("1")));
    }
    @Test void correctionsWorkOnlyWhileShiftIsOpen() throws Exception {
        long j = open(); long ride = s.addRide(j,"A",b("10"),b("200"),60);
        long expense = s.addExpense(vehicle,j,date("2026-10-19"),CategoriaDespesa.EXTRA,b("5"),"Erro");
        s.editRide(ride,"B",b("20"),b("60"),60); s.editExpense(expense,b("7.50"),"Pedágio");
        assertEquals("B",s.repository().ride(ride).plataforma());
        assertEquals(b("7.50"),s.repository().expense(expense).valor());
        s.close(j,time("2026-10-19T12:00"),b("10100"));
        assertThrows(IllegalArgumentException.class,() -> s.editRide(ride,"B",b("30"),b("60"),60));
        assertThrows(IllegalArgumentException.class,() -> s.editExpense(expense,b("8"),"Pedágio"));
    }
    @Test void rejectsDuplicateMaintenanceAndProtectedPaymentEdits() throws Exception {
        long p = s.addPlan(vehicle,"Pneu",b("10000"),b("200"),b("9000"));
        s.maintain(p,date("2026-10-19"),b("10000"),b("180"));
        assertThrows(IllegalArgumentException.class,() -> s.maintain(p,date("2026-10-19"),b("10000.0"),b("180")));
        assertThrows(IllegalArgumentException.class,() -> s.editExpense(s.repository().expenses().getFirst().id(),b("999"),"Alterado"));
        assertEquals(1,s.repository().history().size());
    }
    @Test void planConfigurationDoesNotRewritePastProvision() throws Exception {
        long p = s.addPlan(vehicle,"Pneu",b("10000"),b("200"),b("9000"));
        long j = open(); s.close(j,time("2026-10-19T12:00"),b("10100"));
        s.configurePlan(p,b("10000"),b("300"));
        assertEquals(0,s.repository().shift(j).manutencaoPorKm().compareTo(b("0.02")));
        long next = s.open(vehicle,time("2026-10-20T08:00"),b("10100"));
        assertEquals(0,s.repository().shift(next).manutencaoPorKm().compareTo(b("0.03")));
    }
}