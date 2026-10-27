#!/usr/bin/env bash
# Cenário de exemplo e smoke test do JAR. Apenas dados fictícios, banco novo.
set -euo pipefail
# O cenário usa texto UTF-8 também nos argumentos recebidos pelo Java no Linux.
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
ROOT="$(dirname "$(dirname "$(realpath "$0")")")"
JAR="$ROOT/target/calculadora-entregador.jar"
if [[ ! -f "$JAR" ]]; then
  printf 'Compile primeiro: mvn clean verify\n' >&2
  exit 1
fi
if [[ $# -gt 1 ]]; then
  printf 'Uso: bash examples/demo.sh [caminho-de-um-banco-novo.db]\n' >&2
  exit 2
fi
if [[ $# -eq 1 ]]; then
  DB="$1"
else
  DB="$(mktemp -d)/demo.db"
fi
if [[ -e "$DB" ]]; then
  printf 'Não vou alterar banco existente: %s\n' "$DB" >&2
  exit 2
fi
cli() { java -jar "$JAR" --banco "$DB" "$@"; }
cli veiculo adicionar --nome "Moto de exemplo" --tipo MOTO --odometro 10000 --consumo 30 --combustivel 6 --fixo-diario 20
cli manutencao plano --veiculo 1 --item "Óleo" --intervalo-km 1000 --custo 30 --ultimo-km 9100
cli manutencao plano --veiculo 1 --item "Pneu" --intervalo-km 10000 --custo 200 --ultimo-km 9000
cli meta definir --veiculo 1 --data 2026-10-19 --valor 150
cli jornada abrir --veiculo 1 --inicio 2026-10-19T08:00 --odometro 10000
cli corrida adicionar --jornada 1 --plataforma IFOOD --valor 120 --km 40 --minutos 80
cli corrida adicionar --jornada 1 --plataforma RAPPI --valor 80 --km 20 --minutos 40
cli despesa adicionar --veiculo 1 --data 2026-10-19 --categoria COMBUSTIVEL --valor 60 --descricao "Abastecimento"
cli despesa adicionar --veiculo 1 --jornada 1 --data 2026-10-19 --categoria EXTRA --valor 5 --descricao "Pedágio"
OUTPUT="$(cli jornada fechar --id 1 --fim 2026-10-19T12:00 --odometro 10100)"
printf '%s\n' "$OUTPUT"
grep -Fq 'Lucro líquido estimado: R$ 150,00' <<< "$OUTPUT"
grep -Fq 'Ganho líquido por hora total: R$ 37,50' <<< "$OUTPUT"
grep -Fq 'saldo de caixa: R$ 135,00' <<< "$OUTPUT"
grep -Fq 'VENCIDA' <<< "$OUTPUT"
cli comparativo --periodo SEMANAL --data 2026-10-19 --veiculo 1
cli relatorio --periodo MENSAL --data 2026-10-19 --veiculo 1
cli manutencao registrar --plano 1 --data 2026-10-19 --odometro 10100 --valor 30
cli manutencao historico --plano 1
AFTER="$(cli relatorio --data 2026-10-19 --veiculo 1)"
grep -Fq 'Lucro líquido estimado: R$ 150,00' <<< "$AFTER"
grep -Fq 'saldo de caixa: R$ 105,00' <<< "$AFTER"
printf 'Cenário validado. Banco fictício preservado em: %s\n' "$DB"