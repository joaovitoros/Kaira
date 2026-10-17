# MAPA DE TESTES — amplitude de cobertura do KAIRA

> Levantamento out/2026 (a pedido, depois do bug da âncora do tópico). Objetivo: garantir que a suíte **pegue o tipo de bug que passa por costura entre camadas** — não só o que cada método faz sozinho.
> Estado na data: **43 classes · 224 testes · avaliador 14/14**, tudo verde.

---

## 1. Inventário (o que existe hoje)

| Área | Classes | Testes | Base usada |
|---|---|---|---|
| **Fundação / unidade pura** | `NormalizadorTest`, `NormalizadorStemTest`, `CalculosLevenshteinTest`, `CaminhosTest`, `RegistroTest` | 30 | fixture pura |
| **Arquitetura** | `SemCaminhoHardcodedTest` | 1 | varre `src/` |
| **Dados / curadoria** | `CuradoriaTemasTest`, `CuradoriaConsultoriaTest`, `IntencoesCuradoNaoDegradadoTest`, `GruposSinonimosCuradoTest`, `StopwordsCuradoTest`, `ControlePalavraCorrecaoTest` | 29 | **base real** (+ dicionário) |
| **Regressão** | `AvaliadorTest` (dataset congelado, 14 casos) | 2 | **base real** |
| **Matcher / classificador** | `ControleRespostaMelhorIntencaoTest`, `MatcherUnificadoTest`, `PesosIdfTest`, `StemStopwordsTest`, `SinonimoGruposTest`, `FaixaConfiancaTest`, `FraseExataTest`, `PreAnaliseTest`, `CarregaCacheTest` | 41 | base real + fixtures |
| **Sessão / contexto** | `ControleSessaoTest`, `SessaoPersistenciaTest`, `ControleSessaoNomeTest`, `FatosNovosTest`, `AncoraTopicoTest`, `DesempateContextoTest`, `ContinuacaoTopicoTest`, `IniciativaPropriaTest`, `ApostaPendenteTest` | 54 | base real + fixtures |
| **Resposta / comportamento** | `FallbackTest`, `SlotsTest`, `CriarRespostaTest`, `AprendizadoTest`, `EnsinoTurbinadoTest`, `AntiRepeticaoTurnoTest`, `SistemaDeHumorTest`, `LogMatchTest`, `ControleConhecimentoTest` | 47 | base real + fixtures |
| **UI** | `ControleFaceTest` | 10 | HTML interno da Face |

> **Nota de rastreio:** `CuradoriaConsultoriaTest` (4 testes) roda na suíte local mas **não está commitado** — depende dos `KAIRA/resources/intencoes-consultoria-*.json` (também não commitados; leva de dados em curadoria). Ao clonar o repo limpo, a suíte tem **42 classes / 220 testes** até a leva de consultoria ser commitada.

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
| **Tópico / desempate por contexto** | ✅ | ✅ | ❌ **nenhum** |
| Continuação (`continuacao_topico`) | ✅ | ✅ | ⚠ (testa o turno, não a sequência com tema anterior real) |
| Iniciativa (followups) | ✅ | ✅ | ⚠ |
| Humor | ✅ | ✅ | ⚠ (3 elogios em sequência ✅ — esse tem!) |
| Ensino (`!`) | ✅ | ✅ (fixture) | ❌ (fallback→resposta não é testado ponta a ponta) |
| Slots / resposta composta | ✅ | ✅ | — |
| UI (Face) | ✅ | — | ❌ (não há teste do `decifrar` entregando na Face) |

---

## 3. Recomendações (amplitude de verdade)

### R1 — Spec de conversa fim-a-fim (`ConversaFimAFimTest`) — **a peça que falta**

Dirigir o **pipeline sem cabeça de UI** (`buscaResposta` + `ControleSessao`) sobre a **base real**, com **scripts de 2–3 turnos**, assertando o **observável** (intento escolhido, tópico após o turno, slot renderizado). Casos mínimos:

1. **Tópico (o bug de hoje):** `"assisti um filme ontem a noite"` → tópico `filme` → `"qual seu favorito"` → **filme**. Idem com **música** (o caso de gênero).
2. **Continuação:** filme → `"conta mais"` → `continuacao_topico` **e** tópico preservado (`filme`).
3. **Ensino engatilhado:** frase sem match → `!` armado → a próxima fala vira resposta no **intent certo** (#33) e **não** polui o tópico.
4. **Nome:** `"meu nome e X"` → resposta de nome, turno encerra sem passar pelo classificador.
5. **Aposta pendente (#39):** frase de faixa média → aposta + confirmação/correção no turno seguinte.
6. **Humor:** 3 elogios → faixa alta no pool (versão E2E do que o `SistemaDeHumorTest` já cobre em unidade).

> Cada **bug visto no uso real** vira caso deste spec (o log do #40 ajuda a extrair o par de turnos). Cultura: **bug → spec E2E** antes do fix.

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
