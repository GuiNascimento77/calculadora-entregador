package br.com.calculadoraentregador.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/** Uma conexão por execução da CLI. Cada comando de escrita usa uma transação. */
public final class Database implements AutoCloseable {
    private final Connection connection;

    public Database(Path path) throws SQLException, IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
        try {
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = 5000");
            }
            migrate();
        } catch (SQLException | IOException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    public Connection connection() { return connection; }

    private void migrate() throws SQLException, IOException {
        int current;
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            current = rs.getInt(1);
        }
        if (current > 2) throw new SQLException("Banco criado por uma versão mais nova do aplicativo.");
        for (int version = current + 1; version <= 2; version++) {
            String resource = "/db/migration/V" + version + ".sql";
            String sql;
            try (var in = Database.class.getResourceAsStream(resource)) {
                if (in == null) throw new IOException("Migração ausente: " + resource);
                sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            final int applied = version;
            transaction(() -> {
                try (Statement st = connection.createStatement()) {
                    for (String part : sql.split(";")) if (!part.isBlank()) st.execute(part);
                    st.execute("PRAGMA user_version = " + applied);
                }
                return null;
            });
        }
    }

    @FunctionalInterface
    public interface Work<T> { T run() throws SQLException; }

    public <T> T transaction(Work<T> work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            T value = work.run();
            connection.commit();
            return value;
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override public void close() throws SQLException { connection.close(); }
}