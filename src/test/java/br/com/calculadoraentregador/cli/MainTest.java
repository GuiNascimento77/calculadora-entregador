package br.com.calculadoraentregador.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @TempDir Path dir;
    record Result(int code, String out, String err) {}
    private Result run(String... tokens) {
        List<String> args = new ArrayList<>(List.of("--banco",dir.resolve("cli.db").toString())); args.addAll(List.of(tokens));
        return raw(args.toArray(String[]::new));
    }
    private Result raw(String... args) {
        StringWriter out = new StringWriter(),err = new StringWriter();
        int code = Main.run(args,new PrintWriter(out),new PrintWriter(err));
        return new Result(code,out.toString(),err.toString());
    }
    private void ok(String... args) { Result r = run(args); assertEquals(0,r.code(),r.err()); }
    private void vehicle() { ok("veiculo","adicionar","--nome","Moto","--tipo","MOTO","--odometro","10000","--consumo","30","--combustivel","6","--fixo-diario","20"); }
    @Test void fullWorkflowWithReportsGoalsAndMaintenance() {
        vehicle();
        ok("manutencao","plano","--veiculo","1","--item","Óleo","--intervalo-km","1000","--custo","30","--ultimo-km","9100");
        ok("manutencao","plano","--veiculo","1","--item","Pneu","--intervalo-km","10000","--custo","200","--ultimo-km","9000");
        ok("meta","definir","--veiculo","1","--data","2026-10-19","--valor","150");
        ok("jornada","abrir","--veiculo","1","--inicio","2026-10-19T08:00","--odometro","10000");
        ok("corrida","adicionar","--jornada","1","--plataforma","iFood","--valor","120","--km","40","--minutos","80");
        ok("corrida","adicionar","--jornada","1","--plataforma","Rappi","--valor","80","--km","20","--minutos","40");
        ok("despesa","adicionar","--veiculo","1","--data","2026-10-19","--categoria","COMBUSTIVEL","--valor","60","--descricao","Tanque");
        ok("despesa","adicionar","--veiculo","1","--jornada","1","--data","2026-10-19","--categoria","EXTRA","--valor","5","--descricao","Pedágio");
        Result closed = run("jornada","fechar","--id","1","--fim","2026-10-19T12:00","--odometro","10100");
        assertEquals(0,closed.code(),closed.err()); assertTrue(closed.out().contains("Lucro líquido estimado: R$ 150,00"));
        assertTrue(closed.out().contains("Ganho líquido por hora total: R$ 37,50"));
        assertTrue(closed.out().contains("saldo de caixa: R$ 135,00")); assertTrue(closed.out().contains("ATINGIDA"));
        assertTrue(closed.out().contains("VENCIDA"));
        Result comparison = run("comparativo","--periodo","SEMANAL","--data","2026-10-19","--veiculo","1");
        assertEquals(0,comparison.code()); assertTrue(comparison.out().contains("IFOOD")); assertTrue(comparison.out().contains("RAPPI"));
        ok("relatorio","--periodo","MENSAL","--data","2026-10-19");
        ok("manutencao","registrar","--plano","1","--data","2026-10-19","--odometro","10100","--valor","30");
        assertTrue(run("manutencao","alertas","--veiculo","1").out().contains("EM DIA"));
        assertTrue(run("manutencao","historico","--plano","1").out().contains("despesa #3"));
        assertTrue(run("relatorio","--data","2026-10-19","--veiculo","1").out().contains("Lucro líquido estimado: R$ 150,00"));
        assertTrue(run("relatorio","--data","2026-10-19","--veiculo","1").out().contains("saldo de caixa: R$ 105,00"));
        ok("veiculo","listar"); ok("jornada","listar"); ok("corrida","listar","--jornada","1"); ok("despesa","listar");
    }
    @Test void helpAndVersionDoNotCreateDatabase() {
        assertEquals(0,raw("ajuda").code()); assertTrue(raw().out().contains("CALCULADORA"));
        assertTrue(raw("--version").out().contains("1.0.0")); assertFalse(Files.exists(dir.resolve("cli.db")));
    }
    @Test void rejectsUnknownDuplicateAndMissingOptions() {
        assertEquals(2,run("nao-existe").code());
        assertEquals(2,run("veiculo","listar","--errado","1").code());
        assertEquals(2,run("veiculo","listar","--veiculo").code());
        assertEquals(2,run("meta","status","--veiculo","1","--veiculo","2").code());
        assertEquals(2,run("jornada").code()); assertEquals(2,run("--banco","x.db","veiculo","listar").code());
    }
    @Test void decimalCommaAndBikeDefaultsWork() {
        ok("veiculo","adicionar","--nome","Bike","--tipo","BIKE","--odometro","12,5","--fixo-diario","2,50");
        String list = run("veiculo","listar").out(); assertTrue(list.contains("12.5 km")); assertTrue(list.contains("R$ 2,50/dia"));
    }
    @Test void invalidDatesNumbersEnumsAndIdsAreFriendlyErrors() {
        vehicle();
        assertEquals(2,run("jornada","abrir","--veiculo","1","--inicio","2026-02-30T08:00","--odometro","10000").code());
        assertEquals(2,run("relatorio","--periodo","ANUAL").code());
        assertEquals(2,run("jornada","listar","--veiculo","0").code());
        assertEquals(2,run("veiculo","configurar","--id","1","--combustivel","abc").code());
        assertEquals(2,run("meta","definir","--veiculo","1","--data","2026-10-19","--valor","1.234,56").code());
    }
    @Test void missingIdsAreNotSqlErrors() {
        Result r = run("corrida","listar","--jornada","999");
        assertEquals(2,r.code()); assertTrue(r.err().contains("Jornada não encontrada"));
    }
    @Test void emptyReportsAndPendingGoalsAreExplicit() {
        vehicle(); ok("meta","definir","--veiculo","1","--data","2026-10-19","--valor","150");
        assertTrue(run("meta","status","--veiculo","1","--data","2026-10-19").out().contains("pendente"));
        Result report = run("relatorio","--data","2026-10-19");
        assertTrue(report.out().contains("N/D")); assertTrue(report.out().contains("Sem jornadas fechadas"));
    }
    @Test void fileFailureReturnsOne() throws Exception {
        Path folder = dir.resolve("folder.db"); Files.createDirectory(folder);
        Result r = raw("--banco",folder.toString(),"veiculo","listar");
        assertEquals(1,r.code()); assertTrue(r.err().contains("Erro de banco/arquivo"));
    }
    @Test void editingCommandsAndHelpWithDatabaseOption() {
        assertEquals(0,run("ajuda").code()); assertFalse(Files.exists(dir.resolve("cli.db")));
        vehicle();
        ok("manutencao","plano","--veiculo","1","--item","Óleo","--intervalo-km","1000","--custo","30","--ultimo-km","9000");
        ok("manutencao","configurar","--id","1","--custo","35");
        ok("jornada","abrir","--veiculo","1","--inicio","2026-10-19T08:00","--odometro","10000");
        ok("corrida","adicionar","--jornada","1","--plataforma","A","--valor","10","--km","200","--minutos","60");
        ok("corrida","editar","--id","1","--km","60");
        ok("despesa","adicionar","--veiculo","1","--jornada","1","--data","2026-10-19","--categoria","EXTRA","--valor","5","--descricao","Erro");
        ok("despesa","editar","--id","1","--valor","7,50","--descricao","Pedágio");
        ok("jornada","fechar","--id","1","--fim","2026-10-19T12:00","--odometro","10100");
    }
}