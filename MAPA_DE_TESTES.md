# MAPA DE TESTES — amplitude de cobertura do KAIRA

> Levantamento out/2026 (a pedido, depois do bug da âncora do tópico). Objetivo: garantir que a suíte **pegue o tipo de bug que passa por costura entre camadas** — não só o que cada método faz sozinho.
> Estado na data: **44 classes · 228 testes · avaliador 14/14**, tudo verde.

---

## 1. Inventário (o que existe hoje)

| Área | Classes | Testes | Base usada |
|---|---|---|---|
| **Fundação / unidade pura** | `NormalizadorTest`, `NormalizadorStemTest`, `CalculosLevenshteinTest`, `CaminhosTest`, `RegistroTest` | 30 | fixture pura |
| **Arquitetura** | `SemCaminhoHardcodedTest` | 1 | varre `src/` |
| **Dados / curadoria** | `CuradoriaTemasTest`, `CuradoriaConsultoriaTest`, `IntencoesCuradoNaoDegradadoTest`, `GruposSinonimosCuradoTest`, `StopwordsCuradoTest`, `ControlePalavraCorrecaoTest`, `ControleSinonimoGruposTest` | 36 | **base real** (+ dicionário) |
| **Regressão** | `AvaliadorTest` (dataset congelado, 14 casos) | 2 | **base real** |
| **Matcher / classificador** | `ControleRespostaMelhorIntencaoTest`, `ControleRespostaMatcherUnificadoTest`, `ControleRespostaPesosIdfTest`, `ControleRespostaStemStopwordsTest`, `ControleRespostaSinonimoGruposTest`, `ControleRespostaFaixaConfiancaTest`, `ControleRespostaFraseExataTest`, `ControleRespostaCarregaCacheTest`, `ControleRespostaCacheIdfTest`, `ControlePreAnaliseTest` | 45 | base real + fixtures |
| **Sessão / contexto** | `ControleSessaoTest`, `ControleSessaoPersistenciaTest`, `ControleSessaoNomeTest`, `FatosNovosTest`, `AncoraTopicoTest`, `DesempateContextoTest`, `ContinuacaoTopicoTest`, `IniciativaPropriaTest`, `ApostaPendenteTest` | 57 | base real + fixtures |
| **Resposta / comportamento** | `ControleRespostaFallbackTest`, `ControleRespostaSlotsTest`, `ControleRespostaCriarRespostaTest`, `ControleRespostaAprendizadoTest`, `EnsinoTurbinadoTest`, `AntiRepeticaoTurnoTest`, `SistemaDeHumorTest`, `LogMatchTest`, `ControleConhecimentoTest` | 46 | base real + fixtures |
| **Fluxo fim-a-fim** | `FluxoConversaRealTest` | 1 | **base real**, transcrito do `conversas.log` de 17/10 (10 turnos + 1 variante limpa) |
| **UI** | `ControleFaceTest` | 10 | HTML interno da Face |
| | **44 classes** | **228** | |

> **Nota de rastreio:** `CuradoriaConsultoriaTest` (4 testes) roda na suíte local mas **não está commitado** — depende dos `KAIRA/resources/intencoes-consultoria-*.json` (também não commitados; leva de dados em curadoria). Ao clonar o repo limpo, a suíte tem **43 classes / 224 testes** até a leva de consultoria ser commitada.

> **Nota de método:** as contagens acima foram **medidas** (contagem de `@Test` por arquivo), não estimadas. A versão anterior deste inventário abreviava nomes de classe (`MatcherUnificadoTest`, `FallbackTest`, `AvaliadorTest`...) e várias somas não fechavam; se for mexer aqui, meça de novo.

**Leitura rápida:** as camadas de **dado** e **unidade** estão muito bem guardadas (curadoria com matriz de auto-classificação, avaliador congelado, specs por issue). O que falta não é quantidade — é **camada de integração**.

---

## 2. O achado de amplitude (por que o bug passou)

O bug da âncora do tópico (frase **exata** não gravava o tópico → "qual seu favorito" caía em `gostos_ia`) sobreviveu porque:

