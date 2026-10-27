package br.com.calculadoraentregador.cli;

import br.com.calculadoraentregador.application.DeliveryService;
import br.com.calculadoraentregador.application.ReportService;
import br.com.calculadoraentregador.domain.Checks;
import br.com.calculadoraentregador.domain.Metrics;
import br.com.calculadoraentregador.persistence.Database;
import br.com.calculadoraentregador.persistence.Repository;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import static br.com.calculadoraentregador.domain.Models.*;

/** CLI por comandos. Use ajuda para ver sintaxe, categorias e regras. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out,StandardCharsets.UTF_8),true);
        PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err,StandardCharsets.UTF_8),true);
        System.exit(run(args,out,err));
    }

    /** Retorno 0: sucesso, 2: entrada inválida, 1: banco/arquivo indisponível. */
    public static int run(String[] args, PrintWriter out, PrintWriter err) {
        try {
            List<String> tokens = new ArrayList<>(Arrays.asList(args));
            if (tokens.isEmpty() || tokens.equals(List.of("ajuda")) || tokens.equals(List.of("--help"))) {
                out.print(HELP); return 0;
            }
            if (tokens.equals(List.of("--version"))) { out.println("Calculadora do Entregador 1.0.0"); return 0; }
            Path path = Path.of(System.getProperty("user.home"),".calculadora-entregador","dados.db");
            boolean specified = false;
            for (int i = 0; i < tokens.size(); i++) if (tokens.get(i).equals("--banco")) {
                Checks.require(!specified,"Opção repetida: --banco");
                Checks.require(i + 1 < tokens.size() && !tokens.get(i + 1).startsWith("--"),"Informe o caminho de --banco.");
                path = Path.of(tokens.remove(i + 1)); tokens.remove(i--); specified = true;
            }
            if (tokens.equals(List.of("ajuda")) || tokens.equals(List.of("--help"))) { out.print(HELP); return 0; }
            Checks.require(!tokens.isEmpty(),"Informe um comando. Use ajuda.");
            String command = tokens.removeFirst();
            Set<String> resources = Set.of("veiculo","jornada","corrida","despesa","manutencao","meta");
            String action = "";
            if (resources.contains(command)) {
                Checks.require(!tokens.isEmpty() && !tokens.getFirst().startsWith("--"),"Informe a ação de " + command + ". Use ajuda.");
                action = tokens.removeFirst();
            }
            Checks.require(resources.contains(command) || Set.of("relatorio","comparativo").contains(command),"Comando desconhecido: " + command);
            Options options = new Options(tokens);
            try (Database db = new Database(path)) {
                DeliveryService service = new DeliveryService(db);
                execute(command,action,options,service,out);
            }
            return 0;
        } catch (IllegalArgumentException e) {
            err.println("Erro: " + e.getMessage()); err.println("Use: java -jar calculadora-entregador.jar ajuda"); return 2;
        } catch (SQLException | IOException e) {
            err.println("Erro de banco/arquivo: " + e.getMessage()); return 1;
        } finally { out.flush(); err.flush(); }
    }

    private static void execute(String command, String action, Options o, DeliveryService s, PrintWriter out) throws SQLException {
        Repository r = s.repository();
        switch (command + (action.isEmpty() ? "" : " " + action)) {
            case "veiculo adicionar" -> {
                o.only("nome","tipo","odometro","consumo","combustivel","fixo-diario");
                TipoVeiculo type = o.enumeration("tipo",TipoVeiculo.class);
                BigDecimal consumption = type == TipoVeiculo.BIKE ? o.decimal("consumo","0") : o.decimal("consumo");
                BigDecimal fuel = type == TipoVeiculo.BIKE ? o.decimal("combustivel","0") : o.decimal("combustivel");
                long id = s.addVehicle(o.text("nome"),type,o.decimal("odometro"),consumption,fuel,o.decimal("fixo-diario","0"));
                out.println("Veículo cadastrado. ID: " + id);
            }
            case "veiculo configurar" -> {
                o.only("id","consumo","combustivel","fixo-diario"); long id = o.id("id");
                Veiculo v = r.vehicle(id);
                s.configure(id,o.decimal("consumo",v.consumo().toPlainString()),o.decimal("combustivel",v.precoCombustivel().toPlainString()),
                        o.decimal("fixo-diario",v.fixoDiario().toPlainString()));
                out.println("Configuração atualizada. Só afeta jornadas futuras.");
            }
            case "veiculo listar" -> {
                o.only();
                for (Veiculo v : r.vehicles()) out.printf("#%d | %s | %s | %s km | %s km/L | combustível %s/L | fixo %s/dia%n",
                        v.id(),v.nome(),v.tipo(),number(v.odometro()),number(v.consumo()),money(v.precoCombustivel()),money(v.fixoDiario()));
                if (r.vehicles().isEmpty()) out.println("Nenhum veículo cadastrado.");
            }
            case "jornada abrir" -> {
                o.only("veiculo","inicio","odometro"); long vehicle = o.id("veiculo");
                long id = s.open(vehicle,o.time("inicio"),o.decimal("odometro"));
                out.println("Jornada aberta. ID: " + id); alerts(r,vehicle,new BigDecimal("100"),out);
            }
            case "jornada fechar" -> {
                o.only("id","fim","odometro"); long id = o.id("id");
                s.close(id,o.time("fim"),o.decimal("odometro"));
                Jornada j = r.shift(id); out.println("Jornada fechada.");
                printReport(new ReportService(r).report(new ReportService.Intervalo(j.data(),j.data()),j.veiculoId()),out);
                printGoal(r,j.veiculoId(),j.data(),out); alerts(r,j.veiculoId(),new BigDecimal("100"),out);
            }
            case "jornada listar" -> {
                o.only("veiculo"); Long vehicle = o.optionalId("veiculo"); if (vehicle != null) r.vehicle(vehicle);
                List<Jornada> list = r.shifts().stream().filter(j -> vehicle == null || j.veiculoId() == vehicle).toList();
                for (Jornada j : list) out.printf("#%d | veículo #%d | %s -> %s | %s -> %s km | %s%n",j.id(),j.veiculoId(),
                        j.inicio(),j.aberta() ? "--" : j.fim(),number(j.kmInicio()),j.aberta() ? "--" : number(j.kmFim()),j.aberta() ? "ABERTA" : "FECHADA");
                if (list.isEmpty()) out.println("Nenhuma jornada registrada.");
            }
            case "corrida adicionar" -> {
                o.only("jornada","plataforma","valor","km","minutos");
                out.println("Corrida registrada. ID: " + s.addRide(o.id("jornada"),o.text("plataforma"),o.decimal("valor"),o.decimal("km"),o.integer("minutos")));
            }
            case "corrida listar" -> {
                o.only("jornada"); long id = o.id("jornada"); r.shift(id);
                List<Corrida> list = r.rides().stream().filter(c -> c.jornadaId() == id).toList();
                for (Corrida c : list) out.printf("#%d | %s | %s | %s km | %d min%n",c.id(),c.plataforma(),money(c.valor()),number(c.km()),c.minutos());
                if (list.isEmpty()) out.println("Nenhuma corrida nesta jornada.");
            }
            case "corrida editar" -> {
                o.only("id","plataforma","valor","km","minutos"); long id = o.id("id"); Corrida c = r.ride(id);
                s.editRide(id,o.text("plataforma",c.plataforma()),o.decimal("valor",c.valor().toPlainString()),
                        o.decimal("km",c.km().toPlainString()),o.has("minutos") ? o.integer("minutos") : c.minutos());
                out.println("Corrida corrigida na jornada aberta.");
            }
            case "despesa adicionar" -> {
                o.only("veiculo","jornada","data","categoria","valor","descricao");
                out.println("Despesa registrada. ID: " + s.addExpense(o.id("veiculo"),o.optionalId("jornada"),o.date("data"),
                        o.enumeration("categoria",CategoriaDespesa.class),o.decimal("valor"),o.text("descricao")));
            }
            case "despesa listar" -> {
                o.only("veiculo"); Long vehicle = o.optionalId("veiculo"); if (vehicle != null) r.vehicle(vehicle);
                List<Despesa> list = r.expenses().stream().filter(d -> vehicle == null || d.veiculoId() == vehicle).toList();
                for (Despesa d : list) out.printf("#%d | veículo #%d | %s | %s | %s | jornada %s | %s%n",d.id(),d.veiculoId(),d.data(),
                        d.categoria(),money(d.valor()),d.jornadaId() == null ? "--" : "#" + d.jornadaId(),d.descricao());
                if (list.isEmpty()) out.println("Nenhuma despesa registrada.");
            }
            case "despesa editar" -> {
                o.only("id","valor","descricao"); long id = o.id("id"); Despesa d = r.expense(id);
                s.editExpense(id,o.decimal("valor",d.valor().toPlainString()),o.text("descricao",d.descricao()));
                out.println("Despesa corrigida.");
            }
            case "manutencao plano" -> {
                o.only("veiculo","item","intervalo-km","custo","ultimo-km");
                out.println("Plano cadastrado. ID: " + s.addPlan(o.id("veiculo"),o.text("item"),o.decimal("intervalo-km"),o.decimal("custo"),o.decimal("ultimo-km")));
            }
            case "manutencao registrar" -> {
                o.only("plano","data","odometro","valor");
                out.println("Manutenção registrada. ID: " + s.maintain(o.id("plano"),o.date("data"),o.decimal("odometro"),o.decimal("valor")));
                out.println("Pagamento lançado no caixa automaticamente. Não cadastre a mesma despesa novamente.");
            }
            case "manutencao configurar" -> {
                o.only("id","intervalo-km","custo"); long id = o.id("id"); PlanoManutencao p = r.plan(id);
                s.configurePlan(id,o.decimal("intervalo-km",p.intervaloKm().toPlainString()),o.decimal("custo",p.custo().toPlainString()));
                out.println("Plano atualizado. Provisão nova só afeta jornadas futuras; alertas usam o intervalo atualizado.");
            }
            case "manutencao alertas" -> {
                o.only("veiculo","antecedencia-km"); Long vehicle = o.optionalId("veiculo");
                BigDecimal before = o.decimal("antecedencia-km","100"); Checks.nonNegative(before,"Antecedência");
                if (vehicle != null) r.vehicle(vehicle); alerts(r,vehicle,before,out);
            }
            case "manutencao historico" -> {
                o.only("plano"); Long plan = o.optionalId("plano"); if (plan != null) r.plan(plan);
                List<RegistroManutencao> list = r.history().stream().filter(h -> plan == null || h.planoId() == plan).toList();
                for (RegistroManutencao h : list) out.printf("#%d | plano #%d | %s | %s km | %s | despesa #%d%n",h.id(),h.planoId(),h.data(),number(h.odometro()),money(h.valor()),h.despesaId());
                if (list.isEmpty()) out.println("Nenhuma manutenção realizada.");
            }
            case "meta definir" -> {
                o.only("veiculo","data","valor"); s.goal(o.id("veiculo"),o.date("data"),o.decimal("valor"));
                out.println("Meta de lucro estimado definida (ou substituída) para o dia.");
            }
            case "meta status" -> { o.only("veiculo","data"); printGoal(r,o.id("veiculo"),o.dateOrToday("data"),out); }
            case "relatorio", "comparativo" -> {
                o.only("periodo","data","veiculo");
                ReportService.Periodo p = o.has("periodo") ? o.enumeration("periodo",ReportService.Periodo.class) : ReportService.Periodo.DIARIO;
                var report = new ReportService(r).report(ReportService.intervalo(p,o.dateOrToday("data")),o.optionalId("veiculo"));
                if (command.equals("relatorio")) printReport(report,out); else printPlatforms(report,out);
            }
            default -> throw new IllegalArgumentException("Ação desconhecida: " + command + " " + action);
        }
    }

    private static String money(BigDecimal value) {
        if (value == null) return "N/D";
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
        return f.format(Metrics.cents(value)).replace(' ',' ');
    }
    private static String number(BigDecimal value) {
        return value == null ? "N/D" : value.stripTrailingZeros().toPlainString();
    }

    private static void printReport(ReportService.Relatorio report, PrintWriter out) {
        var r = report.desempenho();
        out.printf("RELATÓRIO %s a %s | fechadas: %d | abertas: %d%n",report.intervalo().de(),report.intervalo().ate(),report.fechadas(),report.abertas());
        out.println("DESEMPENHO (somente jornadas fechadas)");
        out.println("Receita bruta: " + money(r.receita()));
        out.printf("Custos estimados: combustível %s | manutenção provisionada %s | fixos %s | extras %s%n",
                money(r.combustivel()),money(r.manutencao()),money(r.fixos()),money(r.extras()));
        out.println("Custo total estimado: " + money(r.custos()));
        out.println("Lucro líquido estimado: " + money(r.lucro()));
        out.printf("Km totais de trabalho: %s | tempo total (inclui espera): %d min%n",number(r.km()),r.minutos());
        out.println("Custo por km total: " + money(r.custoPorKm()));
        out.println("Ganho líquido por hora total: " + money(r.ganhoPorHora()));
        out.println("CAIXA (corridas registradas + pagamentos do período, inclui jornadas abertas)");
        out.printf("Entradas: %s | pagamentos: %s | saldo de caixa: %s%n",money(report.entradasCaixa()),money(report.pagamentos()),money(report.saldoCaixa()));
        if (report.abertas() > 0) out.println("Aviso: feche as jornadas abertas para completar o desempenho.");
        if (report.fechadas() == 0) out.println("Sem jornadas fechadas no período; os zeros não representam um dia concluído.");
        if (r.km().signum() == 0) out.println("N/D: custo por km indefinido quando não há km de trabalho.");
    }

    private static void printPlatforms(ReportService.Relatorio report, PrintWriter out) {
        out.printf("COMPARATIVO %s a %s (jornadas fechadas)%n",report.intervalo().de(),report.intervalo().ate());
        for (var p : report.plataformas()) out.printf("%s | %d corridas | bruto %s | %s km de entrega | %d min ativos | bruto/km %s | bruto/h ativa %s | custo rateado %s | lucro estimado %s%n",
                p.nome(),p.corridas(),money(p.receita()),number(p.km()),p.minutos(),money(p.brutoPorKmEntrega()),money(p.brutoPorHoraAtiva()),money(p.custoAlocado()),money(p.lucroEstimado()));
        if (report.plataformas().isEmpty()) out.println("Sem dados de jornadas fechadas.");
        out.println("Rateio estimado por km das corridas em cada jornada; se todos forem zero, usa minutos ativos.");
        out.println("Custos incluem espera e deslocamento sem pedido. Ganhos brutos/h ativa NÃO são ganhos/h total.");
        out.println("(SEM CORRIDAS) preserva os custos de jornadas sem pedidos. Rateio não mede lucro observado por plataforma.");
        if (report.abertas() > 0) out.println("Aviso: jornadas abertas foram excluídas do comparativo.");
    }

    private static void printGoal(Repository repo, long vehicle, LocalDate date, PrintWriter out) throws SQLException {
        repo.vehicle(vehicle); Optional<Meta> goal = repo.goal(vehicle,date);
        if (goal.isEmpty()) { out.println("Nenhuma meta definida para " + date + "."); return; }
        var report = new ReportService(repo).report(new ReportService.Intervalo(date,date),vehicle);
        if (report.fechadas() == 0) { out.println("Meta: " + money(goal.get().valor()) + ". Progresso pendente: jornada ainda não fechada."); return; }
        BigDecimal profit = report.desempenho().lucro(); BigDecimal target = goal.get().valor();
        BigDecimal remaining = target.subtract(profit).max(BigDecimal.ZERO);
        out.printf("Meta diária %s | lucro estimado %s | progresso %s%% | falta %s | %s%n",money(target),money(profit),
                Metrics.cents(profit.multiply(new BigDecimal("100")).divide(target,Metrics.MC)).toPlainString(),money(remaining),profit.compareTo(target) >= 0 ? "ATINGIDA" : "NÃO ATINGIDA");
    }

    private static void alerts(Repository repo, Long vehicle, BigDecimal before, PrintWriter out) throws SQLException {
        List<PlanoManutencao> list = repo.plans().stream().filter(p -> vehicle == null || p.veiculoId() == vehicle).toList();
        for (PlanoManutencao p : list) {
            BigDecimal odo = repo.vehicle(p.veiculoId()).odometro(); BigDecimal due = p.ultimoKm().add(p.intervaloKm());
            BigDecimal left = due.subtract(odo);
            String state = left.signum() <= 0 ? "VENCIDA" : left.compareTo(before) <= 0 ? "PRÓXIMA" : "EM DIA";
            out.printf("Manutenção #%d | veículo #%d | %s | %s | atual %s km | próxima %s km | restante %s km | provisão %s/km%n",
                    p.id(),p.veiculoId(),p.item(),state,number(odo),number(due),number(left),
                    p.custo().divide(p.intervaloKm(),Metrics.MC).setScale(4,java.math.RoundingMode.HALF_UP).toPlainString());
        }
        if (list.isEmpty()) out.println("Sem planos de manutenção. Cadastre-os para incluir provisões no lucro estimado.");
    }

    private static final String HELP = """
            CALCULADORA DO ENTREGADOR 1.0.0 — Java 21 + SQLite
            Uso: java -jar calculadora-entregador.jar [--banco caminho.db] COMANDO AÇÃO [opções]
            Banco padrão: ~/.calculadora-entregador/dados.db (criado automaticamente)
            Comandos não são um menu: execute um comando por vez. IDs aparecem após o cadastro.

            veiculo adicionar --nome "Minha moto" --tipo MOTO --odometro 10000 --consumo 35 --combustivel 6 --fixo-diario 20
              Tipos: MOTO, CARRO, BIKE. Consumo em km/L; combustível em R$/L.
              BIKE dispensa consumo/combustível (ambos zero). Fixo diário opcional, padrão 0.
            veiculo listar
            veiculo configurar --id ID [--consumo KM/L] [--combustivel R$/L] [--fixo-diario REAIS]

            jornada abrir --veiculo ID --inicio AAAA-MM-DDTHH:mm --odometro KM
            jornada fechar --id ID --fim AAAA-MM-DDTHH:mm --odometro KM
            jornada listar [--veiculo ID]
              Uma jornada por veículo/dia, em ordem cronológica, encerrada no mesmo dia.
              Use odômetro total e tempo total de trabalho, incluindo espera/deslocamento.

            corrida adicionar --jornada ID --plataforma "IFOOD" --valor REAIS --km KM --minutos MIN
            corrida listar --jornada ID
            corrida editar --id ID [--plataforma NOME] [--valor REAIS] [--km KM] [--minutos MIN]
              Cadastre corridas enquanto a jornada estiver aberta. Valor recebido após taxas.
              Corridas representam trechos/tempos sem sobreposição (veja README).

            despesa adicionar --veiculo ID --data AAAA-MM-DD --categoria CATEGORIA --valor REAIS --descricao "Texto" [--jornada ID]
            despesa listar [--veiculo ID]
            despesa editar --id ID [--valor REAIS] [--descricao "Texto"]
              COMBUSTIVEL, MANUTENCAO, FIXO: pagamento no caixa, sem duplicar estimativa.
              EXTRA: pagamento + custo atribuído. Exige jornada aberta e data correspondente.

            manutencao plano --veiculo ID --item "Óleo" --intervalo-km KM --custo REAIS --ultimo-km KM
            manutencao registrar --plano ID --data AAAA-MM-DD --odometro KM --valor REAIS
            manutencao configurar --id ID [--intervalo-km KM] [--custo REAIS]
            manutencao alertas [--veiculo ID] [--antecedencia-km KM]
            manutencao historico [--plano ID]
              Intervalo deve ser positivo. Registrar gera pagamento de caixa automaticamente.
              Cadastro/execução de manutenção exige veículo sem jornada aberta.

            meta definir --veiculo ID --data AAAA-MM-DD --valor REAIS
            meta status --veiculo ID [--data AAAA-MM-DD]
              Meta de lucro líquido estimado, não de receita bruta.

            relatorio [--periodo DIARIO|SEMANAL|MENSAL] [--data AAAA-MM-DD] [--veiculo ID]
            comparativo [--periodo DIARIO|SEMANAL|MENSAL] [--data AAAA-MM-DD] [--veiculo ID]
              Padrão: diário, hoje, todos os veículos. Semana: segunda a domingo.
              Desempenho usa jornadas fechadas. Caixa inclui todas as receitas/pagamentos.

            ajuda | --help | --version
            Valores: 12.50 ou 12,50, sem milhar. Dinheiro: até 2 casas. Km/consumo: até 6 casas.
            Lucro estimado = receita - combustível por km - manutenção provisionada - fixo diário - extras.
            Saldo de caixa = valores de corridas registrados - despesas pagas nas datas informadas.
            Custos fixos são atribuídos uma vez por dia trabalhado/veículo; dias sem trabalho não geram provisão.
            Sem configuração completa de custos, o lucro estimado pode ficar superestimado.
            """;
}