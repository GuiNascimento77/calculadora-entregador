package br.com.calculadoraentregador.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculadoraLucroTest {

    private final CalculadoraLucro calculadora = new CalculadoraLucro();

    @Test
    void deveDescontarCustosDaReceita() {
        assertEquals(new BigDecimal("134.50"),
                calculadora.calcular(new BigDecimal("200.00"), new BigDecimal("65.50")));
    }

    @Test
    void devePermitirPrejuizo() {
        assertEquals(new BigDecimal("-20.00"),
                calculadora.calcular(new BigDecimal("30.00"), new BigDecimal("50.00")));
    }

    @Test
    void deveAceitarReceitaECustosZerados() {
        assertEquals(new BigDecimal("0.00"),
                calculadora.calcular(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void deveArredondarSomenteOResultadoParaCentavos() {
        assertEquals(new BigDecimal("0.01"),
                calculadora.calcular(new BigDecimal("0.014"), new BigDecimal("0.006")));
        assertEquals(new BigDecimal("10.01"),
                calculadora.calcular(new BigDecimal("10.005"), BigDecimal.ZERO));
    }

    @Test
    void deveRejeitarEntradasNegativas() {
        assertThrows(IllegalArgumentException.class,
                () -> calculadora.calcular(new BigDecimal("-1"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> calculadora.calcular(BigDecimal.ZERO, new BigDecimal("-1")));
    }

    @Test
    void deveRejeitarEntradasNulas() {
        assertThrows(NullPointerException.class,
                () -> calculadora.calcular(null, BigDecimal.ZERO));
        assertThrows(NullPointerException.class,
                () -> calculadora.calcular(BigDecimal.ZERO, null));
    }
    @Test
    void devePreservarPrecisaoParaIndicadores() {
        assertEquals(new BigDecimal("0.008"),calculadora.calcularExato(new BigDecimal("0.014"),new BigDecimal("0.006")));
    }
}