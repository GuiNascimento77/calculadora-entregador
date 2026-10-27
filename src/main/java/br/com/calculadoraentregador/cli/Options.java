package br.com.calculadoraentregador.cli;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.util.*;

/** Parser pequeno e estrito: cada opção tem exatamente um valor. */
final class Options {
    private final Map<String,String> values = new LinkedHashMap<>();
    Options(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i += 2) {
            String key = tokens.get(i);
            if (!key.startsWith("--") || key.length() == 2)
                throw new IllegalArgumentException("Esperada uma opção --nome, recebido: " + key);
            if (i + 1 >= tokens.size() || tokens.get(i + 1).startsWith("--"))
                throw new IllegalArgumentException("Falta o valor de " + key);
            if (values.putIfAbsent(key.substring(2),tokens.get(i + 1)) != null)
                throw new IllegalArgumentException("Opção repetida: " + key);
        }
    }
    void only(String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String key : values.keySet()) if (!names.contains(key))
            throw new IllegalArgumentException("Opção desconhecida: --" + key);
    }
    boolean has(String key) { return values.containsKey(key); }
    String text(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Informe --" + key);
        return value;
    }
    String text(String key, String fallback) { return has(key) ? text(key) : fallback; }
    BigDecimal decimal(String key) {
        String s = text(key);
        if (!s.matches("[+-]?\\d+([.,]\\d+)?"))
            throw new IllegalArgumentException("Número inválido em --" + key + ". Use 12.50 ou 12,50, sem separador de milhar.");
        try { return new BigDecimal(s.replace(',','.')); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Número inválido em --" + key); }
    }
    BigDecimal decimal(String key, String fallback) { return has(key) ? decimal(key) : new BigDecimal(fallback); }
    long id(String key) {
        try {
            long n = Long.parseLong(text(key));
            if (n <= 0) throw new NumberFormatException(); return n;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("--" + key + " exige um ID inteiro positivo."); }
    }
    Long optionalId(String key) { return has(key) ? id(key) : null; }
    int integer(String key) {
        try { return Integer.parseInt(text(key)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("--" + key + " exige um número inteiro."); }
    }
    LocalDate date(String key) {
        try { return LocalDate.parse(text(key)); }
        catch (DateTimeException e) { throw new IllegalArgumentException("Data inválida em --" + key + ". Use AAAA-MM-DD."); }
    }
    LocalDate dateOrToday(String key) { return has(key) ? date(key) : LocalDate.now(); }
    LocalDateTime time(String key) {
        String s = text(key);
        if (!s.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}"))
            throw new IllegalArgumentException("Horário inválido em --" + key + ". Use AAAA-MM-DDTHH:mm.");
        try { return LocalDateTime.parse(s); }
        catch (DateTimeException e) { throw new IllegalArgumentException("Horário inválido em --" + key); }
    }
    <T extends Enum<T>> T enumeration(String key, Class<T> type) {
        String s = text(key);
        try { return Enum.valueOf(type,s.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--" + key + ": escolha " + Arrays.toString(type.getEnumConstants()));
        }
    }
}