package br.com.calculadoraentregador.persistence;

import br.com.calculadoraentregador.application.DeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import static br.com.calculadoraentregador.domain.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    @TempDir Path dir;
    @Test void migrationsAndPersistenceSurviveReopening() throws Exception {
        Path path = dir.resolve("nested/data.db");
        try (Database db = new Database(path)) {
            new DeliveryService(db).addVehicle("Persistida",TipoVeiculo.BIKE,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
            try (var st = db.connection().createStatement(); var rs = st.executeQuery("PRAGMA user_version")) {
                assertEquals(2,rs.getInt(1));
            }
        }
        try (Database db = new Database(path)) {
            assertEquals("Persistida",new Repository(db).vehicle(1).nome());
            try (var st = db.connection().createStatement(); var rs = st.executeQuery("PRAGMA foreign_keys")) {
                assertEquals(1,rs.getInt(1));
            }
        }
    }
    @Test void failedTransactionRollsBackAllWrites() throws Exception {
        try (Database db = new Database(dir.resolve("tx.db"))) {
            Repository r = new Repository(db);
            assertThrows(SQLException.class,() -> db.transaction(() -> {
                r.addVehicle("A",TipoVeiculo.BIKE,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
                r.addRide(999,"A",BigDecimal.ONE,BigDecimal.ONE,1); return null;
            }));
            assertTrue(r.vehicles().isEmpty()); assertTrue(db.connection().getAutoCommit());
        }
    }
    @Test void newerSchemaIsRejected() throws Exception {
        Path path = dir.resolve("future.db");
        try (Database db = new Database(path); var st = db.connection().createStatement()) { st.execute("PRAGMA user_version=99"); }
        assertThrows(SQLException.class,() -> new Database(path));
    }
    @Test void upgradesExistingVersionOneSchema() throws Exception {
        Path path = dir.resolve("old.db");
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + path); var st = c.createStatement()) {
            String sql;
            try (var in = Database.class.getResourceAsStream("/db/migration/V1.sql")) {
                sql = new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            }
            for (String part : sql.split(";")) if (!part.isBlank()) st.execute(part);
            st.execute("INSERT INTO vehicles(name,type,odometer,consumption,fuel_price,daily_fixed) VALUES('Antes','BIKE','0','0','0','0')");
            st.execute("PRAGMA user_version=1");
        }
        try (Database db = new Database(path)) {
            assertEquals("Antes",new Repository(db).vehicle(1).nome());
            try (var st = db.connection().createStatement(); var rs = st.executeQuery("PRAGMA user_version")) { assertEquals(2,rs.getInt(1)); }
        }
    }
}