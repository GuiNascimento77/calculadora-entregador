# Exemplo completo — dados fictícios

Primeiro execute `mvn clean verify` na raiz do projeto. Os comandos abaixo funcionam em Bash, PowerShell e Prompt de Comando, com Java 21 no PATH.

**Escolha um banco novo.** Aqui usamos `exemplo.db` na pasta atual. Se o arquivo já existir, escolha outro nome e use-o em todos os comandos. Não use um banco pessoal: este cenário espera banco vazio e IDs iniciando em 1.

## 1. Veículo, custos e manutenção

```sh
java -jar target/calculadora-entregador.jar --banco exemplo.db veiculo adicionar --nome "Moto de exemplo" --tipo MOTO --odometro 10000 --consumo 30 --combustivel 6 --fixo-diario 20
java -jar target/calculadora-entregador.jar --banco exemplo.db manutencao plano --veiculo 1 --item "Oleo" --intervalo-km 1000 --custo 30 --ultimo-km 9100
java -jar target/calculadora-entregador.jar --banco exemplo.db manutencao plano --veiculo 1 --item "Pneu" --intervalo-km 10000 --custo 200 --ultimo-km 9000
java -jar target/calculadora-entregador.jar --banco exemplo.db veiculo listar
```

- Consumo: 30 km/L; combustível: R$ 6,00/L → R$ 0,20/km.
- Óleo: R$ 30,00 / 1.000 km → R$ 0,03/km.
- Pneu: R$ 200,00 / 10.000 km → R$ 0,02/km.
- Fixo diário: R$ 20,00. Inclua aqui seu rateio de aluguel e demais custos fixos; o aplicativo não calcula automaticamente esse rateio mensal.

Os intervalos e preços são **ilustrativos**, não recomendações de manutenção. Use o manual do veículo e seus custos reais.

## 2. Meta e abertura da jornada

```sh
java -jar target/calculadora-entregador.jar --banco exemplo.db meta definir --veiculo 1 --data 2026-10-19 --valor 150
java -jar target/calculadora-entregador.jar --banco exemplo.db jornada abrir --veiculo 1 --inicio 2026-10-19T08:00 --odometro 10000
```

O comando retorna o ID da jornada: `1`. A configuração de custos é congelada neste momento.

## 3. Corridas e pagamentos

```sh
java -jar target/calculadora-entregador.jar --banco exemplo.db corrida adicionar --jornada 1 --plataforma IFOOD --valor 120 --km 40 --minutos 80
java -jar target/calculadora-entregador.jar --banco exemplo.db corrida adicionar --jornada 1 --plataforma RAPPI --valor 80 --km 20 --minutos 40
java -jar target/calculadora-entregador.jar --banco exemplo.db despesa adicionar --veiculo 1 --data 2026-10-19 --categoria COMBUSTIVEL --valor 60 --descricao "Abastecimento"
java -jar target/calculadora-entregador.jar --banco exemplo.db despesa adicionar --veiculo 1 --jornada 1 --data 2026-10-19 --categoria EXTRA --valor 5 --descricao "Pedagio"
java -jar target/calculadora-entregador.jar --banco exemplo.db corrida listar --jornada 1
```

As plataformas são apenas rótulos informados manualmente. Não há integração com iFood/Rappi nem confirmação de ganhos externos.

`COMBUSTIVEL` registra o dinheiro pago no posto. `EXTRA` adiciona o pagamento ao caixa e o custo à jornada. Não lance combustível como EXTRA: ele já está estimado por km.

## 4. Fechamento e relatório

