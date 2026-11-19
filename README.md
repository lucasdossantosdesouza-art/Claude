# Sistema de Contas a Receber, Pós-venda e Comissão de Vendedor

Monorepo com **frontend Angular**, **API Spring Boot**, autenticação **Keycloak** e banco **PostgreSQL**.

| Camada | Tecnologia |
|---|---|
| Frontend | Angular 22 (CLI, standalone components, signals, zoneless), Angular Material, keycloak-angular |
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security OAuth2 Resource Server, Actuator), Flyway, springdoc-openapi |
| Autenticação | Keycloak 26 (OpenID Connect, Authorization Code + PKCE), papéis `VENDEDOR`, `GESTOR` e `PRODUCAO` |
| Banco | PostgreSQL 17 (funciona em qualquer PostgreSQL gerenciado na nuvem: RDS, Cloud SQL, Azure, Supabase, Neon) |
| Leitura de OS | PDFBox (texto do PDF), Tesseract (OCR de fotos) e, opcionalmente, IA (Claude) para qualquer layout |
| Relatórios | Apache POI (Excel) e OpenPDF (PDF) |

```
├── backend/            API Spring Boot (pacotes por domínio)
│   └── src/main/java/br/com/comissao/
│       ├── config/       segurança (JWT do Keycloak), CORS, OpenAPI, relógio
│       ├── usuario/      espelho local do usuário do Keycloak (sincronizado no acesso)
│       ├── parametro/    parâmetros, faixas de comissão/meta e listas configuráveis
│       ├── os/           Ordem de Serviço, itens, parcelas, recebimentos, cancelamento/estorno
│       ├── comissao/     regras de comissão, fechamento mensal, meta semanal
│       ├── financeiro/   contas a receber, inadimplência, painéis
│       ├── producao/     calendário de produção e avisos/urgências
│       ├── posvenda/     entregas e ocorrências
│       ├── importacao/   leitura inteligente do documento da OS (SAFI/OCR/IA)
│       ├── relatorio/    exportação Excel/PDF
│       ├── arquivo/      armazenamento de comprovantes (interface pronta para S3/GCS/Blob)
│       └── auditoria/    log de alterações
│   └── src/main/resources/db/migration/   migrações Flyway (schema + valores iniciais)
├── frontend/           Angular (core/ shared/ layout/ features/)
├── keycloak/           realm "comissao" (papéis, client web, usuários de exemplo)
├── infra/              script de inicialização do PostgreSQL
├── scripts/            dados de demonstração via API
└── docker-compose.yml  ambiente completo
```

## Subir tudo com Docker

```bash
cp .env.example .env          # opcional: ajuste senhas e ANTHROPIC_API_KEY
docker compose up -d --build
```

| Serviço | URL |
|---|---|
| Sistema (Angular) | http://localhost:8081 |
| API + Swagger | http://localhost:8080/swagger-ui.html |
| Keycloak (admin/admin) | http://localhost:8180 |

### Usuários de exemplo (realm `comissao`)

| Login | Senha | Perfil |
|---|---|---|
| `gestor` | `gestor123` | Gestor(a) / Administrador financeiro |
| `producao` | `producao123` | Chefe de Produção |
| `vendedor`, `carla`, `diego` | `vendedor123` | Vendedor |

Troque essas senhas antes de usar em produção. Usuários e perfis são administrados no console do Keycloak: crie
o usuário e atribua o papel do realm `VENDEDOR`, `GESTOR` ou `PRODUCAO`. No primeiro acesso, o usuário passa a
existir também na API (é assim que um vendedor novo aparece para o gestor).

## Desenvolvimento local (sem Docker)

Pré-requisitos: Java 21, Maven 3.9, Node 22, PostgreSQL e Keycloak 26. Tesseract é opcional e serve para o OCR de fotos.

```bash
# 1) Banco
createuser comissao -P && createdb -O comissao comissao        # senha: comissao

# 2) Keycloak na porta 8180 importando o realm
bin/kc.sh start-dev --http-port=8180 --import-realm             # copie keycloak/realm-comissao.json para data/import/

# 3) API (porta 8080). As migrações Flyway criam o schema.
cd backend && mvn spring-boot:run

# 4) Frontend (porta 4200)
cd frontend && npm install && npx ng serve
```

Variáveis da API: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWKS_URI`, `CORS_ORIGINS`,
`STORAGE_DIR`, `ANTHROPIC_API_KEY`, `CLAUDE_MODEL` e `TESSERACT_CMD`.
O frontend lê `config.json` em tempo de execução (URL da API e do Keycloak). No Docker, esse arquivo é gerado a
partir de `API_URL`, `KEYCLOAK_URL`, `KEYCLOAK_REALM` e `KEYCLOAK_CLIENT_ID`, então o mesmo build serve qualquer ambiente.

### Dados de demonstração

Exige um client do Keycloak com *Direct access grants* habilitado (use apenas em desenvolvimento):

```bash
python3 scripts/popular_demo.py --client <client-com-direct-grant>
```

### Testes

```bash
cd backend && mvn test                     # H2 em modo PostgreSQL
cd backend && mvn test -Dspring.datasource.url=jdbc:postgresql://localhost:5432/comissao_teste \
  -Dspring.datasource.username=comissao -Dspring.datasource.password=comissao   # contra PostgreSQL real
