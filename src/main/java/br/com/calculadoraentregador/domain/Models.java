package br.com.calculadoraentregador.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Valores do domínio. Nenhum record depende de JDBC ou da CLI. */
public final class Models {
    private Models() {}

    public enum TipoVeiculo { MOTO, BIKE, CARRO }
    public enum CategoriaDespesa { COMBUSTIVEL, MANUTENCAO, FIXO, EXTRA }

    public record Veiculo(long id, String nome, TipoVeiculo tipo, BigDecimal odometro,
                          BigDecimal consumo, BigDecimal precoCombustivel, BigDecimal fixoDiario) {}
    public record Jornada(long id, long veiculoId, LocalDateTime inicio, LocalDateTime fim,
                          BigDecimal kmInicio, BigDecimal kmFim, BigDecimal combustivelPorKm,
                          BigDecimal manutencaoPorKm, BigDecimal fixoDiario) {
        public boolean aberta() { return fim == null; }
        public LocalDate data() { return inicio.toLocalDate(); }
    }
    public record Corrida(long id, long jornadaId, String plataforma, BigDecimal valor,
                         BigDecimal km, int minutos) {}
    public record Despesa(long id, long veiculoId, Long jornadaId, LocalDate data,
                          CategoriaDespesa categoria, BigDecimal valor, String descricao) {}
    public record PlanoManutencao(long id, long veiculoId, String item, BigDecimal intervaloKm,
                                 BigDecimal custo, BigDecimal ultimoKm) {}
    public record RegistroManutencao(long id, long planoId, LocalDate data, BigDecimal odometro,
                                    BigDecimal valor, long despesaId) {}
    public record Meta(long veiculoId, LocalDate data, BigDecimal valor) {}
}