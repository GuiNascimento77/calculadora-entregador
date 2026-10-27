package br.com.calculadoraentregador.application;

import br.com.calculadoraentregador.domain.Metrics;
import br.com.calculadoraentregador.persistence.Database;
import br.com.calculadoraentregador.persistence.Repository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import static br.com.calculadoraentregador.domain.Checks.*;
import static br.com.calculadoraentregador.domain.Models.*;

/** Casos de uso e invariantes. Escritas compostas são atômicas. */
public final class DeliveryService {
    private final Database db;
    private final Repository repo;
    public DeliveryService(Database db) { this.db = db; repo = new Repository(db); }
    public Repository repository() { return repo; }

    private void costs(TipoVeiculo type, BigDecimal consumption, BigDecimal price, BigDecimal fixed) {
        nonNegative(consumption,"Consumo"); money(price,"Preço do combustível"); money(fixed,"Custo fixo diário");
        if (type == TipoVeiculo.BIKE)
            require(consumption.signum() == 0 && price.signum() == 0, "Bike deve ter consumo e combustível zerados.");
        else positive(consumption,"Consumo em km/L");
    }

    public long addVehicle(String name, TipoVeiculo type, BigDecimal km, BigDecimal consumption,
                           BigDecimal price, BigDecimal fixed) throws SQLException {
        String clean = text(name,"Nome"); Objects.requireNonNull(type,"Tipo é obrigatório.");
        nonNegative(km,"Odômetro"); costs(type,consumption,price,fixed);
        return db.transaction(() -> repo.addVehicle(clean,type,km,consumption,price,fixed));
    }

    public void configure(long id, BigDecimal consumption, BigDecimal price, BigDecimal fixed) throws SQLException {
        db.transaction(() -> {
            Veiculo v = repo.vehicle(id); costs(v.tipo(),consumption,price,fixed);
            noOpenShift(id);
            repo.configure(id,consumption,price,fixed); return null;
        });
    }

    private void noOpenShift(long vehicle) throws SQLException {
        require(repo.shifts().stream().noneMatch(j -> j.veiculoId() == vehicle && j.aberta()),
                "Existe uma jornada aberta para este veículo. Feche-a primeiro.");
    }
    private Jornada openShift(long id) throws SQLException {
        Jornada j = repo.shift(id); require(j.aberta(),"Jornada já fechada. Seus registros são imutáveis."); return j;
    }
    private void wholeMinute(LocalDateTime time) {
        Objects.requireNonNull(time,"Horário é obrigatório.");
        require(time.getSecond() == 0 && time.getNano() == 0,"Informe horários com precisão de minutos (AAAA-MM-DDTHH:mm).");
    }

    public long open(long vehicle, LocalDateTime start, BigDecimal km) throws SQLException {
        wholeMinute(start); nonNegative(km,"Odômetro inicial");
        return db.transaction(() -> {
            Veiculo v = repo.vehicle(vehicle); noOpenShift(vehicle);
            require(km.compareTo(v.odometro()) >= 0,"Odômetro inicial não pode ser menor que o último registrado.");
            for (Jornada j : repo.shifts()) if (j.veiculoId() == vehicle)
                require(start.toLocalDate().isAfter(j.data()),"Jornadas devem ser registradas em ordem, uma por veículo/dia.");
            for (RegistroManutencao h : repo.history()) if (repo.plan(h.planoId()).veiculoId() == vehicle)
                require(!start.toLocalDate().isBefore(h.data()),"Jornada não pode anteceder manutenção já registrada.");
            BigDecimal fuel = v.tipo() == TipoVeiculo.BIKE ? BigDecimal.ZERO : v.precoCombustivel().divide(v.consumo(),Metrics.MC);
            BigDecimal provision = BigDecimal.ZERO;
            for (PlanoManutencao p : repo.plans()) if (p.veiculoId() == vehicle)
                provision = provision.add(p.custo().divide(p.intervaloKm(),Metrics.MC));
            long id = repo.open(vehicle,start,km,fuel,provision,v.fixoDiario());
            repo.odometer(vehicle,km); return id;
        });
    }

    public void close(long id, LocalDateTime end, BigDecimal km) throws SQLException {
        wholeMinute(end); nonNegative(km,"Odômetro final");
        db.transaction(() -> {
            Jornada j = openShift(id);
            require(end.isAfter(j.inicio()),"Fim deve ser posterior ao início.");
            require(end.toLocalDate().equals(j.data()),"Nesta versão, a jornada deve começar e terminar no mesmo dia.");
            require(km.compareTo(j.kmInicio()) >= 0,"Odômetro final não pode ser menor que o inicial.");
            BigDecimal rideKm = BigDecimal.ZERO; long rideMinutes = 0;
            for (Corrida r : repo.rides()) if (r.jornadaId() == id) {
                rideKm = rideKm.add(r.km()); rideMinutes += r.minutos();
            }
            require(rideKm.compareTo(km.subtract(j.kmInicio())) <= 0,"Km das corridas excedem os km totais da jornada.");
            require(rideMinutes <= Duration.between(j.inicio(),end).toMinutes(),"Tempo das corridas excede o tempo total da jornada.");
            repo.close(id,end,km); repo.odometer(j.veiculoId(),km); return null;
        });
    }

    public long addRide(long shift, String platform, BigDecimal amount, BigDecimal km, int minutes) throws SQLException {
        String clean = text(platform,"Plataforma").toUpperCase(Locale.ROOT);
        require(!clean.equals("(SEM CORRIDAS)"),"Nome de plataforma reservado pelo relatório.");
        money(amount,"Valor da corrida"); nonNegative(km,"Km da corrida");
        require(minutes > 0 && minutes <= 1440,"Tempo da corrida deve estar entre 1 e 1440 minutos.");
        return db.transaction(() -> { openShift(shift); return repo.addRide(shift,clean,amount,km,minutes); });
    }