cd frontend && npx ng test --watch=false
```

Os testes do backend cobrem as regras de comissão do documento (faixas, teto da empresa, faixa travada no mês da
venda, pagamento só sobre o recebido, Meia Margem, meta semanal), a leitura do PDF/foto no layout SAFI e o fluxo da
API com permissões por perfil.

## Leitura automática do documento da OS

Em **Nova OS**, o vendedor anexa o PDF do SAFI ou uma foto. A API devolve os dados para pré-preencher o formulário
(cliente, itens, totais, forma, valor e data das parcelas, serviços e observações). Os campos preenchidos ficam
destacados para o vendedor conferir antes de salvar. O documento fica anexado à OS.

1. **IA (Claude)**: usada quando `ANTHROPIC_API_KEY` está configurada e o parâmetro correspondente está ligado. Lê PDF
   ou foto em qualquer layout e devolve os dados em um formato validado.
2. **Layout fixo SAFI**: lê o texto do PDF com PDFBox, ou faz OCR com Tesseract em fotos e PDFs digitalizados, e
   localiza os campos pelos rótulos da OS do SAFI. É o caminho usado sem chave de IA ou quando a IA falha.

O sistema avisa quando a soma das parcelas difere do total, quando faltam o cliente ou os itens e quando a previsão
de entrega cai num dia com produção bloqueada.

## Regras de negócio e parametrização

Todos os valores, percentuais e prazos do documento já vêm **cadastrados como padrão**: migrações Flyway `V2` e `V3`,
com o item do documento de onde cada um veio. Todos podem ser **alterados depois** na tela *Parametrização* (perfil Gestor).

| Parâmetro | Valor do documento | Origem |
|---|---|---|
| Salário fixo mensal | R$ 1.500,00 | item 3.1 |
| Faixas de comissão (total vendido no mês) | R$ 58.000 → 1% · R$ 70.000 → 2% · R$ 80.000 → 3% | item 3.1 |
| Meta da semana (bônus fixo) | R$ 20.133 → R$ 100 · R$ 23.810 → R$ 150 · R$ 26.690 → R$ 200 | item 3.1 |
| Teto de faturamento mensal da empresa | R$ 230.000,00 | item 3.1 |
| % para quem não atingiu a meta, empresa acima do teto | 1% | item 3.1 |
| % para quem não atingiu a meta, empresa abaixo do teto | 0% (**não definido no documento — confirmar**) | item 3.2 |
| Fator da Meia Margem | 0,5 (metade da faixa) | item 2.2 |
| Tolerância para alerta de inadimplência | 0 dia | item 7 |
| Fechamento automático na virada do mês | sim | item 3.2 |
| Prazos complementares (aviso de vencimento, entrega sugerida, intervalo entre parcelas) | 3 / 10 / 30 dias | sugestão |
| Listas: formas de pagamento, motivos de estorno, produtos do calendário, tipos de ocorrência | — | itens 2, 4 e 6 |

**Vigência (alterações futuras):** cada alteração vale *a partir de um mês* escolhido.
- Os meses anteriores continuam calculados com as regras da época: cada venda usa a regra vigente no mês da venda.
- Dá para **agendar** uma mudança, por exemplo novas faixas a partir de janeiro. Uma alteração agendada pode ser
  excluída antes de algum mês afetado ser fechado.
- Meses já fechados não aceitam alteração, porque a faixa está travada.
- Ao alterar um mês ainda em aberto, as comissões desse mês são recalculadas na hora.
- O histórico mostra quem alterou, quando e o valor anterior → novo.
- O botão *Restaurar valores do documento* volta tudo aos valores originais, a partir do mês escolhido.

Como as regras são aplicadas:
- **Mês de referência = mês da venda.** No fechamento (automático ou manual), a faixa de cada vendedor fica travada.
- **Comissão gerada por parcela**: *pendente de recebimento* → *recebida (mês aberto)* → *autorizada para pagamento*
  (destacada em vermelho) → *paga*. Sempre usa a faixa travada do mês da venda; comissão paga nunca é recalculada.
- **Meia Margem**: % da faixa × fator. **Meta semanal**: apurada por vendedor e paga separadamente.
- **OS fechada** quando todas as parcelas são recebidas (cada recebimento exige o comprovante anexado).

## Funcionalidades por perfil

- **Vendedor**: painel com a faixa do mês, a meta semanal, as comissões, os alertas de atraso e os avisos da
  produção; criação e edição de OS com leitura do documento; consulta ao calendário de produção; recebimento das
  parcelas com comprovante; cancelamento com justificativa e estorno com comprovante e motivo; pós-venda.
- **Gestor(a)**: painel financeiro com contas a receber por data, por mês e no total, comissões autorizadas, OS
  fechadas e inadimplência; comprovantes; fechamento mensal; pagamento de comissões e metas; parametrização; log de
  alterações; exportação em Excel e PDF.
- **Chefe de Produção**: calendário com bloqueio por dia e produto; avisos gerais (mural) ou vinculados a uma OS,
  produto ou data, com marcação de urgência.
- **Todos**: busca por nome de cliente (vendas, datas de pagamento e comissões pagas), histórico do cliente e
  acesso pelo celular.

## Produção

- Rode o Keycloak em modo `start` com HTTPS e `KC_HOSTNAME` público. Ajuste `redirectUris`/`webOrigins` do client
  `comissao-web` e o `KEYCLOAK_ISSUER_URI` da API.
- Use um PostgreSQL gerenciado apontando `DB_URL`/`DB_USER`/`DB_PASSWORD`. O Flyway aplica as migrações na subida.
- Para guardar comprovantes em nuvem, implemente `ArmazenamentoService` (S3/GCS/Azure Blob) no lugar do disco local.
