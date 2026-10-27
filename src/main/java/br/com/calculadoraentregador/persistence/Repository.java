package br.com.calculadoraentregador.persistence;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static br.com.calculadoraentregador.domain.Models.*;

/** SQL parametrizado. Valores decimais são armazenados como TEXT sem perda binária. */
public final class Repository {
    private final Connection c;
    public Repository(Database db) { c = db.connection(); }
    @FunctionalInterface private interface Mapper<T> { T map(ResultSet r) throws SQLException; }

    private void bind(PreparedStatement st, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value == null) st.setNull(i + 1, Types.VARCHAR);
            else if (value instanceof BigDecimal b) st.setString(i + 1, b.toPlainString());
            else if (value instanceof Enum<?> e) st.setString(i + 1, e.name());
            else st.setObject(i + 1, value instanceof LocalDate || value instanceof LocalDateTime ? value.toString() : value);
        }
    }

    private <T> List<T> query(String sql, Mapper<T> mapper, Object... values) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(sql)) {
            bind(st, values);
            try (ResultSet r = st.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (r.next()) result.add(mapper.map(r));
                return List.copyOf(result);
            }
        }
    }

    private int update(String sql, Object... values) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(sql)) {
            bind(st, values); return st.executeUpdate();
        }
    }

    private long insert(String sql, Object... values) throws SQLException {
        update(sql, values);
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT last_insert_rowid()")) {
            return r.getLong(1);
        }
    }

    private BigDecimal decimal(ResultSet r, String column) throws SQLException {
        String s = r.getString(column); return s == null ? null : new BigDecimal(s);
    }
    private Veiculo vehicle(ResultSet r) throws SQLException {
        return new Veiculo(r.getLong("id"), r.getString("name"), TipoVeiculo.valueOf(r.getString("type")),
                decimal(r,"odometer"), decimal(r,"consumption"), decimal(r,"fuel_price"), decimal(r,"daily_fixed"));
    }
    private Jornada shift(ResultSet r) throws SQLException {
        String end = r.getString("ended_at");
        return new Jornada(r.getLong("id"), r.getLong("vehicle_id"), LocalDateTime.parse(r.getString("started_at")),
                end == null ? null : LocalDateTime.parse(end), decimal(r,"km_start"), decimal(r,"km_end"),
                decimal(r,"fuel_per_km"), decimal(r,"maintenance_per_km"), decimal(r,"daily_fixed"));
    }
    private Corrida ride(ResultSet r) throws SQLException {
        return new Corrida(r.getLong("id"), r.getLong("shift_id"), r.getString("platform"), decimal(r,"amount"),
                decimal(r,"km"), r.getInt("minutes"));
    }
    private Despesa expense(ResultSet r) throws SQLException {
        long shift = r.getLong("shift_id"); Long id = r.wasNull() ? null : shift;
        return new Despesa(r.getLong("id"), r.getLong("vehicle_id"), id, LocalDate.parse(r.getString("day")),
                CategoriaDespesa.valueOf(r.getString("category")), decimal(r,"amount"), r.getString("description"));
    }
    private PlanoManutencao plan(ResultSet r) throws SQLException {
        return new PlanoManutencao(r.getLong("id"), r.getLong("vehicle_id"), r.getString("item"),
                decimal(r,"interval_km"), decimal(r,"cost"), decimal(r,"last_km"));
    }

    public List<Veiculo> vehicles() throws SQLException { return query("SELECT * FROM vehicles ORDER BY id", this::vehicle); }
    public Veiculo vehicle(long id) throws SQLException {
        return query("SELECT * FROM vehicles WHERE id=?", this::vehicle, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Veículo não encontrado: " + id));
    }
    public long addVehicle(String name, TipoVeiculo type, BigDecimal odo, BigDecimal consumption,
                           BigDecimal fuel, BigDecimal fixed) throws SQLException {
        return insert("INSERT INTO vehicles(name,type,odometer,consumption,fuel_price,daily_fixed) VALUES(?,?,?,?,?,?)",
                name,type,odo,consumption,fuel,fixed);
    }
    public void configure(long id, BigDecimal consumption, BigDecimal fuel, BigDecimal fixed) throws SQLException {
        update("UPDATE vehicles SET consumption=?,fuel_price=?,daily_fixed=? WHERE id=?", consumption,fuel,fixed,id);
    }
    public void odometer(long id, BigDecimal km) throws SQLException {
        update("UPDATE vehicles SET odometer=? WHERE id=?", km,id);
    }
    public List<Jornada> shifts() throws SQLException { return query("SELECT * FROM shifts ORDER BY started_at,id", this::shift); }
    public Jornada shift(long id) throws SQLException {
        return query("SELECT * FROM shifts WHERE id=?", this::shift,id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Jornada não encontrada: " + id));
    }
    public long open(long vehicle, LocalDateTime start, BigDecimal km, BigDecimal fuel, BigDecimal maintenance,
                     BigDecimal fixed) throws SQLException {
        return insert("INSERT INTO shifts(vehicle_id,day,started_at,km_start,fuel_per_km,maintenance_per_km,daily_fixed) VALUES(?,?,?,?,?,?,?)",
                vehicle,start.toLocalDate(),start,km,fuel,maintenance,fixed);
    }
    public void close(long id, LocalDateTime end, BigDecimal km) throws SQLException {
        update("UPDATE shifts SET ended_at=?,km_end=? WHERE id=?", end,km,id);
    }
    public List<Corrida> rides() throws SQLException { return query("SELECT * FROM rides ORDER BY id", this::ride); }
    public long addRide(long shift, String platform, BigDecimal value, BigDecimal km, int minutes) throws SQLException {
        return insert("INSERT INTO rides(shift_id,platform,amount,km,minutes) VALUES(?,?,?,?,?)", shift,platform,value,km,minutes);
    }
    public Corrida ride(long id) throws SQLException {
        return query("SELECT * FROM rides WHERE id=?",this::ride,id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Corrida não encontrada: " + id));
    }
    public void editRide(long id, String platform, BigDecimal value, BigDecimal km, int minutes) throws SQLException {
        update("UPDATE rides SET platform=?,amount=?,km=?,minutes=? WHERE id=?",platform,value,km,minutes,id);
    }
    public List<Despesa> expenses() throws SQLException { return query("SELECT * FROM expenses ORDER BY day,id", this::expense); }
    public Despesa expense(long id) throws SQLException {
        return query("SELECT * FROM expenses WHERE id=?",this::expense,id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Despesa não encontrada: " + id));
    }
    public void editExpense(long id, BigDecimal value, String description) throws SQLException {
        update("UPDATE expenses SET amount=?,description=? WHERE id=?",value,description,id);
    }
    public long addExpense(long vehicle, Long shift, LocalDate date, CategoriaDespesa category,
                           BigDecimal value, String description) throws SQLException {
        return insert("INSERT INTO expenses(vehicle_id,shift_id,day,category,amount,description) VALUES(?,?,?,?,?,?)",
                vehicle,shift,date,category,value,description);
    }
    public List<PlanoManutencao> plans() throws SQLException { return query("SELECT * FROM maintenance_plans ORDER BY id", this::plan); }
    public PlanoManutencao plan(long id) throws SQLException {
        return query("SELECT * FROM maintenance_plans WHERE id=?",this::plan,id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Plano de manutenção não encontrado: " + id));
    }
    public long addPlan(long vehicle, String item, BigDecimal interval, BigDecimal cost, BigDecimal last) throws SQLException {
        return insert("INSERT INTO maintenance_plans(vehicle_id,item,interval_km,cost,last_km) VALUES(?,?,?,?,?)",vehicle,item,interval,cost,last);
    }
    public void configurePlan(long id, BigDecimal interval, BigDecimal cost) throws SQLException {
        update("UPDATE maintenance_plans SET interval_km=?,cost=? WHERE id=?",interval,cost,id);
    }
    public void lastMaintenance(long plan, BigDecimal km) throws SQLException {
        update("UPDATE maintenance_plans SET last_km=? WHERE id=?",km,plan);
    }
    public long addHistory(long plan, LocalDate date, BigDecimal km, BigDecimal value, long expense) throws SQLException {
        return insert("INSERT INTO maintenance_history(plan_id,day,odometer,amount,expense_id) VALUES(?,?,?,?,?)",plan,date,km,value,expense);
    }
    public List<RegistroManutencao> history() throws SQLException {
        return query("SELECT * FROM maintenance_history ORDER BY day,id", r -> new RegistroManutencao(
                r.getLong("id"),r.getLong("plan_id"),LocalDate.parse(r.getString("day")),decimal(r,"odometer"),
                decimal(r,"amount"),r.getLong("expense_id")));
    }
    public void goal(long vehicle, LocalDate day, BigDecimal amount) throws SQLException {
        update("INSERT INTO goals(vehicle_id,day,amount) VALUES(?,?,?) ON CONFLICT(vehicle_id,day) DO UPDATE SET amount=excluded.amount",
                vehicle,day,amount);
    }
    public Optional<Meta> goal(long vehicle, LocalDate day) throws SQLException {
        return query("SELECT * FROM goals WHERE vehicle_id=? AND day=?", r -> new Meta(r.getLong("vehicle_id"),
                LocalDate.parse(r.getString("day")),decimal(r,"amount")),vehicle,day).stream().findFirst();
    }
}