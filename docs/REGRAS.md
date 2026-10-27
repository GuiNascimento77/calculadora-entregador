# Regras financeiras e operacionais

## Três conceitos diferentes

1. **Receita:** soma dos valores de corridas informados pelo usuário. Use o valor recebido após taxas, incluindo gorjetas ou bônus que deseja atribuir à corrida. Não informe o preço cobrado do cliente se parte dele pertence à plataforma.
2. **Saldo de caixa do período:** receita registrada no período menos pagamentos lançados nas datas do período. Não é o saldo bancário acumulado: não há saldo inicial, contas a receber ou conciliação.
3. **Lucro líquido estimado:** receita de jornadas fechadas menos custos atribuídos ao trabalho, incluindo provisões que ainda não foram pagas.

Todas as corridas são tratadas como recebidas no dia da jornada. Se a plataforma paga depois, o caixa mostrado é simplificado e pode não corresponder ao extrato naquele dia.

## Fórmulas por jornada

```text
km total = odômetro final − odômetro inicial
minutos totais = fim − início

combustível por km = preço por litro / consumo em km por litro
combustível estimado = km total × combustível por km

manutenção por km = soma(custo previsto de cada item / intervalo em km)
manutenção provisionada = km total × manutenção por km

fixos = custo fixo diário configurado no veículo
extras = soma(despesas EXTRA vinculadas à jornada)

custos = combustível estimado + manutenção provisionada + fixos + extras
lucro estimado = receita − custos
custo por km = custos / km total
ganho por hora = lucro estimado × 60 / minutos totais
```

Bike usa combustível por km igual a zero. Em moto/carro, o consumo deve ser positivo; preço pode ser zero, mas isso elimina a provisão de combustível. A estimativa assume consumo médio constante, não medição de abastecimentos.

`N/D` significa divisão indefinida (por exemplo, jornada com zero km), não custo zero. Uma jornada sem pedidos pode gerar prejuízo. Relatórios sem jornadas fechadas mostram zeros com aviso explícito e taxas `N/D`.

## Classificação das despesas

| Categoria | Entra no caixa? | Soma diretamente ao lucro estimado? | Como o custo é atribuído |
|---|---|---|---|
| COMBUSTIVEL | Sim | Não | Km total × preço/consumo |
| MANUTENCAO | Sim | Não | Km total × provisão dos planos |
| FIXO | Sim | Não | Fixo diário do veículo |
| EXTRA | Sim | Sim | Valor vinculado à jornada |

Exemplos: gasolina em COMBUSTIVEL, óleo/pneus em MANUTENCAO, aluguel/seguro em FIXO, pedágios em EXTRA. Usar outra categoria muda o significado do indicador: **não cadastre combustível ou aluguel como EXTRA se já estiverem provisionados**.

É necessário configurar os custos correspondentes: uma despesa FIXO de R$ 600,00 não altera sozinha o fixo diário; uma despesa MANUTENCAO de R$ 200,00 não cria automaticamente um plano. A CLI mostra avisos quando não há planos de manutenção.

`manutencao registrar` atualiza o marco do plano e cria uma despesa MANUTENCAO no mesmo commit. Não lance a mesma despesa novamente. Repetição com mesmo plano, data e odômetro é rejeitada; isso não impede todos os possíveis cadastros manuais duplicados.

## Rateio de custos fixos

O aplicativo recebe o custo **diário já rateado**, somando aluguel, seguro, impostos, depreciação temporal e outros custos fixos que deseja considerar.

Exemplo: R$ 600/mês, com 22 dias de trabalho previstos → R$ 27,27/dia (arredondado). Esse rateio inclui o custo dos dias sem trabalho dentro dos dias trabalhados previstos. Usar R$ 20/dia (600/30) e trabalhar só 22 dias não atribuiria os R$ 600 completos.

Uma jornada fechada recebe o fixo diário uma vez, mesmo sem corridas. Dias sem jornada não geram custo estimado automático; pagamentos desses dias continuam no caixa. A versão atual não ajusta retroativamente o rateio se o número real de dias diferir do previsto.

Não duplique depreciação: se está no fixo diário, não a inclua também no custo de um plano por km. O sistema não estima o valor de revenda nem faz cálculo fiscal.

## Jornada, distância e tempo