```sh
java -jar target/calculadora-entregador.jar --banco exemplo.db jornada fechar --id 1 --fim 2026-10-19T12:00 --odometro 10100
java -jar target/calculadora-entregador.jar --banco exemplo.db meta status --veiculo 1 --data 2026-10-19
java -jar target/calculadora-entregador.jar --banco exemplo.db relatorio --periodo SEMANAL --data 2026-10-19 --veiculo 1
java -jar target/calculadora-entregador.jar --banco exemplo.db relatorio --periodo MENSAL --data 2026-10-19 --veiculo 1
java -jar target/calculadora-entregador.jar --banco exemplo.db comparativo --periodo DIARIO --data 2026-10-19 --veiculo 1
```

O odômetro total registra 100 km, embora as corridas somem apenas 60 km. A jornada registra 4 horas, embora os pedidos somem apenas 2 horas. Assim, deslocamentos sem pedido e espera não desaparecem do cálculo.

| Indicador | Resultado |
|---|---:|
| Receita | R$ 200,00 |
| Combustível estimado | R$ 20,00 |
| Manutenção provisionada | R$ 5,00 |
| Fixo diário | R$ 20,00 |
| Extra | R$ 5,00 |
| Custo total estimado | R$ 50,00 |
| Lucro líquido estimado | R$ 150,00 |
| Custo/km total | R$ 0,50 |
| Ganho/hora total | R$ 37,50 |
| Saldo de caixa | R$ 135,00 |
| Meta | 100%, atingida |

No comparativo, iFood recebe 40/60 dos custos: R$ 33,33; Rappi recebe 20/60: R$ 16,67. Seus lucros estimados rateados ficam R$ 86,67 e R$ 63,33. Esses números são uma atribuição dos custos compartilhados, não uma medição exata por plataforma.

## 5. Realizar a manutenção vencida

O óleo foi trocado aos 9.100 km e tem intervalo fictício de 1.000 km: vence aos 10.100 km. Após fechar a jornada, o alerta mostra `VENCIDA`.

```sh
java -jar target/calculadora-entregador.jar --banco exemplo.db manutencao alertas --veiculo 1
java -jar target/calculadora-entregador.jar --banco exemplo.db manutencao registrar --plano 1 --data 2026-10-19 --odometro 10100 --valor 30
java -jar target/calculadora-entregador.jar --banco exemplo.db manutencao historico --plano 1
java -jar target/calculadora-entregador.jar --banco exemplo.db manutencao alertas --veiculo 1
java -jar target/calculadora-entregador.jar --banco exemplo.db relatorio --data 2026-10-19 --veiculo 1
```

O novo vencimento será 11.100 km. O pagamento de R$ 30,00 entra no caixa automaticamente: saldo passa de R$ 135,00 para R$ 105,00. O lucro estimado continua em R$ 150,00, pois já considerou provisão por km. Não registre o pagamento novamente como despesa manual.

## Outros usos

### Bike (em outro banco novo)

```sh
java -jar target/calculadora-entregador.jar --banco bike-exemplo.db veiculo adicionar --nome "Minha bike" --tipo BIKE --odometro 0 --fixo-diario 2
```

Não há combustível. Cadastre planos de pneus, corrente, freios e outros itens relevantes para provisionar manutenção.

### Corrigir valores antes de fechar a jornada

Na sua jornada ainda aberta, substitua os IDs pelos retornados no cadastro:

```sh
java -jar target/calculadora-entregador.jar corrida editar --id 1 --km 12.5 --minutos 25
java -jar target/calculadora-entregador.jar despesa editar --id 1 --valor 7.50 --descricao "Pedagio corrigido"
```

Estes dois comandos usam o banco padrão, não `exemplo.db`. Se estiver trabalhando em outro banco, informe `--banco`.

### Atualizar custos para próximas jornadas

```sh
java -jar target/calculadora-entregador.jar veiculo configurar --id 1 --combustivel 6.50 --fixo-diario 25
java -jar target/calculadora-entregador.jar manutencao configurar --id 1 --custo 40 --intervalo-km 1000
```

O veículo deve estar sem jornada aberta. Jornadas antigas mantêm suas estimativas originais. Veja todos os comandos com `ajuda`.