| Medição | Resultado |
|---|---|
| Classes que exercitam `FormulaResposta.decifrar` (**o pipeline real do turno**) | **0** |
| Classes que exercitam `buscaResposta` (a costura exato↔fuzzy) | **4** (`DesempateContextoTest`, `FaixaConfiancaTest`, `LogMatchTest`, `MatcherUnificadoTest`) |
| `AncoraTopicoTest` (#38), que **afirma** "o tópico ancora" | chama `melhorIntencaoDetalhado` **direto** — nunca `buscaResposta` |

**O padrão que falha:** o spec testa a **camada interna** (o helper que ancora), enquanto o app passa pela **porta** (`buscaResposta`), que tem dois ramos (igualdade literal × fuzzy) — e o bug estava justamente **em ter o segundo ramo sem a âncora**. Nenhum teste atravessava a porta.

**Segundo padrão que falha (o do gênero):** features de **ciclo de vida** (tópico no turno N → usado no turno N+1; humor acumulando; ensino engatilhado) são testadas **em unidades isoladas** — ninguém monta os **dois turnos em sequência**. O sintoma do uso real ("conversa de filme, depois a genérica") é exatamente um caso de dois turnos.

### Matriz de amplitude por área

| Área | Unidade | Base real | Ciclo de vida (N→N+1) |
|---|---|---|---|
| Normalizador/stem/stopwords | ✅ | ✅ | — |
| Classificador (matcher) | ✅ | ✅ | ⚠ (frases soltas, não em sequência) |
| Curadoria de dados | ✅ matriz | ✅ | — |
| **Tópico / desempate por contexto** | ✅ | ✅ | ✅ `FluxoConversaRealTest` (11 turnos, log real) |
| Continuação (`continuacao_topico`) | ✅ | ✅ | ⚠ (testa o turno, não a sequência com tema anterior real) |
| Iniciativa (followups) | ✅ | ✅ | ⚠ |
| Humor | ✅ | ✅ | ⚠ (3 elogios em sequência ✅ — esse tem!) |
| Ensino (`!`) | ✅ | ✅ (fixture) | ❌ (fallback→resposta não é testado ponta a ponta) |
| Slots / resposta composta | ✅ | ✅ | — |
| UI (Face) | ✅ | — | ❌ (não há teste do `decifrar` entregando na Face) |

---

## 3. Recomendações (amplitude de verdade)

### R1 — Spec de conversa fim-a-fim (`FluxoConversaRealTest`) — **parcialmente entregue**

Dirigir o **pipeline sem cabeça de UI** (`buscaResposta` + `ControleSessao`) sobre a **base real**, com **scripts de 2–3 turnos**, assertando o **observável** (intento escolhido, tópico após o turno, slot renderizado). Casos mínimos:

1. **Tópico (o bug de hoje):** `"assisti um filme ontem a noite"` → tópico `filme` → `"qual seu favorito"` → **filme**. Idem com **música** (o caso de gênero). ✅ **entregue** — o `FluxoConversaRealTest` cobre isto e mais, com os 10 turnos reais do `conversas.log` de 17/10 (inclui a mesma frase genérica resolvendo pra `filme` e pra `musica` conforme o tópico, a troca de assunto legítima pelo classificador, e o degrau de correção de palavra). Ganhou ainda uma **variante limpa** do mesmo turno (`"qual seu filme favorita?"`, sem o erro de digitação) como contraponto: sem erro o match é **direto** no classificador (1.00, 3/3 tokens), com erro cai pra **0.61** pela correção — ou seja, o teste agora prova que a correção só é necessária quando existe erro.
2. **Continuação:** filme → `"conta mais"` → `continuacao_topico` **e** tópico preservado (`filme`). ⬜ aberto.
3. **Ensino engatilhado:** frase sem match → `!` armado → a próxima fala vira resposta no **intent certo** (#33) e **não** polui o tópico. ⬜ aberto.
4. **Nome:** `"meu nome e X"` → resposta de nome, turno encerra sem passar pelo classificador. ✅ **parcial** — a captura do nome na janela da pergunta está no fluxo; o turno `"meu nome e X"` (padrão explícito, não o nome pelado) não.
5. **Aposta pendente (#39):** frase de faixa média → aposta + confirmação/correção no turno seguinte. ⬜ aberto.
6. **Humor:** 3 elogios → faixa alta no pool (versão E2E do que o `SistemaDeHumorTest` já cobre em unidade). ⬜ aberto.

> Cada **bug visto no uso real** vira caso deste spec (o log do #40 ajuda a extrair o par de turnos). Cultura: **bug → spec E2E** antes do fix.

**Achado de infra do próprio R1:** os testes que dirigem o `buscaResposta` precisam carregar os **três** caches que o app carrega no `ControleCache.carregaCacheGeral()` (`ControleResposta`, `ControlePalavra`, `ControleSinonimo`). Sem o `ControleSinonimo`, o `mesmaPalavra` cai no fallback `equalsIgnoreCase` e o teste roda numa configuração que **não é a de produção** — isso mudou o desfecho de um turno de verdade no primeiro dia (ver `FluxoConversaRealTest.prepara`).

### R2 — Regra de camada (convenção)

> Spec que afirma **comportamento visível** ("o tópico ancora", "a resposta sai", "a aposta confirma") **passa pelo entry point mais próximo do app** (`buscaResposta`/`lerResposta`), **não** pelo helper interno (`melhorIntencaoDetalhado`). O helper pode ter spec próprio (unidade), mas a afirmação de comportamento vai na porta.

Isso é exatamente o que separou o `AncoraTopicoTest` (helper) do bug (porta).

### R3 — Seams para subir até o `decifrar` (opcional)

Hoje `decifrar` não é testável ponta a ponta (chama `ControleFace`, `System.exit` no DESLIGAR, `Math.random`). O projeto **já criou seams** (o `semeador` do #35) — estender:
- um **observador de Face** (stub que captura `recebeResp`) para assertar a fala final;
- **modo sem `System.exit`** para o comando DESLIGAR;
- aí o E2E pode começar em `decifrar` e cobrir a **cascata inteira** (comando → nome → resposta → correção → aposta → fallback→ensino), que hoje não tem nenhum teste.

### R4 — Fixtures continuam boas (mas com 1 caso de base real)

Fixtures são rápidas e isoladas — manter. Porém os **empates reais** (IDF + sinônimos do dump) só aparecem na base real: todo spec de feature deve ter **≥1 caso com a base real**. A maioria já faz — a regra só formaliza.

---

## 4. Como encaixa no fluxo do projeto

1. `ConversaFimAFimTest` nasce **vermelho** nos casos 1 e 3 (tópico no match exato; ensino ponta a ponta) — vira o guard do bug de hoje e do #35/#39.
2. Fecha no fluxo de sempre: **issue → spec vermelho → receita → verde**.
3. O `RESUMO_FUNCIONAMENTO.md` §12 ganha a **R2** como convenção (spec de comportamento vai na porta).
4. A matriz da §2 é viva: cada bug novo marca um ❌ onde a suíte não olhava.
