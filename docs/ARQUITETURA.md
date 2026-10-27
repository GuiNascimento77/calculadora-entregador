# Arquitetura e decisões técnicas

## Stack

- Java 21, Maven.
- JDBC com SQLite local (driver Xerial).
- JUnit Jupiter, Maven Surefire.
- Maven Shade para JAR executável com dependências e bibliotecas nativas do driver.
- GitHub Actions em Linux: compilação, testes, cenário real do JAR e artefatos.

Sem framework de API, ORM ou container obrigatório. A intenção é tornar explícitas as regras do domínio e manter uma CLI fácil de rodar offline.

## Fluxo de um comando

```text
Main + Options
     ↓ parsing estrito / formatação
DeliveryService ou ReportService
     ↓ invariantes / transações / seleção de período
Repository + Database
     ↓ SQL parametrizado / JDBC
SQLite local

Metrics + CalculadoraLucro
     ↑ funções de cálculo chamadas pela camada de aplicação
```

- `domain/Models`: records imutáveis e enums; não conhecem terminal ou JDBC.
- `domain/Checks`: validação reutilizável de valores e texto.
- `domain/Metrics`: cálculos de jornada, agregação e taxas. `CalculadoraLucro` preserva a primeira regra do projeto e é reutilizada pelos indicadores.
- `application/DeliveryService`: valida estado, cronologia e relações; todos os comandos de escrita usam transações.
- `application/ReportService`: monta intervalos, separa caixa/desempenho e rateia plataformas.
- `persistence/Database`: abre conexão, ativa foreign keys, espera por locks até 5 segundos e aplica migrações.
- `persistence/Repository`: SQL com prepared statements, mapeamento de records e persistência decimal em texto.
- `cli/Options`: rejeita opções desconhecidas/repetidas, valores ausentes, IDs inválidos, enums e datas incorretos.
- `cli/Main`: roteia comandos e imprime resultados. `run` recebe escritores e retorna código de saída, facilitando testes sem `System.exit`.

## Modelo persistido

```text
vehicles 1 ─── N shifts 1 ─── N rides
vehicles 1 ─── N expenses (vínculo opcional com shifts; obrigatório em EXTRA)
vehicles 1 ─── N maintenance_plans 1 ─── N maintenance_history
maintenance_history 1 ─── 1 expenses (pagamento gerado automaticamente)
vehicles 1 ─── N goals (uma meta por data)
```

### Entidades

| Tabela | Responsabilidade |
|---|---|
| vehicles | Tipo, odômetro conhecido e parâmetros atuais de custo |
| shifts | Horários, odômetros inicial/final e snapshot de custos |
| rides | Receita e produtividade ativa de cada plataforma |
| expenses | Pagamentos por data, categoria e descrição |
| maintenance_plans | Intervalo, custo previsto e último marco por item |
| maintenance_history | Serviços realizados, odômetro e pagamento vinculado |
| goals | Meta de lucro estimado por veículo/data |

`INTEGER PRIMARY KEY` gera IDs locais. Não há exclusão pela CLI. Relações têm chaves estrangeiras. Uma restrição única garante veículo/data na jornada; um índice parcial impede duas jornadas abertas para o mesmo veículo. Invariantes monetárias/cronológicas mais detalhadas são verificadas no serviço — editar o banco por fora pode violá-las.

## Migrações e integridade

- `V1.sql`: tabelas, relações e restrições básicas.
- `V2.sql`: índices de consulta e unicidade de jornada aberta.
- `PRAGMA user_version` registra a versão aplicada.
- Cada migração roda em transação; reabrir o banco não recria tabelas.
- Banco de versão superior à suportada é rejeitado, não rebaixado.
- Realizar manutenção cria histórico + despesa + novo marco/odômetro atomicamente.
- Fechar jornada atualiza fechamento e odômetro do veículo no mesmo commit.
- Erros de validação/SQL produzem rollback; testes verificam esse comportamento.

Faça backup com comandos encerrados antes de futuras atualizações. O banco é local e sem criptografia; permissões e backups são responsabilidade do usuário. Não há sincronização nem modelo de concorrência para serviço multiusuário. Execute uma escrita por vez; disputas de lock podem retornar erro e exigem nova tentativa manual.

## Decisões financeiras

Provisão de combustível/manutenção é diferente de pagamento. Os dois são mantidos em indicadores separados para evitar dupla contagem. O snapshot da abertura evita que inflação de preços altere silenciosamente jornadas antigas. O custo fixo diário é um parâmetro explicitamente rateado pelo usuário.

Distância remunerada não é a base do custo total: odômetro inclui ida à coleta e retorno vazio. Tempo ativo de pedidos não é a base do ganho/hora total: horário da jornada inclui espera. O rateio de custos por plataforma é uma hipótese transparente, não uma atribuição observada.

## Estratégia de testes

- **Unidade:** fórmulas, precisão, prejuízo, zero km, ausência de horas e custos sem dupla contagem.
- **Aplicação com SQLite:** casos de uso, correções, metas, snapshots, cronologia, pagamentos de manutenção e relatórios.
- **Persistência:** migrações novas/antigas, reabertura, foreign keys e rollback.
- **CLI:** parse, mensagens, códigos de saída e cenário completo com banco isolado.
- **JAR:** `examples/demo.sh` lança processos Java reais para validar driver, recursos de migração e continuidade dos dados entre comandos.

Os bancos de teste ficam em diretórios temporários de JUnit; o cenário do JAR cria um banco novo e preserva sua localização para inspeção. Nenhum teste usa o banco pessoal padrão.

## Como evoluir para API REST

1. Expor os casos de uso por controladores HTTP, sem reaproveitar o texto da CLI como contrato.
2. Adicionar DTOs e validações HTTP; IDs e campos persistidos não precisam virar o contrato público diretamente.
3. Extrair interfaces de repositório se houver outra persistência ou necessidade de mocks; hoje o serviço usa um repositório JDBC concreto para manter o projeto pequeno.
4. Rever conexão/transações e concorrência para múltiplas requisições — não compartilhar esta conexão da CLI entre threads.
5. Acrescentar autenticação/autorização e propriedade dos dados antes de expor finanças na internet.
6. Resolver os limites de uma jornada/dia, pausas, turnos noturnos e correções auditadas conforme novos requisitos.
7. Criar uma interface responsiva com design definido na fase web.

Esta entrega não inclui API, deploy, servidor, interface web ou publicação automática. O workflow apenas compila, testa e disponibiliza artefatos no próprio GitHub Actions.