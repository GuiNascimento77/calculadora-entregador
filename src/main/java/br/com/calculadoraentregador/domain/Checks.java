package br.com.calculadoraentregador.domain;

import java.math.BigDecimal;
import java.util.Objects;

public final class Checks {
    private Checks() {}

    public static BigDecimal nonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " é obrigatório.");
        if (value.signum() < 0) throw new IllegalArgumentException(name + " não pode ser negativo.");
        if (value.precision() > 18 || value.scale() > 6 || value.abs().compareTo(new BigDecimal("1000000000000")) > 0)
            throw new IllegalArgumentException(name + ": limite de 12 dígitos inteiros e 6 casas decimais.");
        return value;
    }

    public static BigDecimal positive(BigDecimal value, String name) {
        nonNegative(value, name);
        if (value.signum() == 0) throw new IllegalArgumentException(name + " deve ser maior que zero.");
        return value;
    }

    public static BigDecimal money(BigDecimal value, String name) {
        nonNegative(value, name);
        if (value.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException(name + " deve ter no máximo duas casas decimais.");
        return value;
    }

    public static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " é obrigatório.");
        String clean = value.strip();
        if (clean.length() > 200 || clean.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(name + " deve ter até 200 caracteres, sem controles.");
        return clean;
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}