- Uma jornada por veículo/data; novas jornadas precisam ter data posterior às anteriores.
- Uma jornada aberta por veículo, fechada no mesmo dia, com precisão de minutos.
- Odômetro inicial não pode retroceder o último registrado. Deslocamentos pessoais entre jornadas podem aumentar o odômetro, mas não são considerados km de trabalho.
- Horário final deve ser posterior ao inicial; odômetro final deve ser maior ou igual ao inicial.
- A soma dos km/minutos dos pedidos não pode exceder a jornada. Não registre pedidos simultâneos com distâncias/tempos duplicados: agrupe a corrida ou reparta os trechos.
- Tempo é a janela completa da jornada, incluindo espera. Não há registro de pausas; se incluir uma pausa pessoal longa, ela também reduzirá o ganho/hora informado.
- Cadastre corridas e extras antes de fechar. Corridas podem ser corrigidas em jornada aberta; extras só podem ser corrigidos antes do fechamento.
- Não há reabertura ou exclusão de jornadas pela CLI. Horários/odômetros da abertura não são editáveis nesta versão; confira antes de confirmar o comando.

## Snapshot de custos

Ao abrir uma jornada, são copiados o combustível por km, a soma da manutenção por km e o fixo diário. Atualizações do veículo ou de planos não reescrevem os indicadores antigos. Mudanças de custo são bloqueadas enquanto o veículo tem jornada aberta.

Se esqueceu um plano antes da abertura, ele só será considerado nas jornadas futuras. Registros de caixa podem ser feitos depois, nas datas corretas, sem alterar a provisão daquela jornada.

## Comparativo entre plataformas

Para cada jornada fechada:

```text
peso da plataforma = km dos seus pedidos / km de todos os pedidos
custo alocado = custo total da jornada × peso
lucro estimado da plataforma = receita da plataforma − custo alocado
```

Se todos os km dos pedidos forem zero, usa participação no tempo ativo. Há fallback defensivo de divisão igual se também não houver minutos (não ocorre em corridas válidas, que exigem minutos positivos). Jornadas sem pedidos aparecem como `(SEM CORRIDAS)`, preservando seus custos no comparativo.

Todos os custos da jornada entram no rateio, inclusive custos de deslocamento vazio e espera. Não é possível afirmar a qual plataforma esses trechos pertenceram. Extras também são compartilhados pela mesma regra, não atribuídos a um pedido específico.

O relatório mostra **bruto/km de entrega** e **bruto/hora ativa** como métricas diferentes de **custo/km total** e **lucro/hora total**. Plataformas são normalizadas para maiúsculas e espaços externos removidos; nomes diferentes permanecem separados. O sistema não integra APIs externas.

## Períodos e metas

- Diário: data exata.
- Semanal: segunda a domingo contendo a data indicada.
- Mensal: primeiro ao último dia do mês indicado.
- Limites inclusivos; data padrão é hoje no relógio local da máquina.
- Desempenho exclui jornadas abertas. Caixa inclui todas as corridas registradas e despesas nas datas selecionadas.
- Meta é de lucro estimado por veículo/data; uma nova definição substitui a anterior.
- Progresso só é calculado após fechar a jornada. Lucro negativo pode produzir progresso negativo; acima de 100% indica superação da meta. O restante nunca é menor que zero.

## Precisão e limites

Dinheiro usa `BigDecimal` construído de texto, com no máximo duas casas nas entradas. Distância/consumo admitem até seis casas. Valores são armazenados como TEXT no SQLite e convertidos para decimal no Java; SQL não soma valores como ponto flutuante.

Divisões e multiplicações de estimativa usam `MathContext.DECIMAL128`. Não se arredondam parcelas para centavos antes de somar. A apresentação monetária usa duas casas e `HALF_UP`. Linhas individualmente arredondadas podem divergir em um centavo do total ou entre plataformas; os cálculos internos preservam mais precisão.

Entradas negativas são rejeitadas, mas prejuízo é válido. Campos numéricos financeiros/distância têm limite de magnitude de 1 trilhão, precisão total até 18 dígitos e escala até 6. Valores monetários limitam casas significativas decimais a 2. Corridas admitem 1–1440 minutos.

## O que esta estimativa não faz

Não mede consumo real, não calcula obrigações fiscais, não é sistema contábil e não garante intervalos de manutenção. Configure todos os custos relevantes e confira o manual do veículo. Seguro, financiamento, impostos, depreciação e acidentes não são inferidos automaticamente.