    public void editRide(long id, String platform, BigDecimal amount, BigDecimal km, int minutes) throws SQLException {
        String clean = text(platform,"Plataforma").toUpperCase(Locale.ROOT);
        require(!clean.equals("(SEM CORRIDAS)"),"Nome de plataforma reservado pelo relatório.");
        money(amount,"Valor da corrida"); nonNegative(km,"Km da corrida");
        require(minutes > 0 && minutes <= 1440,"Tempo da corrida deve estar entre 1 e 1440 minutos.");
        db.transaction(() -> {
            Corrida c = repo.ride(id); openShift(c.jornadaId());
            repo.editRide(id,clean,amount,km,minutes); return null;
        });
    }

    public long addExpense(long vehicle, Long shift, LocalDate date, CategoriaDespesa category,
                           BigDecimal amount, String description) throws SQLException {
        Objects.requireNonNull(date,"Data é obrigatória."); Objects.requireNonNull(category,"Categoria é obrigatória.");
        money(amount,"Despesa"); positive(amount,"Despesa"); String clean = text(description,"Descrição");
        return db.transaction(() -> {
            repo.vehicle(vehicle);
            require(category != CategoriaDespesa.EXTRA || shift != null,"Despesa EXTRA exige --jornada para atribuir o custo.");
            if (shift != null) {
                Jornada j = repo.shift(shift);
                require(j.veiculoId() == vehicle,"Jornada pertence a outro veículo.");
                require(date.equals(j.data()),"Data da despesa vinculada deve ser a data da jornada.");
                if (category == CategoriaDespesa.EXTRA) openShift(shift);
            }
            return repo.addExpense(vehicle,shift,date,category,amount,clean);
        });
    }

    public long addPlan(long vehicle, String item, BigDecimal interval, BigDecimal cost, BigDecimal last) throws SQLException {
        String clean = text(item,"Item").toUpperCase(Locale.ROOT); positive(interval,"Intervalo em km");
        money(cost,"Custo previsto"); nonNegative(last,"Km da última manutenção");
        return db.transaction(() -> {
            Veiculo v = repo.vehicle(vehicle); noOpenShift(vehicle);
            require(last.compareTo(v.odometro()) <= 0,"Última manutenção não pode ultrapassar o odômetro atual.");
            require(repo.plans().stream().noneMatch(p -> p.veiculoId() == vehicle && p.item().equals(clean)),"Já existe um plano para este item.");
            return repo.addPlan(vehicle,clean,interval,cost,last);
        });
    }

    public void editExpense(long id, BigDecimal amount, String description) throws SQLException {
        money(amount,"Despesa"); positive(amount,"Despesa"); String clean = text(description,"Descrição");
        db.transaction(() -> {
            Despesa d = repo.expense(id);
            if (d.categoria() == CategoriaDespesa.EXTRA) openShift(d.jornadaId());
            require(repo.history().stream().noneMatch(h -> h.despesaId() == id),
                    "Pagamento gerado por manutenção não pode ser editado isoladamente.");
            repo.editExpense(id,amount,clean); return null;
        });
    }

    /** Mantém o marco anterior. Nova provisão só afeta jornadas futuras. */
    public void configurePlan(long id, BigDecimal interval, BigDecimal cost) throws SQLException {
        positive(interval,"Intervalo em km"); money(cost,"Custo previsto");
        db.transaction(() -> {
            PlanoManutencao p = repo.plan(id); noOpenShift(p.veiculoId());
            repo.configurePlan(id,interval,cost); return null;
        });
    }

    /** Atualiza o marco e cria um pagamento. Repetir o mesmo marco/data é rejeitado. */
    public long maintain(long plan, LocalDate date, BigDecimal km, BigDecimal amount) throws SQLException {
        Objects.requireNonNull(date,"Data é obrigatória."); nonNegative(km,"Odômetro"); money(amount,"Valor pago");
        return db.transaction(() -> {
            PlanoManutencao p = repo.plan(plan); Veiculo v = repo.vehicle(p.veiculoId()); noOpenShift(v.id());
            require(km.compareTo(v.odometro()) >= 0,"Manutenção não pode retroceder o odômetro atual.");
            require(km.compareTo(p.ultimoKm()) >= 0,"Manutenção não pode retroceder o último marco.");
            for (Jornada j : repo.shifts()) if (j.veiculoId() == v.id())
                require(!date.isBefore(j.data()),"Manutenção não pode anteceder a última jornada registrada.");
            for (RegistroManutencao h : repo.history()) {
                if (repo.plan(h.planoId()).veiculoId() == v.id())
                    require(!date.isBefore(h.data()),"Manutenções do veículo devem ser registradas em ordem de data.");
                require(h.planoId() != plan || !h.data().equals(date) || h.odometro().compareTo(km) != 0,
                        "Manutenção já registrada para este plano, data e odômetro.");
            }
            long expense = repo.addExpense(v.id(),null,date,CategoriaDespesa.MANUTENCAO,amount,"Manutenção: " + p.item());
            long id = repo.addHistory(plan,date,km,amount,expense);
            repo.lastMaintenance(plan,km); repo.odometer(v.id(),km); return id;
        });
    }

    public void goal(long vehicle, LocalDate date, BigDecimal amount) throws SQLException {
        Objects.requireNonNull(date,"Data é obrigatória."); money(amount,"Meta"); positive(amount,"Meta");
        db.transaction(() -> { repo.vehicle(vehicle); repo.goal(vehicle,date,amount); return null; });
    }
}