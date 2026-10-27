# Calculadora do Entregador — v1.0.0

Primeira versão completa em CLI, com Java 21 e SQLite local.

## Incluído

- Cadastro de motos, bikes e carros; parâmetros de combustível e custo fixo diário.
- Jornadas por horário e odômetro, corridas por plataforma e despesas categorizadas.
- Lucro líquido estimado, saldo de caixa, custo por km total e ganho por hora total.
- Metas diárias, relatórios diários/semanais/mensais e comparação de plataformas.
- Provisões, planos, alertas e histórico de manutenção.
- Persistência com migrações, testes e CI.

## Executar

Baixe `calculadora-entregador.jar` nos arquivos desta versão. Instale Java 21 e execute:

```sh
java -jar calculadora-entregador.jar ajuda
```

SQLite e suas dependências estão incluídos. Não é necessário instalar Maven para executar o JAR. O banco padrão é `~/.calculadora-entregador/dados.db`.

## Limites conhecidos

Uma jornada por veículo/dia, encerrada no mesmo dia. A receita é tratada como recebida na data da jornada. Custos fixos precisam ser rateados pelo usuário. O comparativo de plataformas usa custos compartilhados estimados. A precisão dos resultados depende de configurar todos os custos relevantes.

Esta versão não inclui API REST, interface web, autenticação ou sincronização.

Veja o README e `docs/EXEMPLO.md` para um cenário completo com banco fictício separado.