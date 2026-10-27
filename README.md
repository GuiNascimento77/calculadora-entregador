# Calculadora do Entregador

[![Java CI](https://github.com/GuiNascimento77/calculadora-entregador/actions/workflows/ci.yml/badge.svg)](https://github.com/GuiNascimento77/calculadora-entregador/actions/workflows/ci.yml)

**Quanto sobra depois de rodar?** Uma CLI local em Java 21 que separa receita bruta, saldo de caixa e lucro líquido estimado de motoboys, entregadores de bike e motoristas de app.

O diferencial é usar **todos os km de trabalho e todo o tempo da jornada**, não apenas os trechos com pedido. Manutenção é provisionada por km; pagamentos ficam em um indicador separado para evitar dupla contagem.

## Funcionalidades

- Cadastro e configuração de motos, bikes e carros.
- Abertura/fechamento de jornada com horários e odômetro.
- Corridas por plataforma: valor, km e minutos; correção enquanto a jornada estiver aberta.
- Despesas de combustível, manutenção, custos fixos (como aluguel) e extras.
- Lucro líquido estimado, custo por km total e ganho por hora total.
- Meta de lucro por veículo/dia, com progresso e valor restante.
- Relatórios diários, semanais e mensais, filtráveis por veículo.
- Comparativo de plataformas com receita, produtividade ativa e custos rateados.
- Planos e histórico de manutenção, alertas por km e provisão de custos.
- SQLite com migrações versionadas, transações e chaves estrangeiras.
- Testes de domínio, integração com banco e CLI; workflow GitHub Actions.

**Versão 1.0.0:** CLI por comandos, sem API REST, interface web, autenticação ou sincronização. Não é um menu interativo. A fase visual será discutida separadamente.

## Começar em 2 minutos

Para compilar: **JDK 21 + Maven 3.9 ou superior**. Para usar o JAR já compilado: apenas Java 21. SQLite e dependências estão dentro do JAR; não é necessário instalar um servidor ou o programa `sqlite3`.

Na pasta que contém o `pom.xml`:

```sh
java -version
javac -version
mvn -version
mvn clean verify
java -jar target/calculadora-entregador.jar ajuda
```

O Maven deve informar Java 21. Ajuste `JAVA_HOME` se necessário. A primeira compilação precisa de internet para baixar dependências; a CLI funciona offline depois disso.

Se baixou só o JAR, substitua `target/calculadora-entregador.jar` por `calculadora-entregador.jar` nos comandos.

### Um primeiro veículo

```sh
java -jar target/calculadora-entregador.jar veiculo adicionar --nome "Minha moto" --tipo MOTO --odometro 10000 --consumo 30 --combustivel 6 --fixo-diario 20
java -jar target/calculadora-entregador.jar veiculo listar
```

O banco padrão é criado em **`~/.calculadora-entregador/dados.db`**, na pasta pessoal do usuário. Cada comando abre esse mesmo banco. Para outro banco, acrescente `--banco caminho.db` em **todos** os comandos.

Use ponto ou vírgula decimal (`12.50` ou `12,50`), sem separador de milhar. Datas: `AAAA-MM-DD`; horários: `AAAA-MM-DDTHH:mm`. Dinheiro admite até 2 casas decimais; km/consumo até 6. Nomes e descrições com espaços devem ficar entre aspas.

## Demonstração completa

[Siga o cenário com comandos para qualquer terminal](docs/EXEMPLO.md). Ele usa dados fictícios e um banco separado.

Em Linux com Bash, há também um cenário automático que valida o JAR e recusa bancos existentes:

```sh
bash examples/demo.sh
```

O script cria um banco temporário novo e informa onde ele ficou. Não modifica seu banco padrão.

### Resultado do cenário

```text
Receita bruta: R$ 200,00
Combustível estimado: R$ 20,00
Manutenção provisionada: R$ 5,00
Custos fixos: R$ 20,00
Extras: R$ 5,00
Lucro líquido estimado: R$ 150,00
Km totais: 100 | Tempo total: 240 min
Custo por km total: R$ 0,50
Ganho líquido por hora total: R$ 37,50
Saldo de caixa: R$ 135,00
```

Caixa e lucro divergem: o cenário pagou R$ 60,00 em combustível, mas estima consumo de apenas R$ 20,00 durante a jornada. Veja [fórmulas e regras](docs/REGRAS.md).

## Qualidade e CI

```sh
mvn test
mvn clean verify
bash examples/demo.sh
```

Os testes usam bancos temporários isolados. Cobrem precisão monetária, prejuízo, divisão por zero, espera, deslocamento vazio, custos sem dupla contagem, snapshots, ordem cronológica, metas, manutenção, relatórios, migrações, rollback e execução da CLI.

O workflow `.github/workflows/ci.yml` executa build, testes e smoke test do JAR a cada push/PR, e disponibiliza o JAR e os relatórios JUnit como artefatos. Consulte o badge acima e a aba Actions para o resultado do commit atual.

## Arquitetura

```text
src/main/java/br/com/calculadoraentregador/
├── domain/       records, validações e fórmulas puras
├── application/  casos de uso e relatórios
├── persistence/  conexão, migrações e SQL parametrizado
└── cli/          parsing, ajuda e saída no terminal
src/main/resources/db/migration/   V1.sql e V2.sql
src/test/java/                     testes por camada
```

As regras não dependem da CLI. Veja [modelo e decisões técnicas](docs/ARQUITETURA.md) para entender como adicionar uma API no futuro.

## Limitações importantes

- Uma jornada por veículo/dia, registrada em ordem e encerrada no mesmo dia. Não há pausas ou turnos noturnos atravessando a meia-noite.
- A soma dos km e minutos das corridas não pode exceder a jornada. Pedidos simultâneos devem ser agrupados ou ter trechos/tempos repartidos sem duplicação.
- A receita deve ser o valor efetivamente recebido após taxas da plataforma. Não há controle de contas a receber, estornos ou repasses futuros.
- O fixo diário só é atribuído a dias com jornada fechada. Defina um rateio adequado aos dias de trabalho; dias sem trabalho não recebem custo estimado automático.
- Comparação entre plataformas usa um rateio, não rastreamento real de espera e deslocamentos por plataforma.
- Depreciação, seguro, impostos e outros custos precisam ser incluídos conscientemente no fixo diário ou em extras, sem duplicá-los.
- Banco local, sem criptografia, backup automático, vários usuários ou exclusão de registros pela CLI.
- Corridas e extras de jornadas fechadas são imutáveis. Pagamentos manuais de caixa podem ser corrigidos; pagamentos gerados por manutenção não podem ser editados isoladamente.

**Não chame a estimativa de lucro real completo sem cadastrar todos os custos relevantes.**

## Dados e backup

Com todos os comandos encerrados, copie o arquivo `.db` para um local seguro. Para restaurar, use a cópia com `--banco`. Não edite tabelas manualmente; isso pode quebrar as invariantes. Migrações rodam automaticamente ao abrir o aplicativo; um banco de versão futura é rejeitado.

Não envie bancos pessoais para o GitHub. O `.gitignore` exclui `.db`, arquivos auxiliares, `target/` e configurações locais de IDE.

## Repositório e downloads

- [Código no GitHub](https://github.com/GuiNascimento77/calculadora-entregador)
- [Execuções de CI](https://github.com/GuiNascimento77/calculadora-entregador/actions)
- [Versões e JAR para download](https://github.com/GuiNascimento77/calculadora-entregador/releases)

Para obter o código:

```sh
git clone https://github.com/GuiNascimento77/calculadora-entregador.git
```

Abra a pasta clonada e execute `mvn clean verify`. Não inclua bancos pessoais em commits. O projeto não inclui uma licença própria por padrão; uma publicação pública não concede automaticamente todos os direitos de reutilização.

## Roadmap

API REST, interface responsiva, pausas/turnos noturnos, exportação CSV, rastreamento mais preciso de custos por plataforma e backup. Nenhum desses itens é necessário para usar a CLI atual.
