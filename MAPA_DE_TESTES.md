# MAPA DE TESTES — amplitude de cobertura do KAIRA

> Levantamento out/2026 (a pedido, depois do bug da âncora do tópico). Objetivo: garantir que a suíte **pegue o tipo de bug que passa por costura entre camadas** — não só o que cada método faz sozinho.
> Estado na data: **50 classes · 248 testes · avaliador 14/14**, tudo verde.
> **Atualização out/2026 (leva das baterias — §5):** **51 classes · 251 testes · avaliador 44/44** — 30 casos de **frase real** colhidos das 2 rodadas de bateria + `SensibilidadeTest`.

---

## 1. Inventário (o que existe hoje)

| Área | Classes | Testes | Base usada |
|---|---|---|---|
| **Fundação / unidade pura** | `NormalizadorTest`, `NormalizadorStemTest`, `CalculosLevenshteinTest`, `CalculosDamerauTest`, `CaminhosTest`, `RegistroTest` | 33 | fixture pura |
| **Arquitetura** | `SemCaminhoHardcodedTest` | 1 | varre `src/` |
| **Dados / curadoria** | `CuradoriaTemasTest`, `CuradoriaConsultoriaTest`, `IntencoesCuradoNaoDegradadoTest`, `GruposSinonimosCuradoTest`, `StopwordsCuradoTest`, `ControlePalavraCorrecaoTest`, `CorrecaoOrdemArquivoTest`, `PalavrasRegeneraAoFaltarTest`, `ControleSinonimoGruposTest` | 40 | **base real** (+ dicionário) |
| **Regressão** | `AvaliadorTest` (dataset congelado, **44 casos** — 14 de brinquedo + 30 de frase real das baterias) | 2 | **base real** |
| **Empatia / segurança** | `SensibilidadeTest` (tema sensível nunca cai em intento alegre; luto/crise/desabafo no intento de acolhimento; pool mínimo) | 3 | **base real** |
| **Matcher / classificador** | `ControleRespostaMelhorIntencaoTest`, `ControleRespostaMatcherUnificadoTest`, `ControleRespostaPesosIdfTest`, `ControleRespostaStemStopwordsTest`, `ControleRespostaSinonimoGruposTest`, `ControleRespostaFaixaConfiancaTest`, `ControleRespostaFraseExataTest`, `ControleRespostaCarregaCacheTest`, `ControleRespostaCacheIdfTest`, `ControlePreAnaliseTest` | 45 | base real + fixtures |
| **Sessão / contexto** | `ControleSessaoTest`, `ControleSessaoPersistenciaTest`, `ControleSessaoNomeTest`, `FatosNovosTest`, `AncoraTopicoTest`, `DesempateContextoTest`, `ContinuacaoTopicoTest`, `IniciativaPropriaTest`, `ApostaPendenteTest` | 59 | base real + fixtures |
| **Resposta / comportamento** | `ControleRespostaFallbackTest`, `ControleRespostaSlotsTest`, `ControleRespostaCriarRespostaTest`, `ControleRespostaAprendizadoTest`, `EnsinoTurbinadoTest`, `AntiRepeticaoTurnoTest`, `SistemaDeHumorTest`, `LogMatchTest`, `ControleConhecimentoTest` | 46 | base real + fixtures |
| **Fluxo fim-a-fim** | `FluxoConversaRealTest`, `EnsinoFluxoTest`, `EnsinoOrganicoTest`, `FluxoConversaReal18Test` | 11 | **base real** + base isolada — o primeiro transcrito do `conversas.log` de 17/10 (10 turnos + 1 variante limpa); o segundo é o loop de 3 turnos do ensino; o terceiro e o `EnsinoOrganicoTest` (#48), o ciclo N->N+1 do aprendizado organico; o quarto e o `FluxoConversaReal18Test`, o uso real de 18/10 transcrito do `conversas.log` |
| **UI** | `ControleFaceTest` | 10 | HTML interno da Face |
| | **51 classes** | **251** | |

> **Nota de rastreio:** o `CuradoriaConsultoriaTest` **não lê os patches de curadoria** — ele filtra a base carregada pelos rótulos de `origem`, ou seja, depende do `intencoes.json` **versionado** conter as levas. Os patches vivem em **`KAIRA/resources/curadoria/`** (material de passagem; entrada do `merge_intencoes_consultoria.py`; nada em `src/`, `test/` ou no app os lê). *(a nota anterior dizia que o teste dependia dos `intencoes-consultoria-*.json`: impreciso — medido out/2026 ao arquivar os patches)*

> **Nota de método:** as contagens acima foram **medidas** (contagem de `@Test` por arquivo), não estimadas. A versão anterior deste inventário abreviava nomes de classe (`MatcherUnificadoTest`, `FallbackTest`, `AvaliadorTest`...) e várias somas não fechavam; se for mexer aqui, meça de novo.

**Leitura rápida:** as camadas de **dado** e **unidade** estão muito bem guardadas (curadoria com matriz de auto-classificação, avaliador congelado, specs por issue). O que falta não é quantidade — é **camada de integração**.

---

## 2. O achado de amplitude (por que o bug passou)

O bug da âncora do tópico (frase **exata** não gravava o tópico → "qual seu favorito" caía em `gostos_ia`) sobreviveu porque:

| Medição | Resultado |
|---|---|
| Classes que exercitam `FormulaResposta.decifrar` (**o pipeline real do turno**) | **2** (`EnsinoOrganicoTest` + `FluxoConversaReal18Test`, #48) |
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
| Ensino (`!`) | ✅ | ✅ (fixture) | ✅ `EnsinoFluxoTest` (fallback → armo → absorvo → frase volta a casar) |
| Slots / resposta composta | ✅ | ✅ | — |
| UI (Face) | ✅ | — | ❌ (não há teste do `decifrar` entregando na Face) |
| **Empatia / segurança** | — | ✅ `SensibilidadeTest` (#5) | — |

---

## 3. Recomendações (amplitude de verdade)

### R1 — Spec de conversa fim-a-fim (`FluxoConversaRealTest`) — **parcialmente entregue**

Dirigir o **pipeline sem cabeça de UI** (`buscaResposta` + `ControleSessao`) sobre a **base real**, com **scripts de 2–3 turnos**, assertando o **observável** (intento escolhido, tópico após o turno, slot renderizado). Casos mínimos:

1. **Tópico (o bug de hoje):** `"assisti um filme ontem a noite"` → tópico `filme` → `"qual seu favorito"` → **filme**. Idem com **música** (o caso de gênero). ✅ **entregue** — o `FluxoConversaRealTest` cobre isto e mais, com os 10 turnos reais do `conversas.log` de 17/10 (inclui a mesma frase genérica resolvendo pra `filme` e pra `musica` conforme o tópico, a troca de assunto legítima pelo classificador, e o degrau de correção de palavra). Ganhou ainda uma **variante limpa** do mesmo turno (`"qual seu filme favorita?"`, sem o erro de digitação) como contraponto: sem erro o match é **direto** no classificador (1.00, 3/3 tokens), com erro cai pra **0.61** pela correção — ou seja, o teste agora prova que a correção só é necessária quando existe erro.
2. **Continuação:** filme → `"conta mais"` → `continuacao_topico` **e** tópico preservado (`filme`). ⬜ aberto.
3. **Ensino engatilhado:** frase sem match → `!` armado → a próxima fala vira resposta no **intent certo** (#33) e **não** polui o tópico. ✅ **entregue** (`EnsinoFluxoTest`, #45) — o loop inteiro fecha com a **mesma string** em 3 turnos: não casa → vai pro `bufferFrases` → é ensinada **pelo buffer** (não por literal nova) → volta a casar. Foi essa spec que **revelou o bug**: o `enviarResposta` chamava `ativarAprendizado()` **antes** de gravar o `bufferFrases`, e `mudarVerificar()` estourava em `btnSinalizar` null — a UI era pre-condição do dado, e quando falhava a frase do usuário se perdia. O que **não** cobre ainda: a afirmação "não polui o tópico" no turno do ensino (o teste isola a base justamente pra não vazar, então não asserta o tópico da sessão).
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

---

## 5. Leva out/2026 — as baterias viraram guarda

As duas rodadas de bateria às cegas (`logs/baterias/`, 811 falas) foram medidas e convertidas em teste onde valia. O mapa dos artefatos está em `logs/baterias/LEIA-ME.txt`.

| Entrou | O que é | Por quê |
|---|---|---|
| **avaliador 14 → 44 casos** | 30 frases **reais** travadas em `test/resources/avaliacao.json`, colhidas por sonda e conferidas **a frio** | o dataset só tinha frase de brinquedo; agora cobre a classe `kaira` ("tchau kaira" → despedida), as âncoras do `obrigado`, luto/crise ("nao vejo a hora de acabar" → `peso_demais`) e os desabafos |
| **`SensibilidadeTest`** (3 testes) | a regra do relatório virou asserção: tema sensível **nunca** cai em `felicidade_usuario`/`comercacao`/`festas`/`piada`/`humor_usuario`; luto/crise/desabafo caem no intento de acolhimento; os pools de acolhimento têm mínimo (4 respostas, 6 exemplos, followups) | empatia/segurança foi a **pior dimensão** (2,5/10) e a regra existia só no placar heurístico (`score_replay.ps1`) |

**Dois achados de harness — a leva pagou por si:**

1. **O avaliador media sem os caches.** `Avaliador.avaliar()` chamava `ControleResposta.resolver` sem `ControlePalavra`/`ControleSinonimo.carregaCache()` — **a mesma classe** do achado de infra do R1 (a regra "carregar os três caches" estava escrita, mas valia só para quem dirigia o `buscaResposta`). Sintoma medido: `"obrigada por ouvir"` resolvia para `"ouvindo musica agora"`; **com** os caches, para `"obrigado por escutar"`. **Corrigido**: o harness carrega os três.
2. **O dataset dependia da ORDEM.** Os casos rodavam em sequência compartilhando a sessão — o tópico do caso anterior vazava no desempate. `"as vezes me sinto sozinha"` resolvia a frio e virava `null` no meio da fila. **Corrigido**: `ControleSessao.reset()` por caso (o javadoc do harness já prometia "consulta pura").

**O que NÃO virou teste (de propósito):** o corpus inteiro. Rodar 811 falas, medir `aposta`/`direto` e contar GRAVE é **ferramenta** (`com.JCODE.tools.ReplayBaterias` + `score_replay.ps1`): mede **deriva agregada**, precisa de base isolada e não deve acoplar a suíte ao `logs/`. Vale o R2: spec afirma comportamento pontual; o benchmark mede o conjunto.

**Como repetir a colheita:** a sonda `ProbeCasos` (TEMP) imprime `entrada → intento → exemplo-resposta`; o caso só entra no dataset se o intento bater com o desejado **e** a frase resolver a frio (com os três caches). Próxima rodada de bateria: destilar → medir → colher os casos novos.
