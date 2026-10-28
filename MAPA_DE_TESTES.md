# MAPA DE TESTES — amplitude de cobertura do KAIRA

> Levantamento out/2026 (a pedido, depois do bug da âncora do tópico). Objetivo: garantir que a suíte **pegue o tipo de bug que passa por costura entre camadas** — não só o que cada método faz sozinho.
> Estado na data: **50 classes · 248 testes · avaliador 14/14**, tudo verde.
> **Atualização out/2026 (leva das baterias — §5):** **51 classes · 251 testes · avaliador 44/44** — 30 casos de **frase real** colhidos das 2 rodadas de bateria + `SensibilidadeTest`.
> **Atualização out/2026 (leva da memória/triagem — §6):** **67 classes · 371 testes · avaliador 44/44 · replay 5,4/0 graves** — as inteligências das issues #50–#63 (triagem, memória v2, opções, molduras/composição, comando seguro, higiene) com specs na base real isolada. Contagens **medidas** (contagem de `@Test` por arquivo).
> **Atualização out/2026 (leva de dados — §6):** **68 classes · 380 testes** — a colisão `fala/fale` (#64, literais do `continuacao_topico` + saída do exemplo do `convite_conversa`), a rota de cálculo honesta (#65, intento `calculo`) e a curadoria do C3 (#66, 15 → 25 entradas com verbete e licença), todas de **dado puro** — `CalculoHonestoTest` e `ControleConhecimentoTest` guardam.

---

## 1. Inventário (o que existe hoje)

| Área | Classes | Testes | Base usada |
|---|---|---|---|
| **Fundação / unidade pura** | `NormalizadorTest`, `NormalizadorStemTest`, `CalculosLevenshteinTest`, `CalculosDamerauTest`, `CaminhosTest`, `RegistroTest` | 33 | fixture pura |
| **Arquitetura** | `SemCaminhoHardcodedTest` | 1 | varre `src/` |
| **Dados / curadoria** | `CuradoriaTemasTest`, `CuradoriaConsultoriaTest`, `IntencoesCuradoNaoDegradadoTest`, `GruposSinonimosCuradoTest`, `StopwordsCuradoTest`, `ControlePalavraCorrecaoTest`, `CorrecaoOrdemArquivoTest`, `PalavrasRegeneraAoFaltarTest`, `ControleSinonimoGruposTest` | 40 | **base real** (+ dicionário) |
| **Regressão** | `AvaliadorTest` (dataset congelado, **44 casos** — 14 de brinquedo + 30 de frase real das baterias) | 2 | **base real** |
| **Empatia / segurança** | `SensibilidadeTest` (tema sensível nunca cai em intento alegre; luto/crise/desabafo no intento de acolhimento; pool mínimo) | 3 | **base real** |
| **Matcher / classificador** | `ControleRespostaMelhorIntencaoTest`, `ControleRespostaMatcherUnificadoTest`, `ControleRespostaPesosIdfTest`, `ControleRespostaStemStopwordsTest`, `ControleRespostaSinonimoGruposTest`, `ControleRespostaFaixaConfiancaTest`, `ControleRespostaFraseExataTest`, `ControleRespostaCarregaCacheTest`, `ControleRespostaCacheIdfTest`, `ControlePreAnaliseTest`, **`GateTokensPesoTest` (#49)** | 50 | base real + fixtures |
| **Sessão / contexto** | `ControleSessaoTest`, `ControleSessaoPersistenciaTest`, `ControleSessaoNomeTest`, `FatosNovosTest`, `AncoraTopicoTest`, `DesempateContextoTest`, `ContinuacaoTopicoTest`, `IniciativaPropriaTest`, `ApostaPendenteTest`, **`FatosModeloV2Test` (#56), `FatosSessaoV2Test` (#58), `MaquinaContextoTest` (#41 reaberta)** | 83 | base real + fixtures |
| **Resposta / comportamento** | `ControleRespostaFallbackTest`, `ControleRespostaSlotsTest`, `ControleRespostaCriarRespostaTest`, `ControleRespostaAprendizadoTest`, `EnsinoTurbinadoTest`, `AntiRepeticaoTurnoTest`, `SistemaDeHumorTest`, `LogMatchTest`, `ControleConhecimentoTest` + a **leva da memória**: `TriagemPerguntaTest`, `IntegridadeFatoTest`, `TriagemVariacoesTest`, `EnsinoSegredoTest`, `ExplicaMelhorTest`, `PortaUsuarioPolimentoTest`, `OpcoesMemoriaTest`, `EnsinoNegativoTest`, `MoldurasSorteadasTest`, `ComposicaoRespostaTest`, `ComandoSeguroTest`, `HigieneBateriasTest` + **`CalculoHonestoTest` (#65)** | 145 | base real + fixtures |
| **Fluxo fim-a-fim** | `FluxoConversaRealTest`, `EnsinoFluxoTest`, `EnsinoOrganicoTest`, `FluxoConversaReal18Test` | 12 | **base real** + base isolada |
| **UI** | `ControleFaceTest` | 10 | HTML interno da Face |
| | **68 classes** | **380** | |

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
| Continuação (`continuacao_topico`) | ✅ | ✅ | ✅ `ExplicaMelhorTest` (#37/#54 — "conta mais"/"explica melhor" sobre o tópico com o dicionário) |
| Iniciativa (followups) | ✅ | ✅ | ✅ `MaquinaContextoTest` (#41 — a pergunta pendente N→N+1) |
| Humor | ✅ | ✅ | ⚠ (3 elogios em sequência ✅ — esse tem!) |
| Ensino (`!`) | ✅ | ✅ (fixture) | ✅ `EnsinoFluxoTest`/`EnsinoOrganicoTest` (o ciclo N→N+1) — a cláusula "não polui o tópico" **não** é assertada por design (o teste isola a base) |
| Slots / resposta composta | ✅ | ✅ | — |
| **Triagem / memória (portas)** | ✅ | ✅ `TriagemPerguntaTest`/`TriagemVariacoesTest` | ✅ `FatosModeloV2Test`/`FatosSessaoV2Test` (relações + não-contradição) |
| **Fluxo de opções** | ✅ | ✅ | ✅ `OpcoesMemoriaTest` (estado N→N+1: convite → lista → grava) |
| **Molduras / composição** | ✅ | ✅ | ✅ `MoldurasSorteadasTest`/`ComposicaoRespostaTest` (variedade + anti-repetição) |
| **Comando seguro (#62)** | ✅ `ComandoSeguroTest` | ✅ | — |
| **Rota de cálculo (#65)** | ✅ `CalculoHonestoTest` | ✅ | — (dado puro; número fora da base cai na aposta sem ancorar tópico) |
| **Dicionário C3 (#66)** | ✅ `ControleConhecimentoTest` (7) | ✅ | — (dado puro; o resumo é falado no "conta mais") |
| UI (Face) | ✅ | ✅ (as specs da leva dirigem o `decifrar` e assertam a `ControleFace.resposta`) | ⚠ (o render Swing não é coberto) |
| **Empatia / segurança** | — | ✅ `SensibilidadeTest` (#5) | — |

---

## 3. Recomendações (amplitude de verdade)

### R1 — Spec de conversa fim-a-fim (`FluxoConversaRealTest`) — **parcialmente entregue**

Dirigir o **pipeline sem cabeça de UI** (`buscaResposta` + `ControleSessao`) sobre a **base real**, com **scripts de 2–3 turnos**, assertando o **observável** (intento escolhido, tópico após o turno, slot renderizado). Casos mínimos:

1. **Tópico (o bug de hoje):** `"assisti um filme ontem a noite"` → tópico `filme` → `"qual seu favorito"` → **filme**. Idem com **música** (o caso de gênero). ✅ **entregue** — o `FluxoConversaRealTest` cobre isto e mais, com os 10 turnos reais do `conversas.log` de 17/10 (inclui a mesma frase genérica resolvendo pra `filme` e pra `musica` conforme o tópico, a troca de assunto legítima pelo classificador, e o degrau de correção de palavra). Ganhou ainda uma **variante limpa** do mesmo turno (`"qual seu filme favorita?"`, sem o erro de digitação) como contraponto: sem erro o match é **direto** no classificador (1.00, 3/3 tokens), com erro cai pra **0.61** pela correção — ou seja, o teste agora prova que a correção só é necessária quando existe erro.
2. **Continuação:** filme → `"conta mais"` → `continuacao_topico` **e** tópico preservado (`filme`). ✅ **entregue** — o `ExplicaMelhorTest` (#54) e o `ControleConhecimentoTest` (#37) cobrem o "conta mais"/"explica melhor" sobre o tópico com o dicionário C3.
3. **Ensino engatilhado:** frase sem match → `!` armado → a próxima fala vira resposta no **intent certo** (#33) e **não** polui o tópico. ✅ **entregue** (`EnsinoFluxoTest`, #45) — o loop inteiro fecha com a **mesma string** em 3 turnos: não casa → vai pro `bufferFrases` → é ensinada **pelo buffer** (não por literal nova) → volta a casar. Foi essa spec que **revelou o bug**: o `enviarResposta` chamava `ativarAprendizado()` **antes** de gravar o `bufferFrases`, e `mudarVerificar()` estourava em `btnSinalizar` null — a UI era pre-condição do dado, e quando falhava a frase do usuário se perdia. **O que não é assertado por design:** a cláusula "não polui o tópico" — o teste isola a base e reseta a sessão justamente pra não vazar; o comportamento real fica medido no replay.
4. **Nome:** `"meu nome e X"` → resposta de nome, turno encerra sem passar pelo classificador. ✅ (a janela do nome é E2E no `HigieneBateriasTest`, com blocklist de "tudo"/saudações/profissões; o padrão explícito segue em unidade no `ControleSessaoNomeTest`).
5. **Aposta pendente (#39):** frase de faixa média → aposta + confirmação/correção no turno seguinte. ⬜ aberto.
6. **Humor:** 3 elogios → faixa alta no pool (versão E2E do que o `SistemaDeHumorTest` já cobre em unidade). ⬜ aberto.

> Cada **bug visto no uso real** vira caso deste spec (o log do #40 ajuda a extrair o par de turnos). Cultura: **bug → spec E2E** antes do fix.

**Achado de infra do próprio R1:** os testes que dirigem o `buscaResposta` precisam carregar os **três** caches que o app carrega no `ControleCache.carregaCacheGeral()` (`ControleResposta`, `ControlePalavra`, `ControleSinonimo`). Sem o `ControleSinonimo`, o `mesmaPalavra` cai no fallback `equalsIgnoreCase` e o teste roda numa configuração que **não é a de produção** — isso mudou o desfecho de um turno de verdade no primeiro dia (ver `FluxoConversaRealTest.prepara`).

### R2 — Regra de camada (convenção)

> Spec que afirma **comportamento visível** ("o tópico ancora", "a resposta sai", "a aposta confirma") **passa pelo entry point mais próximo do app** (`buscaResposta`/`lerResposta`), **não** pelo helper interno (`melhorIntencaoDetalhado`). O helper pode ter spec próprio (unidade), mas a afirmação de comportamento vai na porta.

Isso é exatamente o que separou o `AncoraTopicoTest` (helper) do bug (porta).

### R3 — Seams para subir até o `decifrar` (opcional) — **superado em parte**

O `decifrar` **agora é o entry point dos E2E**: desde o #45 (a UI deixou de ser pré-condição) e o #48, as specs da leva dirigem o `decifrar` direto e assertam a resposta na `ControleFace.resposta` (o observável do bot) — não há mais o "a cascata inteira não tem teste". O que resta do seam:
- o **render Swing** (a bolha na Face) — coberto só no `ControleFaceTest` (o HTML interno);
- o **DESLIGAR** (`System.exit`) — continua fora (o comando foi neutralizado no #62, então nem é mais necessário).

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

---

## 6. Leva da memória/triagem (out/2026) — as baterias de triagem viraram guarda

As baterias T01/T02 da triagem (`logs/baterias/triagem/` + RELATORIO §TRIAGEM) expuseram o bug de integridade do fato e o mapa de variações — convertidos em specs ao longo das issues #50–#63:

| Entrou | O que é | Por quê |
|---|---|---|
| **`TriagemPerguntaTest` (7)** | as portas DELA/USUÁRIO na base real isolada | a triagem da #50 provada ponta a ponta (hit/miss/ensino) |
| **`TriagemVariacoesTest` (15)** | o T02 virou fixture: plural/fillers/ordem invertida/misses honestos/feminino | as variantes que quebravam a porta |
| **`IntegridadeFatoTest` (9)** | pergunta não vira fato; sem sobrescrita; feminino | o BUG CRÍTICO do T02 ("meu time e qual mesmo?") |
| **`FatosModeloV2Test` (8) + `FatosSessaoV2Test` (10)** | o modelo de relações `gosta`/`nao_gosta` dos dois lados + a não-contradição | a evolução do modelo de fatos |
| **`MaquinaContextoTest` (6)** | a pergunta pendente N→N+1 (o simétrico do #48) | o sequestro de turno do followup |
| **`OpcoesMemoriaTest` (8)** | o fluxo convite→lista→sorteio | a parte interativa da memória |
| **`EnsinoSegredoTest`/`EnsinoNegativoTest`/`HigieneBateriasTest`** | segredo, negativa, nome/gênero/textão | a leva de recs das baterias |
| **`MoldurasSorteadasTest`/`ComposicaoRespostaTest`** | a Ideia 2 (variedade + composição) | a repetição intra-conversa |
| **`ContinuacaoTopicoTest` (+2 literais, #64)** | a colisão `fala/fale` nos dois caminhos (exato e fuzzy) | "fale mais sobre isso" virava `convite_conversa` |
| **`CalculoHonestoTest` (6, #65)** | a rota de cálculo honesta (literais do corpus + variantes + aposta) | "quanto é 7 vezes 8" pedia ensino |
| **`ControleConhecimentoTest` (+2, #66)** | 10 temas novos no C3 com verbete + licença (família, amigos, saúde, namoro, série…) | "conta mais" sem substância nesses temas |

**A régua do ciclo (as 4 portas):** spec da issue → suíte (**68 classes**) → avaliador (**44/44**) → **replay do corpus** (`ReplayBaterias` + `score_replay.ps1`; alvo **GERAL ≥ 5,2 / GRAVE = 0**). O replay tem a régua documentada (BOM=1 / PARCIAL=0,3 / RUIM=0 / GRAVE=−1; `triagem`=BOM) e o proxy **não vê o texto** — mede **direção**, não valor absoluto.
