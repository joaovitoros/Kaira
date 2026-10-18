# KAIRA — Plano de Evolução

> KAIRA — assistente de conversa
>
> Versão do documento: **1.0** — Outubro/2026
> Escopo: diagnóstico da base atual + roadmap completo até conversação livre
> Estado do projeto na data deste documento: v0.0.1, ~250 frases, 102 sinônimos, 456 palavras registradas
>
> **Atualização de progresso (out/2026):** tarefas já entregues estão com `[x]` e nota com issue/commit. **Fase 0** concluída (menos G6 e Gradle); **Fase 1** concluída (§5.1–§5.7 — a última, frame+slot, na issue #18; §7.3 parcial na #17 e descartados à parte); **Fase 2 CONCLUÍDA** (§6.1 na #19+#20, §6.2 na #1+#22, §6.3 na #21 — os 3 critérios de aceite da fase marcados); **Fases 3–5** intactas. Issues #1–#22 fechadas (github.com/joaovitoros/Kaira), 55 commits em `master`, avaliador de regressão 14/14 com 0 gaps. **Trabalhos laterais entregues por commits diretos (sem issue):** tabelas de frames/fatos ampliadas, curadoria contínua da base (36→54 intenções, ciclos A/B), chat em balões, `conversas.log` + simulador de conversa (§6.2 opcional) e specs `RegistroTest`/`CuradoriaTemasTest`. **Ideias em estudo** anotadas na §12.

---

## Como usar este documento

- As fases estão em ordem de prioridade: **quanto menor o número, maior o ganho por esforço gasto**.
- Cada fase tem **objetivo, motivo, tarefas (com referências aos arquivos reais), critérios de aceite e estimativa**.
- Use os checklists (`- [ ]`) para acompanhar o progresso — no GitHub/IntelliJ eles viram caixinhas clicáveis.
- Nada aqui exige jogar fora o código atual: **a Fase 5 (LLM) reaproveita tudo das Fases 1–3** como camada de "skills".

### Sumário

1. [Onde estamos hoje](#1-onde-estamos-hoje)
2. [Diagnóstico técnico](#2-diagnóstico-técnico)
3. [O plano — visão geral](#3-o-plano--visão-geral)
4. [Fase 0 — Fundação](#4-fase-0--fundação)
5. [Fase 1 — Intenções e classificação](#5-fase-1--intenções-e-classificação)
6. [Fase 2 — Memória e contexto](#6-fase-2--memória-e-contexto)
7. [Fase 3 — Personalidade viva](#7-fase-3--personalidade-viva)
8. [Fase 4 — Geração própria (opcional)](#8-fase-4--geração-própria-opcional)
9. [Fase 5 — IA de verdade (LLM)](#9-fase-5--ia-de-verdade-llm)
10. [Top 5 ações imediatas](#10-top-5-ações-imediatas)
11. [Alimentando a base de dados](#11-alimentando-a-base-de-dados)
12. [Ideias em estudo (backlog)](#12-ideias-em-estudo-backlog)
13. [Apêndice A — Esquema JSON proposto](#apêndice-a--esquema-json-proposto)
14. [Apêndice B — Exemplos de código](#apêndice-b--exemplos-de-código)
15. [Apêndice C — Esforço estimado](#apêndice-c--esforço-estimado)
16. [Apêndice D — Checklist de decisões](#apêndice-d--checklist-de-decisões)

---

## 1. Onde estamos hoje

### 1.1 Estrutura do projeto

```
Projeto/
├── src/com/JCODE/
│   ├── Main.java                      → carrega caches, abre a janela
│   ├── view/Face.java                 → interface Swing (chat)
│   ├── controller/
│   │   ├── ControleCache.java         → bootstrap geral (1ª execução cria arquivos)
│   │   ├── ControleKaira.java          → dados gerais (nome/versão) em kaira.json
│   │   ├── ControleResposta.java      → match exato + dados de respostas.json
│   │   ├── ControleCorrecao.java      → correção de palavra/frase + sinônimos
│   │   ├── ControlePalavra.java       → dicionário palavras.json
│   │   ├── ControleSinonimo.java      → sinonimos.json
│   │   ├── ControleComandos.java      → comando "Executar"
│   │   ├── ControleFace.java          → ponte UI ↔ lógica + modo ensino
│   │   └── FormulaResposta.java       → pipeline principal (decifrar)
│   ├── domain/                        → POJOs (KAIRA, Falas, Resposta, Sinonimo...)
│   └── util/Calculos.java             → porcentagens de similaridade
├── KAIRA/resources/                    → respostas.json, sinonimos.json, palavras.json, kaira.json
├── externallibs/                      → jackson 2.x, commons-io
└── out/                               → build IntelliJ
```

### 1.2 Fluxo atual de uma mensagem

```
Usuário digita (Face.java)
        │
        ▼
ControleFace.MandarFrase()
        │
        ▼
FormulaResposta.decifrar(fala)
        │
        ├─ 1º ControleComandos.validaComandos()      → "Executar X" → Runtime.exec(...)   ⚠ furo de segurança
        │
        ├─ 2º ControleResposta.lerResposta()         → match EXATO (equalsIgnoreCase) contra respostas.json
        │
        ├─ 3º ControleCorrecao.corrigirPalavras()    → corrige cada palavra via palavras.json, refaz match exato
        │
        ├─ 4º ControleCorrecao.corrigirFrases()      → match fuzzy de frase (60%/67%), refaz match exato
        │
        └─ 5º ControleCorrecao.procuraSinonimo()     → troca palavras por sinônimo ALEATÓRIO, refaz match exato
                │
                ▼
FormulaResposta.enviarResposta()
        ├── achou?  → mostra a resposta
        └── null?   → "Não sei como responder a isso"
                     + modo ensino ativado (botão "!" fica verde)
                     → próxima mensagem do usuário vira a resposta ensinada
                     → salva em respostas.json (assunto: "Auto-Aprendizado")
```

### 1.3 Números atuais

| Recurso | Quantidade | Arquivo |
|---|---|---|
| Frases com resposta | 250 | `KAIRA/resources/respostas.json` |
| Sinônimos | 102 | `KAIRA/resources/sinonimos.json` |
| Palavras no dicionário | 456 | `KAIRA/resources/palavras.json` |
| Assuntos distintos | ~15 | (cumprimento, status, relacionamento, gostos...) |

---

## 2. Diagnóstico técnico

### 2.1 O que já está bom (e deve ser mantido)

| Ponto | Onde |
|---|---|
| Pipeline em cascata com fallbacks | `FormulaResposta.decifrar()` — a *ideia* está certa, só a implementação trava |
| Auto-aprendizado com o usuário | `ControleFace` + `FormulaResposta.manterResposta()` — coleta dados reais de graça |
| Separação view / controller / domain | pacotes do projeto |
| Persistência em JSON com Jackson | controllers |
| Respostas múltiplas por frase (variedade) | `escolheMultiRespostas()` |
| Controle de humor "esboçado" | `comandosResposta()` já tem o gancho para assunto "status" |

### 2.2 Gargalos (em ordem de impacto)

> **Status (out/2026):** G1 resolvido · G2 resolvido · G3 resolvido · G4 resolvido · G5 resolvido · G6 pendente · G7 resolvido · G8 resolvido · G9 parcial · G10 parcial

| Gargalo | Status | Resolvido em |
|---|---|---|
| G1 frase-exata | resolvido | matcher por cobertura ponderada por IDF sobre intenções (issues #5, #6, #11, #14) |
| G2 sinônimos roleta | resolvido | grupos no matcher (issue #9); procuraSinonimo deletado; geração com guarda de stopword (#13) |
| G3 sem normalização | resolvido | Normalizador.texto + stem de 12 regras + 54 stopwords nos dois lados (issues #2, #12) |
| G4 sem memória | resolvido | sessão em memória + `sessao.json` persistente (issues #19/#20) |
| G5 UI apaga conversa | resolvido | histórico acumula via append (issue #1); separador de turnos e auto-scroll entregues (issue #22) |
| G6 Runtime.exec | pendente | backlog de segurança |
| G7 caminhos relativos | resolvido | Caminhos.recurso() resolve a base de qualquer diretório (issue #4, CaminhosTest) |
| G8 similaridades frágeis | resolvido | Levenshtein + frase palavra-a-palavra (issue #15) |
| G9 código morto/NPE | parcial | decisao passou a ser usado; placeholder do humor virou valor real (#23); prints seguem vivos |
| G10 engenharia | parcial | git + JUnit + avaliador (21 classes de teste); Gradle/pacote/estáticos pendentes |

#### G1 — Match é frase-exata
`ControleResposta.lerResposta()` usa `equalsIgnoreCase(frase.getFrase())`. Todo o resto do pipeline existe só para *forçar* a frase do usuário a virar uma frase registrada. Com 250 frases, a taxa de acerto é baixa por construção — **adicionar mais frases soltas não resolve, só escala o problema**.

#### G2 — Sinônimos são roleta russa
`ControleSinonimo.procuraSinonimo()` escolhe **um sinônimo aleatório por palavra** e substitui na frase do usuário. Além de não-determinístico (a mesma frase pode acertar numa vez e errar noutra), vários pares registrados são falsos:

- `"maneira" → "legal"`, `"segundo" → "como"`, `"do" → "da"`, `"sérios" → "importantes"`

Isso **degrada** a entrada em vez de expandir. Ex.: o usuário escreve "eu ia sair" → vira "eu voce sair" → não casa com nada.

#### G3 — Sem normalização de texto
"você" ≠ "voce", "gosto" ≠ "gostei" ≠ "gostar". Não há stemming PT-BR, não há stopwords, e a pontuação só remove `?` (`FormulaResposta.decifrar()` faz `replace("?", "")`). Acentos digitados corretamente pelo usuário **reduzem** a chance de acerto, porque a base registra tudo sem acento.

#### G4 — Sem memória de conversa
Cada mensagem é processada isoladamente. KAIRA não sabe o nome do usuário, não lembra do tópico anterior, não faz follow-up. É o maior diferencial percebido entre "bot" e "companheira de conversa".

#### G5 — UI apaga a conversa
`ControleFace.actionPerformed()` faz `textAreaLoli.setText(resposta)` — **sobrescreve** a resposta anterior. Não há histórico visível, então a conversa não "existe" para o usuário.

#### G6 — Furo de segurança
`ControleComandos.validaComandos()` chama `Runtime.getRuntime().exec(fraseComando[1])` a partir do texto do usuário, sem confirmação e sem whitelist. Qualquer um que digite "Executar cmd" no chat manda rodar um programa. (E `fraseComando[1]` só pega a primeira palavra — programas com caminho contendo espaço nem funcionam.)

#### G7 — Caminhos relativos
Todos os controllers usam `"KAIRA/resources/..."` relativo ao diretório de execução. Rodar o jar de outra pasta = `FileNotFoundException` (e hoje isso dispara a reinstalação dos dados iniciais!). Ver `ControleKaira`, `ControleResposta`, `ControlePalavra`, `ControleSinonimo`.

#### G8 — Similaridades frágeis (Calculos.java)
- `calculoPorcentagemPalavra` compara caractere-a-caractere **na mesma posição**: "abacate" vs "abacaxi" pontua alto, mas "lista" vs "sltia" pontua baixo. O algoritmo padrão para isso é **distância de Levenshtein**.
- `calculoPorcentagemFrase` usa `fraseUsuario.contains(palavra)` — **substring!** O exemplo registrado "oi" casa com "foi", "oito", "herói". Isso explica matches absurdos do fuzzy.

#### G9 — Código morto e riscos de NPE
- `criacaoLeituraRespostas()` gera `decisao` aleatória que não é usada (código de "criação de resposta" comentado).
- `verificarCriarResp` em `ControleFace` leva a um branch 100% comentado.
- `comandosResposta()` faz `resposta.getAssunto().equalsIgnoreCase(...)` → NPE se algum registro vier sem `assunto`.
- `escolheMultiRespostas()` devolve `null` se a lista estiver vazia (vira "não sei" sem explicação).
- Debug `System.out.println` espalhado; `Math.random()` direto (sem `Random` seedado, sem log).
- `"numero de " + versao` monta o texto estranho "numero de 0.0.1" em `comandosResposta()`.
- `"nivel de humor em... de 10"` — placeholder do humor que nunca foi implementado. *(corrigido na issue #23 — agora mostra `nivel de humor em <valor> de 10`)*

#### G10 — Engenharia
- Sem git, sem build tool (depende do IntelliJ: `.iml` + `out/`), sem testes.
- Pacote `com.JCODE` em maiúsculas (convenção Java é minúsculas).
- Tudo `static` → impossível instanciar dois "cérebros", difícil testar.
- Primeira execução: cria arquivos, mostra JOptionPane e `System.exit(0)` — exige reinício manual.

---

## 3. O plano — visão geral

| Fase | Nome | Ganho percebido | Estimativa | Depende de |
|---|---|---|---|---|
| 0 | Fundação | Nenhum (mas destrava as outras) | 1–2 dias | — |
| 1 | Intenções e classificação | ⭐⭐⭐⭐ acerta muito mais sem decorar frases | 1–2 semanas | Fase 0 |
| 2 | Memória e contexto | ⭐⭐⭐⭐ "ela lembra de mim" | ~1 semana | Fase 1 |
| 3 | Personalidade viva | ⭐⭐⭐ humor, iniciativa, follow-up | 1–2 semanas | Fase 2 |
| 4 | Geração própria | ⭐⭐ respostas novas sem LLM | ~1 semana | Fase 1 |
| 5 | IA de verdade (LLM) | ⭐⭐⭐⭐⭐ conversa livre | 2–5 dias (integração) | Fases 1–3 |

**A regra de ouro:** as Fases 1–3 não são "versão de brinquedo antes do LLM". Elas viram a **camada de skills** do sistema final — comandos, fatos memorizados, personalidade e humor por regras; LLM só para conversa aberta. É exatamente assim que assistentes reais funcionam (regra rápida primeiro, modelo pesado como fallback).

---

## 4. Fase 0 — Fundação

**Objetivo:** destravar as fases seguintes e eliminar riscos. 1–2 dias.

### Tarefas

- [x] `git init` + `.gitignore` (`out/`, `*.class`, `.idea/`) + primeiro commit — *(commit inicial b34046f; .gitignore cobre out/, .idea/, *.iml, *.class)*
- [x] **Normalização central** — criar `util/Normalizador.java` com UMA função usada por todo o pipeline (ver código no [Apêndice B](#apêndice-b--exemplos-de-código)): — *(issue #2; `texto()` virou a função canônica do pipeline)*
  - lowercase, remover acentos (`Normalizer.NFD`), remover pontuação, colapsar espaços, trim
  - aplicar também nos dados ao carregar (base e entrada ficam no mesmo "idioma")
- [x] **Mover os dados para pasta fixa** — `%APPDATA%/KAIRA/` (via `System.getenv("APPDATA")`) ou ao lado do `.jar`. Trocar todas as strings `"KAIRA/resources/..."` (G7). Migração: se existir pasta antiga, copiar antes — *(solução adotada: `Caminhos.recurso()` resolve a base via `kaira.home`/`KAIRA_HOME`/code source — roda de qualquer pasta sem %APPDATA%; issue #4)*
- [ ] **Neutralizar o comando "Executar"** (G6): pedir confirmação via dialog OU remover por enquanto. Se mantiver: whitelist de programas + juntar todas as palavras do caminho (não só `fraseComando[1]`) — *(G6 segue pendente)*
- [ ] Limpar código morto (G9): `decisao` não usada, branch `verificarCriarResp`, prints de debug → um `Logger` simples ou `System.Logger` — *(parcial: `decisao` virou uso real na geração; placeholder do humor consertado na #23; prints de debug ainda vivos)*
- [ ] (Opcional) Adotar Gradle: `gradle init` + mover os jars para dependências declaradas; pacote `com.JCODE` → `com.jcode` (rename automático no IntelliJ)
- [x] (Opcional) Primeiro teste JUnit: `NormalizadorTest` (entrada "  Olá, Tudo bem? " → "ola tudo bem") — *(issue #2; hoje são 21 classes de teste + avaliador)*

### Critérios de aceite

- Rodar o `.jar` de **qualquer pasta** funciona e encontra os dados
- "Você está bem?" e "voce esta bem" viram a mesma string internamente
- Nenhum `Runtime.exec` direto de input do usuário sem confirmação
- `git log` mostra o ponto de partida

---

## 5. Fase 1 — Intenções e classificação

**Objetivo:** passar de *"qual frase exata foi essa?"* para *"o que o usuário quis dizer?"*. É a fase de maior retorno.

### 5.1 Mudança do modelo de dados

As 250 frases atuais provavelmente se agrupam em **~40–60 intenções reais**. O campo `assunto` de hoje já é um esboço disso — mas está no nível da frase (não da intenção) e é metadado da **resposta** (serve só pra `comandosResposta` anexar humor/versão/`exit` via string mágica). A mudança formaliza `assunto` como **intenção**: um rótulo da **entrada** ("o que o usuário quis dizer") que leva a um pool de respostas, com `acoes` explícito no lugar da string mágica. Reestruturar de:

```json
{"frase": "oi", "respostas": ["Como vai?"], "assunto": "cumprimento"}   // modelo atual
```

para o modelo **intenção → exemplos → respostas** (esquema completo no [Apêndice A](#apêndice-a--esquema-json-proposto)):

```json
{
  "intento": "cumprimentar",
  "exemplos": ["oi", "ola", "e ai", "bom dia", "oi tudo bem", "salve", "opa"],
  "respostas": ["Oi! Como você está hoje?", "Olá! Bom te ver por aqui."],
  "followups": ["E você, como está?"],
  "palavrasChave": ["oi", "ola", "salve", "opa"]
}
```

No arquivo final, cada item de `exemplos` e `respostas` é um objeto `{texto, origem}` para rastrear a proveniência de cada frase (detalhe no [Apêndice A](#apêndice-a--esquema-json-proposto)).

**Por quê:** 5–10 variações por intenção multiplicam a cobertura efetiva sem escrever respostas novas. "e aí", "salve", "opa" hoje seriam 3 frases extras para decorar; no novo modelo são só exemplos de custo ~zero.

- [x] Mapear as 250 frases atuais → intenções (agrupar por `assunto` + olhar as respostas parecidas) — *(issue #7; dados travados por testes de curadoria: IntencoesCuradoNaoDegradadoTest)*
- [x] Escrever o conversor (um script Java ou conversão manual única) — manter backup do JSON antigo — *(geração única para intencoes.json; respostas.json original mantido no projeto)*
- [x] Para cada intenção, adicionar 5–10 exemplos (gírias, com/sem acento, erros comuns de digitação) — *(parcial: exemplos migrados + expansão contínua pelo auto_aprendizado)*
- [ ] Novo campo `followups` já nasce preenchido — será usado na Fase 2 — *(campo existe no esquema; preenchimento/uso seguem na Fase 2)*

### 5.2 Classificador por pontuação (substitui o `equalsIgnoreCase`)

Em vez de igualdade, **somar pontos** entre a frase do usuário e cada intenção:

```
score(usuario, intenção) = Σ peso(palavra) para cada palavra do usuário que aparece
                           nos exemplos/palavrasChave da intenção
peso(palavra) = raro no geral + presente na intenção = mais pontos (IDF simples)
```

Detalhes práticos:

- [x] Implementar **TF-IDF simplificado** (ver pseudo-código no [Apêndice B](#apêndice-b--exemplos-de-código)) — dá pra fazer em Java puro, ~50 linhas — *(feito: `pesosIdf` = log(N/df) por token pos-stem; score = pesos dos casados ÷ pesos da entrada; denominador 0 cai na cobertura uniforme — issue #14)*
- [x] Ou, versão mínima viável: **Jaccard sobre conjuntos de palavras** (`interseção / união`) — 10 linhas — *(descartado — o TF-IDF acima cumpriu a mesma função)*
- [x] Definir limiar de confiança (ex.: ≥ 0,5 → acionar intenção; entre 0,3 e 0,5 → responder com a melhor aposta + "Foi isso que você quis dizer?"; < 0,3 → fallback) — *(faixas entregues: ALTA ≥ 0,6 responde direto · MÉDIA 0,3–0,6 aposta + "Foi isso que você quis dizer?" · BAIXA < 0,3 fallback — issue #16, commit 08e1666)*
- [x] Empate entre intenções → desempatar pelo **contexto** da Fase 2 (tópico atual) — *(entregue: issue #41 — `desempatePorContexto` no `buscaResposta`: frase só com tokens genéricos + tópico com exemplo que casa → o tópico decide **antes** do match ruidoso; spec `DesempateContextoTest`)*
  - **Achados do uso real (out/2026) — dois buracos independentes, "a ver depois":**
    1. **A âncora do tópico não rodava no match exato.** O ramo de igualdade literal do `buscaResposta` dá `return` **antes** de ancorar → o tópico ficava vazio → o desempate não tinha o que usar. É o que fazia "qual seu favorito" cair em `gostos_ia` (o tópico filme nunca foi gravado). **Em correção pela casa (out/2026).** Lição: o `AncoraTopicoTest` (#38) afirmava "match de verdade ancora o tópico" chamando `melhorIntencaoDetalhado` **direto** — nunca o caminho do app; por isso o buraco passou.
    2. **Gênero no desempate.** Ele casa por **token compartilhado**, e o dump não liga variantes de gênero (`favorito ≁ favorita`, `seu ≁ sua`) → tópicos com exemplo feminino (`musica`, `serie`) ficavam inalcançáveis: "qual seu favorito" com tópico musica respondia **filme**. **Corrigido no código (#42, out/2026):** `TOKENS_GENERICOS` virou lista de **grupos de variante** (gênero/número/sinônimo) em vez de lista chata; a `fraseSoGenerica` usa o flatten (comportamento idêntico) e o desempate passou a casar variante com variante via `mesmoGrupoGenerico`. Cirúrgico **por decisão**: o grupo vale só no desempate, porque um índice de sinônimo largo age no **classificador** e desloca os pesos dos 14 casos congelados. Confirmação ao vivo: o mesmo turno do bug (`qual o seu preferido?` com tópico `musica`) resolve pra `musica` — coberto por `FluxoConversaRealTest` e `desempateCasaGeneroESinonimoNaBaseReal`.
      - **Rota de dados (re-adotada após achar a causa raiz):** tentou-se o `grupos_sinonimos.json` (grupo `["seu","sua","seus","suas"]` + fusão de `favorito`/`preferido`). Na primeira medição **quebrou**: `"qual a sua cor favorita"` (gostos_ia) ganhou um 2º token de match (`sua`~`seu`), **empatou** com `filme` em 0.61 e o desempate por frame levou pro lado pior — o degrau de correção do `FluxoConversaRealTest` acusou. **Mas a causa raiz não era o sinônimo.** O turno entra com erro de digitação (`fimle`), e o `ControleCorrecao.corrigirPalavras` devolvia `fome` em vez de `filme`: ambos ficam a distância 2 de `fimle`, o `<` estrito do `correcaoPalavra` (`ControlePalavra` L81) dá o empate pra quem vem **primeiro** no `palavras.json`, e `filme` simplesmente **não existia** naquele arquivo (456 itens; tinha `filmes`, não `filme`). O match então chegava enfraquecido (0.61) — e só um match fraco é vulnerável ao sinônimo. **Correção:** +`"filme"` no `palavras.json`, antes do `"fome"`; com isso a correção devolve `filme`, o match vai a **1.00**, e os sinônimos voltaram pro arquivo. Suíte completa verde (228 + avaliador 14/14). Lição dupla: (a) sinônimo commitado **não é reserva, é comportamento vivo**, porque o `ControleSinonimo.carregaCache()` roda no startup e o `mesmaPalavra` é usado pelo classificador inteiro; (b) ele **só** estraga match já enfraquecido — um dicionário de correção incompleto era o que abria a fresta.
    3. **Borda restante:** intenção **sem nenhum exemplo de "favorito"** (arte, história, clima, podcast...) → o desempate devolve null → a pergunta genérica caía no vencedor ruidoso. **Medido (out/2026): 82 dos 98 intents estão nessa situação** (`historia` e `clima` existem na base; `arte`/`podcast` não). **O que se descobriu ao medir é pior do que o enunciado:** não era só resposta errada — era **sequestro do tópico**. O desempate acerta em devolver `null`, mas o código caía pro classificador normal, que casava `gostos_ia` a **1.00** (`"qual a sua cor preferida"`) e o `ancoraTopico` **apagava o tema do usuário**: `historia` → `gostos_ia`, e o assunto não voltava mais. **Corrigido no código (#44, out/2026):** o ramo genérico sinaliza `genericaSemDesempate` quando o desempate devolve `null` e, antes do `return`, restaura `topicoAnterior` (só quando havia tópico — sem nada a preservar, o comportamento de antes segue igual). Specs `genericaSemExemploDeFavoritoNaoSequestraOTopico` (fixture) e `bordaRealTopicoSemFavoritoNaoSequestrado` (base real, `historia`+`clima`), ambas vermelhas pelo motivo certo antes do fix: `expected:<[histor]ia> but was:<[gostos_]ia>`. **Pendente:** o outro candidato — a resposta com `{topico}` ("sobre história, não tenho um favorito marcado..."), pra reconhecer a lacuna em vez de devolver match de `gostos_ia`. O fix de #44 salva o tópico; não conserta a resposta.

**Limite da cobertura (por que a pré-análise da §5.7 existe):** o pareamento por exemplos casa a entrada com frases **já vistas**. Pra um tópico novo fora da base (ex.: "qual seu carro favorito?", nunca visto) a cobertura só pega o que tiver em comum com exemplos parecidos ("qual seu jogo favorito") e ainda assim precisa de uma resposta pro tópico novo — ver [§5.7](#57-pré-análise-frame--slot-tarefa-8).

### 5.3 Stemming PT-BR caseiro

"gosto/gostei/gostar/gostosa" devem contar como a mesma palavra:

- [x] Sufixos para cortar, em ordem (implementação ~15 linhas): `ções→ção`, `-mente`, `-ando/-endo/-indo`, `-ei`, `-ou`, `-amos/-emos/-imos`, `-istas/-ista`, `-ção`, `-eza`, `-ança`, `-ar/-er/-ir`, plural `-s` — *(issue #12: 12 regras na ordem, primeira que casa vence, formas sem acento, aplicação única sem encadear)*
- [x] Tamanho mínimo do radical: 3 letras (evita "pá" → "p") — *(guarda: radical < 3 devolve a palavra original)*
- [x] Alternativa pronta: **Apache Lucene** tem `PortugueseStemmer` (algoritmo RSLP) — é um jar a mais no `externallibs`, mesmo esquema do Jackson — *(descartado — o stem caseiro cobriu o necessário sem jar extra)*

### 5.4 Stopwords PT-BR

Palavras que não discriminam intenção (aparecem em tudo) não devem pontuar:

```
a o as os de da do das dos e é um uma que em no na nos nas por para com
ao aos à às se eu tu ele ela nós eles você vocês me te lhe isso isto este
esse aquele mais muito pouco já não sim mas ou nem como quando onde quem qual
```

- [x] Lista num arquivo `stopwords.txt` (ou campo no JSON), aplicada **depois** do stemming — *(issue #12: 54 palavras + `vc` em KAIRA/resources/stopwords.txt; carregada com texto+stem (forma canônica) e filtrada nos DOIS lados via `tokensDe`; arquivo ausente = sem filtro. Bônus: a geração de respostas também não troca sinonimo de stopword — follow-up da #13)*

### 5.5 Sinônimos: de substituição para **grupos**

Matar o G2. Não trocar a palavra do usuário — em vez disso, duas palavras casam se pertencem ao mesmo grupo:

```json
{"grupos": [
  ["oi", "ola", "salve", "opa", "eae"],
  ["voce", "vc", "tu"],
  ["gostar", "curtir", "amar"],
  ["legal", "bacana", "massa", "daora"]
]}
```

- [x] Converter `sinonimos.json` para grupos; **remover os pares falsos** ("maneira"→"legal" etc.) — *(grupos_sinonimos.json curado, pares falsos fora — issue #9)*
- [x] No classificador: palavra do usuário → grupo → compara pelo grupo — *(ControleSinonimo.mesmaPalavra no matcher; índice com duas chaves: cru+stem pro grupo, cru pro indicePalavras da geração — issues #11 e #12)*
- [x] Deletar a lógica de substituição aleatória em `ControleSinonimo.procuraSinonimo` — *(não existe mais no código; a geração nova usa buscaGrupoSinonimo + CriarResposta — issue #13)*

### 5.6 Correção de erro de digitação

- [x] Substituir `calculoPorcentagemPalavra` por **distância de Levenshtein** (código no [Apêndice B](#apêndice-b--exemplos-de-código)): distância 1–2 (dependendo do tamanho) → corrige — *(issue #15: `Calculos.levenshtein` do Ap. B.2; limiar por tamanho do digitado — ≤4 letras aceita 1, maior aceita 2; nota honesta: Levenshtein puro não vê anagrama — `lista`/`sltia` = 4, fica de fora)*
- [x] Tirar o desempate da correção de cima da **ordem do `palavras.json`** — *(issue #46: no empate de distância vencia quem vinha primeiro no arquivo — `fimle` viraria `fale` numa lista alfabética; `Calculos.damerau` = Levenshtein + troca de letra vizinha = 1, e `fimle`/`filme` passa a vencer sozinho em qualquer ordem — `CorrecaoOrdemArquivoTest` + `CalculosDamerauTest`; medido: empate em 6,4% dos erros de 1 edição e lá a ordem só acerta 44%; anagrama livre continua fora do limiar, `lista`/`sltia` = 4 — Ap. B.2 segue verdadeiro)*
- [x] Regenerar o dicionário quando o arquivo some — *(issue #47: `buscaArquivoPalavra` devolvia `null` no `FileNotFoundException` → cache `null` → NPE na primeira correção — apagar o `palavras.json` (ou clonar sem ele) derrubava a correção inteira. Agora o ramo chama `geraPalavrasDasFontes()`: lê `intencoes.json` + `grupos_sinonimos.json`, normaliza igual o pipeline (`Normalizador.texto`), grava o arquivo e devolve a lista — **nunca `null`** (sem base devolve lista vazia; sem grupos regenera só da base). Ordem **base primeiro, só-sinônimo depois** = o desempate do #46 protegido em **qualquer** regeneração; medido (`ProbeEmpatesAB`, mesma amostra nos dois dicionários): os 18660 sinônimos não mudam o acerto das palavras da base (94,1% = 94,1% — empate dobra 8,5% → 18,5% mas não muda quem vence) e levam a palavra só-de-sinônimo de 0% pra 94,7% — `PalavrasRegeneraAoFaltarTest`; `palavras.json` regenerado pelo **próprio fluxo de produção**: 20673 entradas, commitado == regenerado)*
- [x] Corrigir `calculoPorcentagemFrase` para comparar **palavra por palavra** (nunca `contains` na frase inteira — G8) — *(issue #15: palavra exata por palavra; "sol" dentro de "ensolarado" não conta mais)*
- [x] A cascata de correção continua existindo, mas agora: normaliza → stemma → agrupa → classifica (muitos "erros" somem sozinhos com a pontuação) — *(mantida em 4 degraus: lerResposta → corrigirPalavras → corrigirFrases → geração)*

### 5.7 Pré-análise frame + slot (task #8)

> **Escopo decidido:** fica pra **depois** da task #7 (migração pra `intencoes.json` + cobertura de exemplos). A #7 fecha os 2 gaps do eval; esta seção é o passo seguinte.
>
> **Atualização:** a #7 foi concluída (migração + gaps fechados + avaliador 14/14) — **esta seção está liberada** e é o próximo item da Fase 1.

Ideia do autor: antes de buscar na base, decompor a frase em **frame** (a estrutura/molde) e **slot** (o termo de conteúdo). Ex.: "qual seu **carro** favorito?" e "qual seu **jogo** favorito?" são o **mesmo frame** (`qual seu X favorito`); o slot muda (`carro` vs `jogo`). Como a KAIRA conhece o frame pelo jogo, ela sabe que é uma **pergunta de preferência** mesmo nunca tendo visto "carro".

- [x] **Marcadores são frase-modelo, não palavra solta**: `qual...favorito`, `voce gosta de`, `o que acha do`, `bom/boa dia/tarde/noite` (cumprimento de família). "qual/quando/como" isolado → PERGUNTA genérica é fraco (quase tudo é pergunta no banco) — *(issue #18: tabela curada de 4 famílias casadas por regex de 2+ palavras — `qual .* favorit` · `gosta de` · `acha d` · `bom dia|boa tarde|boa noite`)*
- [x] **O mecanismo é o §5.4 (stopwords)**: remover `qual/seu/de/favorito` isola o conteúdo (`carro`). Esta seção é o §5.4 + §5.2 juntos, separando **frame** de **slot** em vez de só indexar palavra-chave — *(issue #18: `ControlePreAnalise.preAnalise` — slot = palavras de conteúdo sem stopword/pronomes/rubricas, na forma **crua** do usuário; "qual seu carro favorito" → frame `preferencia`, slot `carro`)*
- [x] **Rodar ANTES, mas como SINAL — nunca como portão**: o frame é calculado logo de cara e entra como **peso/desempate** no ranking da cobertura, sem barrar a busca geral. Se o frame erra, a cobertura ainda roda plena e pode achar — nunca regride o que a #7 conquistou — *(issue #18: desempate de 3º nível no `melhorIntencaoDetalhado` (`vencePorFrame`): só age em empate exato de score **e** resto; sem frame na entrada = comportamento byte a byte o anterior — avaliador 14/14 confirma zero regressão)*
- [x] **Fallback informado** (quase nunca há resposta pro slot novo): em vez do "Não sei como responder a isso" seco, dizer **"ainda não sei sobre {slot}"** (ex.: "ainda não sei sobre carro"). Evolui o fallback da [§7.3](#73-fallbacks-inteligentes) — *(issue #18: sobrecarga `respostaFallback(String slot)` + pool `FRASES_FALLBACK_SLOT` com `{slot}` pré-preenchido — mesma alma da #17 (honesto + curioso + `ensin` + `?`) e anti-repetição compartilhada; slot vazio cai no pool genérico)*
- [x] O frame já calculado fica disponível pra **decidir o caminho** no futuro (contexto prévio) — *(`ControlePreAnalise.preAnalise` é público; quem vai usá-lo pra decidir caminho é a Sessão da §6.1)*
- [ ] **Expandir a tabela de frames com o uso real** — os slots e frames que repetem no `auto_aprendizado` são a mina de padrões: cada frame novo = 1 linha em `FRAMES` (+ rubricas associadas) — *(backlog contínuo, sugerido pelo autor em out/2026 — a tabela atual é curada de propósito, não é parser)*

### Critérios de aceite

| Entrada do usuário | Comportamento esperado |
|---|---|
| "Oi!" | intenção `cumprimentar` |
| "e aí, beleza?" | intenção `cumprimentar` (não está registrada como frase) |
| "ola tud bem" (typo) | intenção `cumprimentar` com confiança razoável |
| "você gosta de música?" e "voce curte musica?" | **mesma** intenção |
| "qual seu jogo favorito" e "qual seu jogo preferido" | mesma intenção ("favorito" e "preferido" no mesmo grupo) |
| frase sem relação com nada | fallback honesto com slot quando há conteúdo: **"Ainda não sei sobre {slot}... me ensina?"**; sem conteúdo, o pool genérico (#18) |

---

## 6. Fase 2 — Memória e contexto

**Objetivo:** KAIRA lembra da conversa. É a maior "ilusão de inteligência" pelo menor preço — sem nenhum modelo de ML.

### 6.1 Sessão de conversa

Criar `domain/Sessao.java` (instância, não static):

```java
class Sessao {
    String nomeUsuario;            // null até aprender
    String topicoAtual;            // última intenção significativa
    int turnos;                    // quantas trocas
    int humor;                     // 0–10, começando em 5 (usado na Fase 3)
    List<Turno> historico;         // últimas N mensagens (N=20)
    Map<String,String> fatos;     // "jogo favorito" → "minecraft", etc.
}
```

- [x] Detectar **nome**: regex simples sobre padrões `"meu nome é X"`, `"me chamo X"`, `"sou o X"`, `"pode me chamar de X"` — e perguntar "Como posso te chamar?" no primeiro uso — *(issue #19: `ControleSessao` — 4 `Pattern`s na grafia **crua** do usuário, "sou" ancorado no início da frase; a pergunta sai uma vez só no 1º turno, e nunca se o nome já foi extraído)* — **fluxo da resposta fechado pela issue #24** (demanda do usuário, out/2026): nome "pelado" digitado na janela da pergunta salva e devolve `"Prazer em conhecer voce, X!"` fora do classificador (lista de bloqueio: `oi`, `tudo bem`, `não`… não viram nome); padrões param no conector (`e`, `que`, `com`…), então `"meu nome é João e quero falar de filmes"` salva `João` e o resto do assunto segue pro classificador via `tiraNome`
- [x] Detectar **fatos sobre o usuário** com pares pergunta/resposta da conversa: usuário diz "eu gosto de minecraft" → `fatos.put("gosta de", "minecraft")`. Nas próximas conversas: "Você me disse que gosta de minecraft — ainda joga?" — *(issue #19: tabela `{chave, regex}` com 1 regra na v1; **usar** os fatos nas respostas é a §6.3 — a estrutura cresce como a tabela de frames)*
- [x] **Tópico atual**: guardado a cada intenção classificada; usado para desempate e para "sobre isso?" — *(issue #19: hook no `melhorIntencaoDetalhado` — última intenção significativa, match `null` não limpa; o uso no ranking vem no futuro)*
- [x] Persistir sessão entre execuções (`sessao.json` na pasta de dados) — "Bem-vindo de volta, João!" ao reabrir — *(issue #20: `gravaSessao`/`carregaSessao`/`boasVindas` no `ControleSessao` — Jackson por visibilidade de campo (`FIELD/ANY`, a `Sessao` não tem setter de turnos/humor/histórico/fatos), arquivo ausente **ou corrompido** vira sessão nova sem derrubar a abertura; `Main` carrega antes da `Face`, `Face` grava no `windowClosing`; gravação UTF-8, `sessao.json` no `.gitignore` — review fix: construtor vazio no `Turno`, sem ele o `readValue` lancava na leitura)*

### 6.2 Histórico visível (corrige G5)

- [x] `textAreaLoli.append(...)` em vez de `setText` — o chat vira um log real — *(issue #1; teste ControleFaceTest)*
- [x] Auto-scroll para o fim; separador visual entre turnos — *(issue #22: o auto-scroll já existia no `balao()` (`setCaretPosition(doc.getLength())`) com spec próprio; entregue na #22 o `fimDeTurno()` — régua `<hr>` depois da resposta da KAIRA, cursor no fim junto, `null` não fecha turno; 3 specs no `ControleFaceTest`)*
- [x] (Opcional) `conversas.log` com timestamp — dados para análise futura e para treinar/tunar — *(entregue nos commits laterais: `Registro.linha` nos ganchos de decifrar/resposta/sessão, coberto por `RegistroTest`, arquivo no `.gitignore`)*

### 6.3 Slots nas respostas

- [x] Suporte a placeholders nas respostas: `{nome}`, `{topico}`, `{hora_do_dia}` ("bom dia" só antes do meio-dia, senão "boa tarde") — *(issue #21: `ControleResposta.preencheSlots` — render na entrega, o JSON guarda o slot cru; defaults de sessão vazia: `amigo` / `tudo um pouco`; regra literal de hora do plano (2 estados); dois pontos de render idempotentes — `comandosResposta` e `enviarResposta`)*
- [x] Exemplo: `"Bom te ver de novo, {nome}! Da última vez a gente tava falando sobre {topico}."` — *(capacidade coberta pelo spec (nome+tópico+hora renderizados); nenhum dado usa `{topico}` ainda — a curadoria pode aplicar quando quiser)*
- [x] (do §6.1) uso dos `fatos` nas respostas: slots `{fato:CHAVE}` com filtro no pool — *(issue #21: resposta com fato pendente não sai do pool; sem fato aproveitável → fallback genérico; dados: `lembra_gosta`/`lembra_mora`/`lembra_trabalha`)* — *(+ #32 (BRIEFING P7.4): time / música favorita / pet / sonho — 5 padrões novos em `PADROES_FATO` com artigo cortado e intents `lembra_time`/`lembra_musica`/`lembra_pet`/`lembra_sonho` no espelho dos existentes)*

### Critérios de aceite

- [x] Fechar e reabrir → KAIRA cumprimenta pelo nome e retoma o tópico — *(#20)*
- [x] "você lembra o que eu falei que gosto?" → resposta correta baseada em `fatos` — *(#21)*
- [x] A UI mostra a conversa inteira, não só a última resposta — *(#1 + balões)*

---

## 7. Fase 3 — Personalidade viva

**Objetivo:** transformar respostas mortas em interação. O código já tem o gancho: `comandosResposta()` anexa "nivel de humor em... de 10" para o assunto "status" — vamos implementar o humor de verdade.

### 7.1 Sistema de humor

- [x] Campo `humor` (0–10, inicial 5) na sessão, persistido — *(estrutural desde a #19; persistência + clamp 0..10 na spec da #23)*
- [x] **Modificadores**: elogio ("você é legal", "gosto de você") → +1; grosseria ("você é chata", xingamento) → −1; despedida carinhosa → +0 — *(detectaHumor em `processa`, padrões na fala crua com/sem acento, log em `Registro` — issue #23)*
- [x] **Faixas de tom** — mesma intenção, resposta diferente por humor:
  - 0–3: seca/cética — `"tô de boa."`
  - 4–6: neutra — `"estou bem, obrigada por perguntar."`
  - 7–10: animada — `"estou ótima! seu papo melhora meu dia!"` — *(`status_geral` re-bandado: 4 seca / 14 neutra / 4 animada, inclusive as frases de exemplo do plano — issue #23)*
- [x] Implementação: cada intenção pode ter respostas marcadas por faixa (ver [Apêndice A](#apêndice-a--esquema-json-proposto)), ou um pós-processador ajusta a mensagem — *(decisão: usar o `humorMin`/`humorMax` já existentes — filtro do pool em `comandosResposta`, sem schema novo e sem pós-processador; faixa vazia cai no pool inteiro — issue #23)*
- [x] "Como está seu humor?" → responde o valor real + frase da faixa (finalmente conserta o placeholder) — *(`nivel de humor em <valor> de 10` — issue #23)*
- [ ] (Opcional) Decaimento: humor volta 1 ponto por dia sem interação

### 7.2 Iniciativa própria

- [x] A cada N turnos (ex.: 3) sem KAIRA ter feito pergunta → usar `followups` da intenção — *(issue #25: contador `turnosDesdePergunta` em `processa`; `perguntaInteresse()` lê os followups do tópico atual no banco — 195 já escritos pela curadoria em 74 intenções — e zera também quando a pergunta de nome dispara)*
- [x] Perguntas de interesse genérico num pool próprio: "E você, o que andou fazendo hoje?", "Assiste algo bom ultimamente?" — *(`PERGUNTAS_INTERESSE` em `ControleSessao` com as frases do plano, uma com `{nome}`; prioridade pro followup do tópico, pool genérico como segunda fonte; anti-repetição estilo `ultimoFallback` — issue #25)*
- [ ] (Opcional) `javax.swing.Timer` → silêncio de 60s+ gera um comentário leve ("tá aí? posso esperar, sou paciente... sou feita de paciência, literalmente")

### 7.3 Fallbacks inteligentes

Substituir o "Não sei como responder a isso" seco:

- [ ] Classificou algo com confiança média → aposta + confirmação: *"Sobre {palavrasChave}, é isso? Ainda tô aprendendo — me responde essa: ..."* — *(degrau da faixa média pronto — issue #16; a frase personalizada com {palavrasChave} e o followup ficam pra §7.1 humor)*
- [x] Confiança baixa → honesto + curioso: *"Ainda não sei falar disso... me ensina? O que você quis dizer?"* → cai no modo ensino atual (que já existe e funciona!) — *(pool de 3 frases em `FRASES_FALLBACK` + anti-repetição via `ultimoFallback` — issue #17; o modo ensino segue armado no `enviarResposta`)*
- [ ] Com a pré-análise da [§5.7](#57-pré-análise-frame--slot-tarefa-8) (task #8): quando o **frame** foi reconhecido mas o **slot** é novo, responder "ainda não sei sobre {slot}" em vez do genérico
- [x] **Modo ensino turbinado**: ao ensinar, perguntar em qual intenção encaixa (ou criar nova) — em vez de gravar frase solta com assunto "Auto-Aprendizado". O dado coletado passa a alimentar o classificador — *(entregue — issue #33: `ensinaEm` classifica antes de gravar; score ≥ `LIMIAR` (0.6) → par no intento vencedor, dúvida → `auto_aprendizado` como sempre; `invalidaCacheIdf` por último (recalculo sem NPE no ensino em cadeia); linha `APRENDE   :` no log — spec `EnsinoTurbinadoTest`)*
- [x] **Loop do ensino ponta a ponta** — o que faltava do ensino: fallback → `!` armado → próxima fala absorvida → **a frase volta a casar** — *(entregue — issue #45, spec `EnsinoFluxoTest`: o loop de 3 turnos atravessado pela **mesma string** — não casa → vai pro `bufferFrases` → é ensinada **pelo buffer** (não por literal nova) → volta a casar. A spec **revelou o bug**: `enviarResposta` chamava `ativarAprendizado()` **antes** de gravar o `bufferFrases`, e `mudarVerificar()` estourava em `btnSinalizar` null — a UI era pre-condição do dado e, quando falhava, a frase do usuário se perdia. Fix em `ControleFace.mudarVerificar()`: estado primeiro, UI depois, com guarda de null. `mudarCriar()` tem o mesmo padrão mas **não foi mexida** — não há caminho ativo que a chame)*

### Critérios de aceite

- Elogiar 3× seguidas muda o tom das respostas de forma perceptível — *(entregue — issue #23, `SistemaDeHumorTest.tresElogiosMudamTomDaResposta`)*
- KAIRA faz pelo menos 1 pergunta espontânea a cada ~5 turnos — *(entregue — issue #25: dispara no 3o turno, com followups do tópico ou o pool genérico — `IniciativaPropriaTest`)*
- Fallback nunca repete duas vezes a mesma frase (anti-repetição simples) — *(entregue — issue #17)*

---

## 8. Fase 4 — Geração própria (opcional)

**Objetivo:** respostas que não estão gravadas, sem LLM. Divertido e educativo, mas limitado — não gastar muito tempo aqui se a meta final é a Fase 5.

### 8.1 Templates + gramática mínima

- [ ] Respostas parametrizadas compostas: `[saudação] + [reação ao tópico] + [followup]`, cada peça sorteada de um pool coerente com humor/tópico
- [ ] Ex.: "{saudacao}! {reacao:{topico}} {pergunta_pool}" → "Oi de novo! Minecraft é um assunto bom. Qual seu servidor favorito?"

### 8.2 Cadeia de Markov (para curiosidade)

- [ ] Treinar ordem-2/3 sobre o próprio `respostas.json` + `conversas.log` acumulado
- [ ] Usar só para comentários curtos/engraçados — texto longo desanda rápido
- [ ] Higiene: nunca gerar frase idêntica a uma registrada; tamanho máximo

**Aviso honesto:** Markov impressiona nos primeiros 30 segundos e depois quebra. É uma fase de aprendizado, não de produto.

**Refinamento (ideia do autor + análise out/2026) — Markov como FILLER do ciclo:**

- **A ideia:** quando ela já falou todas as frases que sabe sobre um assunto, antes de reiniciar a lista, soltar **uma última resposta curta gerada** — "aleatória" no meio do ciclo, para mascarar a rotatividade. O *encaixe* é bom: o reinício visível fica 1 turno mais longe e o padrão percebido "quebra".
- **O acerto em relação à versão original desta seção:** treinar **por intent** (corpus estreito e coerente — mesmo tema, mesma persona) é muito melhor que treinar na base inteira (que mistura vozes e assuntos). A qualidade do Markov depende do corpus; corpus por tema ataca exatamente o defeito antigo.
- **Por que NÃO dá agora (limite técnico, não de vontade):** os pools têm **4–12 respostas curtas** por intent (`lembra_*` tem 4; as diárias, 6–12, depois da leva de dados out/2026). Nesse tamanho, uma cadeia ordem-2/3 tem ~60–120 bigramas → a saída tende a ser **cópia ou quase-cópia** (não mascara — denuncia), e a higiene "nunca gerar frase idêntica" rejeita o resto → sobra quase nada → o gerador cai de volta na frase original (o filler não existe). Dead ends e loops também são frequentes.
- **Custo assimétrico:** repetir é "meh"; uma frase gerada meio sem sentido no meio de um papo bom é um "**quê?**" — quebra a ilusão *mais* do que a repetição que ia mascarar.
- **O mesmo slot com motores melhores (risco zero, e quase sem código novo):**
  1. **Pool de conectores curados** — frases curtas, vagas e na voz dela, topic-agnostic ("Pois é.", "Hmm, tô digerindo isso.", "Isso rende conversa, viu."). Curado = testável (o projeto sabe validar); vago mascara melhor que texto gerado.
  2. **Conector com `{topico}`** — o slot **já existe e já renderiza** (`ControleResposta.preencheSlots`, default "tudo um pouco"): *"Pois é, {topico} é desses assuntos que rendem."* → filler ciente do tema com **zero código novo** (reusa a máquina de #27/#28).
  3. **Emprestar o pool de `reacao_curta`** (introduzido na leva de dados out/2026) — perfil exato de filler ("Verdade. Continua, tô ouvindo."). Cruzamento entre intents, custo mínimo.
  - Regra do encaixe: **quando a janela recente do intent esgota** (o anti-repetição #35 já controla essa janela — N=2), sai um conector **antes** do sorteio normal voltar.
- **Onde a ideia TEM futuro:** quando o corpus por tema for **rico** — o dicionário de conhecimentos (§12.1/C3, lead da Wikipédia + OpenWordnet atrás do `{topico}`). Com parágrafos por tema em vez de 6 frases, a recomposição por fragmento deixa de ser frágil e vira motor natural (resumir, comentar, recombinar). **Não descartar — guardar para a era do dicionário.**
- **Decidir com evidência, não intuição:** medir a **taxa de exaustão** em sessões reais (mesmo intent acionado mais de N (= tamanho do pool) vezes numa sessão) usando o `conversas.log` — **depois da higiene C2** (hoje o log é poluído pelos testes; ver §6-C2 do `BRIEFING_EXPANSAO_DIALOGO.md`). Se a exaustão é rara, o filler é otimização de caso raro; se é comum, o slot merece investimento.

---

## 9. Fase 5 — IA de verdade (LLM)

**Objetivo:** conversação livre. Sendo honesto: conversa aberta de qualidade, hoje, significa **modelo de linguagem** (LLM). As três opções:

### 9.1 Comparação dos caminhos

| | **A. LLM local (recomendado)** | B. API paga | C. Treinar modelo próprio |
|---|---|---|---|
| Como | Ollama ou LM Studio na mesma máquina; KAIRA chama via HTTP | OpenAI / Anthropic / Google | Python + HuggingFace; KAIRA vira cliente do serviço |
| Custo | R$ 0 (só hardware) | Centavos por conversa (cresce com uso) | R$ 0 + muito tempo (meses/anos) |
| Qualidade da conversa livre | Boa (modelos 7–8B) | Excelente | Depende (e muito) |
| Privacidade | 100% offline | Dados vão pra nuvem | 100% offline |
| Requisito | ~8 GB RAM para modelo 7–8B quantizado | Conta + chave de API | GPU + conhecimento de ML |
| Aprendizado pessoal | Alto (integração, prompts) | Alto | Altíssimo (mas outro hobby) |

### 9.2 Caminho A passo a passo (Ollama)

1. Instalar o [Ollama](https://ollama.com) (Windows: instalador direto)
2. `ollama pull qwen2.5:7b` (bom em português; alternativas: `llama3.1:8b`, `gemma2:9b`, `phi3.5`)
3. Ollama sobe servidor em `http://localhost:11434` — chamar `POST /api/chat` (exemplos de JSON e de cliente Java com `HttpClient` no [Apêndice B](#apêndice-b--exemplos-de-código))
4. `controller/ClienteLLM.java`: envia histórico da sessão + system prompt; recebe texto; mostra na UI

### 9.3 Arquitetura híbrida (o desenho final)

```
Mensagem do usuário
        │
        ▼
   [Fase 1] Classificador de intenções ── confiança alta? ──► resposta por regras (rápida, offline, barata)
        │ confiança baixa
        ▼
   [Fase 2/3] Sessão: nome, fatos, humor ──► injetados no prompt como contexto
        │
        ▼
   [Fase 5] LLM ──► resposta gerada, com a personalidade da KAIRA
        │
        ▼
   A resposta gerada vira sugestão de novo exemplo/intenção ──► base continua crescendo
```

- System prompt carrega a identidade: *"Você é a KAIRA. Personalidade: carinhosa, curiosa, um pouco sarcástica. Humor atual: {humor}/10. O usuário se chama {nome}. Fatos que você sabe: {fatos}."*
- **Comandos e fatos ficam nas regras** (confiável, rastreável, sem custo); LLM cobre o resto

### 9.4 Cuidados

- Nunca enviar dados sensíveis do usuário para API em nuvem sem avisar (o log da Fase 2 contém a vida das conversas)
- Chave de API nunca hardcode — variável de ambiente
- Timeout + fallback: se o LLM demorar/estiver fora, cair no sistema de intenções (que continua 100% funcional)

---

## 10. Top 5 ações imediatas

Ordem sugerida, começando amanhã:

1. ✅ **Append no histórico da UI** (`ControleFace.actionPerformed`: `setText` → `append`) — bug mais visível, correção de 5 minutos, muda a sensação do produto — *(issue #1)*
2. ✅ **`util/Normalizador.java`** + aplicar em `decifrar()` e no carregamento da base — mata a família de problemas de acento/case/pontuação — *(issue #2)*
3. ✅ **Reestruturar `respostas.json` por intenção** — agrupar as 250 frases em ~40–60 intenções com exemplos ([Apêndice A](#apêndice-a--esquema-json-proposto)) — *(issue #7)*
4. ✅ **Classificador por pontuação** (Jaccard/TF-IDF) no lugar do `equalsIgnoreCase` — junto com grupos de sinônimos e stemming — *(issues #9–#14)*
5. ✅ **`Sessao` com nome do usuário** — pergunta no primeiro uso, usa `{nome}` nas respostas — *(issues #19/#21)*

Essas 5 já transformam a experiência sem nenhuma dependência externa e sem jogar nada fora.

---

## 11. Alimentando a base de dados

### 11.1 A fonte mais valiosa já está ligada

O modo ensino atual coleta pares reais. Melhorias para extrair mais valor:

- Registrar **confiança** do match que gerou cada aprendizado
- Perguntar a **intenção** no ensino (Fase 3) — dado vira exemplo do classificador
- Deduplicar (mesma frase ensinada 2× → mesma intenção, mais uma resposta no pool)

### 11.2 Autoria manual dirigida (mais eficaz que "mais frases")

Escrever por **categoria de intenção**, com checklist de cobertura. Mínimo para se sentir "viva":

| Categoria | Exemplos de variações mínimas |
|---|---|
| Cumprimentos | oi, olá, e aí, salve, opa, bom dia/tarde/noite, eae |
| Despedidas | tchau, flw, até mais, até logo, xau, bonne |
| Sobre a KAIRA | quem é você, o que você é, seu nome, o que sabe fazer, sua versão |
| Gostos dela | cor, música, jogo, comida, filme favorito |
| Estado/humor | como está, tudo bem?, você sente?, está feliz? |
| Relacionamento | elogios, grosserias, "gosto de você", "me odeia?" |
| Small talk | tempo, tédio, "me conta algo", "fala algo engraçado" |
| Ajudante | "pode me ajudar", "o que você sabe fazer" |
| Meta-conversa | "você lembra de mim?", "sobre o que conversamos?" |

### 11.3 Corpora públicos (com ressalvas)

- **OpenSubtitles (PT-BR)** — diálogo informal em quantidade enorme; precisa limpar pesado (legendas quebradas, xingamentos). Melhor como banco de inspiração de *variações de frase* do que de respostas
- **Corpus do projeto Chatterbot** — tem categoria de saudações em português (verificar estado atual do repositório; o projeto foi descontinuado, mas os YAMLs continuam úteis)
- **Transcrições TED (PT)** — linguagem mais formal; útil para perguntas factuais, não para papo
- **Seu próprio `conversas.log`** (Fase 2) — com tempo, a melhor fonte de todas: são as conversas *reais* com a KAIRA

### 11.4 Achados da 1ª sessão de uso externo (out/2026) — o log pagou o investimento

Primeira sessão real (23 turnos, testador externo). **~30% dos turnos foram sequestrados** por `cumprimentar`/`agradecimento` — e a causa **não é o fluxo novo** (o aprendizado orgânico funcionou ponta a ponta: `ENSINO P1` → `porteiro 0.240 / piso 0.1` → `absorvido` + ack; e `cancelado (fala entendida)` quando o usuário mudou de assunto). São **dados**:

| Entrada real | Caiu em | Mecanismo |
|---|---|---|
| "prazer tmbm" | agradecimento 0.48 (aposta) | "prazer" só existia no exemplo "muito obrigado o prazer e meu" |
| "não, fiquei feliz em te conhecer" | noticias 0.38 (aposta) | conhecer~saber (sinônimo legítimo) + frase ausente na base |
| "…gosta…gato…" / "tenho 4 gato" | **cumprimentar 0.52 / 0.30** | **grupo falso do dump: `["bom","gato","pao"]`** — "gato" casava com "bom dia" |
| "obrigada a todos" (via `corrigida-frase`) | **agradecimento 1.00** | 619 exemplos + fuzzy 60% → quase qualquer frase gruda num obrigado |
| "agradeço desde já" | agradecimento 1.00 | match "legítimo", mas o pool de 101 respostas respondeu "obrigada pelo almoço" |

**Causas estruturais:** (1) **pares falsos reintroduzidos pelo dump em massa** (o G2 voltou em escala — 14,3 mil grupos, com pontes como bom~gato~pão); (2) `agradecimento` com **619 exemplos** + o degrau `corrigida-frase`; (3) **ordem do arquivo como desempate** (cumprimentar era a intenção nº 1 e agradecimento a nº 17 — venciam empates); (4) pools de resposta legados e sem contexto.

**Correções aplicadas (out/2026 — dados, com validação: suíte 248 testes + avaliador 14/14 verde):**
- grupo falso `bom~gato~pão` **removido** de `grupos_sinonimos.json` (proveniência anotada na `fonte`);
- **+10 exemplos de uso real** (`intencoes-curadoria-uso-real.json`, origem `curadoria uso-real out/2026`): `cumprimentar` ganhou "prazer em te conhecer", "prazer tmbm/tambem", "igualmente", "foi um prazer", "fiquei feliz em te conhecer"; `pet` ganhou "tenho 4 gatos", "tenho um gato", "tenho dois gatos", "meu gato tem 4 anos";
- **poda do `agradecimento`: 619 → 437** (182 removidas por redundância real — token-set contido em outro exemplo do mesmo intento). Potencial semelhante **medido e não aplicado** em cumprimentar (53→40), despedida (80→61) e elogio (62→55);
- **reorder**: `cumprimentar` e `agradecimento` movidos para o **fim** do arquivo (o desempate por ordem deixa de ser deles; os exatos do avaliador são ordem-independentes);
- **par ensinado na sessão** (que havia caído em `auto_aprendizado` — e deixado a guarda do arquivo curado vermelha) **rotacionado para `saude_usuario`** ("tirei o aparelho fixo do dente…" + a explicação), preservando o ensinamento e devolvendo a caixa de entrada ao estado "nasce vazia".
- **poda complementar do `agradecimento`** (sessão de teste às cegas, leva de fechamento da sessao 01): +11 falsos amigos removidos dos exemplos (`obrigado` como ADJETIVO - "tu es obrigada a fazer isso", "sou obrigado a obter um visto", "nao quero ser obrigado a fazer isso"... - 437→425) e a frankenfrase "muito obrigado o prazer e meu"; **pool de RESPOSTAS podado 101→74** (saíram comida/objeto/carona que a IA nao pode receber - "obrigado pelo almoço", "obrigado pela carona", "obrigado pelo livro" - e sarcasmos - "obrigado por nada", "obrigado de nada"); "obrigado a todos" (gratidão legítima) permanece

**Ficou de fora (anotado para depois):**
- **Código:** `corrigirFrases` só deveria aceitar correção que **melhore** o score (hoje gruda por 60%); e o `ENTRADA` do log **perde o texto quando a fala tem quebra de linha** (vários turnos logados com entrada vazia + texto na linha seguinte — atrapalha a mineração do §11.1);
- **Tensão de design (decisão pendente):** o aprendizado em runtime grava no **mesmo** `intencoes.json` curado — qualquer sessão real deixa `auto_aprendizado` com dados e a guarda do arquivo curado fica vermelha. Opções: (a) arquivo próprio de aprendizados (`aprendizados.json`) mesclado na carga; (b) reset pré-commit do inbox; (c) a guarda comparar só o HEAD;
- **Casos restantes da sessão:** "sabe quem é o X?" caiu em `habilidades` (candidato ao dicionário C3); `"como vc é?"` caiu em `identidade` pelo radical de stopword "como" (ambíguo por natureza — status × identidade).

**2ª rodada — 20 baterias de teste às cegas (out/2026, `logs/baterias/`):**

> 20 personas × ~21 falas; as 10 primeiras pelo `kaira.jar` (**base antiga**), as 10 últimas pelo código-fonte (**base com o refino**). Média **3,2/10** (social 5,5 · naturalidade 4,0 · intenção 3,5 · contexto 3,5 · relevância 3,0 · **empatia 2,5 — o pior**). Medição minha nos **432 turnos**: **40% caíram em `aposta`** (só 34% `direto`) — o "erra o alvo com frequência" do relatório, quantificado.

- **`kaira` → piada (11 ocorrências) — CORRIGIDO (dados):** o token raro `kaira` (do MEU exemplo "piada ruim essa kaira") dominava o score. **`kaira` virou stopword** (o nome do bot não carrega assunto) → "tchau kaira" → despedida, "oi kaira" → cumprimentar.
- **⚠ Regressão da MINHA poda por subconjunto — CORRIGIDA (dados):** ela removeu **79 âncoras canônicos** (`obrigado`, `obrigada`, `valeu`, `de nada`, `muito obrigado`…) por serem subconjuntos de exemplos maiores — sem o âncora de **resto 0**, o empate caía em `elogio` (exemplo "obrigado por existir"). Poda **revertida** (425 → 607), mantida a curadoria semântica do autor. **Lição:** podar por **subconjunto** quebra o desempate por resto/ordem do classificador; só **duplicata exata** é segura — o alvo "619 → ~250" fica **revisto**.
- **Pool do `agradecimento` invertido (35 respostas nas baterias) — CORRIGIDO (dados):** 65 de 74 respostas eram ela **agradecendo** quando o usuário agradecia ("obrigado por X" — papel trocado). Pool **74 → 19**, só aceitações ("De nada :)", "Por nada, foi um prazer." + 10 novas curadas na voz dela).
- **Empatia (nota 2,5 — o pior) — ABERTO:** conteúdo sensível caía em resposta alegre/fora de contexto ("meu marido faleceu" → "Boa! … Como você comemorou?"). O refino já empurrou para **fallback honesto**, mas **não há camada acolhedora** — proposta de intento dedicado (dados, texto sob aprovação do autor) + camada de segurança emocional (código) em avaliação.
- **Token comum decide o match — CÓDIGO:** 29 matches por 1 token (`eh`, `foi`, `tem`, `esta`, `amo`…) e 40% de apostas. O fix é tratar tokens comuns como stopword no matching e **exigir ≥2 tokens de peso** para vencer.
- **Captura de nome indevida (32 capturas, 9 ruins) — CÓDIGO:** "sou corinthiano roxo", "sou enfermeira", "kkkk", "bom dia" viraram nome. Lista de bloqueio (saudações/riso/profissões) + confirmação.
- **Aprendizado aceita qualquer coisa (4 eventos, score 0.22–0.29 ≥ piso 0.1) — CÓDIGO:** absorveu até um bug-report ("às vezes você responde coisa sem sentido") e uma equação. Dado real para **calibrar o `piso`** (ou exigir confirmação).
- **Outros (código):** `Runtime.exec` (G6/§Fase 0), ENTER residual "\n" na UI, linha de humor do `stdout` sem timestamp no log.

---

## 12. Ideias em estudo (backlog)

> Brainstorm do usuário (out/2026), anotado sem detalhar — forma e ordem a decidir depois. Nenhuma virou issue; a **#20 continua reservada pra persistência** (§6.1).

### 12.1 Contexto antes das intenções + dicionário de conhecimentos

- Na hora de responder, a KAIRA deveria olhar o **contexto** (tópico/história da sessão) **antes** de ir pra cascata de intenções.
- Encher a base de frases prontas não resolve no longo prazo (mesma conclusão do diagnóstico do §2: *"adicionar mais frases soltas não resolve, só escala o problema"*). O que faltaria: uma **base grande de conhecimentos, tipo dicionário** — termo → texto longo sobre o tema.
- Com uma lógica de transformação (ideia: usar a lista de **stopwords** como base da extração — lógica ainda a pensar), virar o texto longo numa **frase simples**; se o usuário pedir explicação maior, aí devolver o texto inteiro.
- Encaixes já existentes: `conhecimento_*` (4 intents de frases prontas = mini-dicionário de hoje); `palavras.json`/`ControlePalavra` é dicionário de *palavras* pra correção (outro papel); `stopwords.json` + pesos IDF (`ControleRespostaPesosIdf`) são a infra pronta pra pontuar conteúdo; parecido com §8 (geração própria) e §9 (LLM) — decidir se é regra própria ou skill.
- **Bases livres levantadas (out/2026)** — a primeira combinação: **OpenWordnet-PT** pro *significado curto* + **dump da Wikipédia PT** (lead por tema) pro *texto longo* do resumidor:
  - *OpenWordnet-PT* (`github.com/own-pt/openWordnet-PT`) — **CC BY 4.0** (só crédito): glosses em PT-BR, sinônimos, relações semânticas; pacote ~2,2 MB; citar Rademaker et al. (2012);
  - *Dicionário Aberto* (`dicionario-aberto.net` + API REST grátis) — fonte em domínio público (Cândido de Figueiredo, 1913), downloads CC BY-SA 2.5 PT — português antigo/ortografia pré-1945, usar com pé atrás;
  - *Wikcionário via Kaikki* (JSONL/SQLite) — CC BY-SA 4.0; *DicSin* (sinônimos pt-BR) — GPLv2; catálogo `linguistic-datasets-pt.etica.ai` pra garimpar mais;
  - **Evitar**: Priberam/Michaelis/Houaiss/Infopédia/Dicio (grátis só na consulta, dados licenciados), BabelNet (não-comercial), scrapes de terceiros;
  - Licença rastreada por entrada no campo `origem` (Apêndice A) — CC BY = crédito; CC BY-SA = crédito + mesma licença no derivado;
  - Arranque previsto: recorte dessas bases → `KAIRA/resources/conhecimentos.json` no padrão do projeto (verificar licenças de novo no momento da importação).

### 12.2 Continuação por contexto ("conte-me mais sobre isso")

- ✅ **Entregue (issue #27, out/2026)** — intenção `continuacao_topico` com os literais da continuação ("fala mais disso", "conta mais sobre isso", "e depois o que aconteceu"...), e o hook de tópico (`melhorIntencaoDetalhado`) pula esse intento: o `topicoAtual` sobrevive e as respostas com `{topico}` montam a reação sobre o tema anterior. **#28 fechou a experiência:** copa ancorada em `{topico}` na voz dela (reage em vez de devolver a bola), `perguntaInteresse` segura o pool genérico no turno do pedido (a fonte 1 — followups do tema — passa) e literais `conta mais`/`me fale mais` (vazavam pra piada/convite). Respostas compostas com substância do tema (fragmentos) seguem pendentes.
- Frase como "conte-me mais sobre isso" não tem frase-fato própria: a KAIRA olharia o `topicoAtual` (existe desde a #19), veria que o último tópico era X e montaria a resposta sobre X (puxando do dicionário da 12.1).
- "Montar uma frase inacabada" = resposta **por fragmentos**, não frase inteira pronta — linha já prevista no plano: respostas compostas `[reação ao tópico] + [followup]` (§7.1 e o checklist de `followups` do §6).
- Encaixes prontos: `Sessao.topicoAtual`/`historico` (issue #19); desempate por contexto (pendência do §5.1); campo `followups` **em uso desde a issue #25** (`perguntaInteresse`); placeholders `{topico}` (pendência do §6).

### 12.3 Ideias do usuário (out/2026) — identidade, memória e aprendizado orgânico

> Brainstorm do autor em out/2026. Anotadas aqui antes de virarem issue; a **Ideia 3** saiu na #48 (entregue - ver status no fim da secao); as demais nenhuma começou. Depende do trilho novo (#42–#45) que fecha depois de #39–#41.

**Ideia 1 — Identificar tipos de pergunta + memória dela em `kaira.json`.**
A KAIRA precisa tipar a pergunta e reagir por família:

| Família | Exemplo | Estado hoje |
|---|---|---|
| Conceito | "o que é dinheiro?" | existe (`duvida_dinheiro`…) |
| Sobre o usuário | "o que eu gosto?" | existe (fatos em `Sessao.fatos` + `PADROES_FATO`) |
| **Sobre ela** | "qual sua cor preferida?", "o que você gosta?" | **não existe — é o novo** |

O `kaira.json` já existe (`KAIRA/resources/`, lido pelo `ControleKaira`) mas só tem `{"nome":"K.A.I.R.A","versao":"0.0.1"}` — é o lugar certo pra crescer os **fatos da KAIRA** (mesmo padrão do `sessao.json`, que já persiste os fatos do usuário). Fluxo-piloto "cor favorita": pergunta sobre ela → busca nos fatos dela → acha, responde; não acha → "quais são as minhas opções?" → usuário lista → ela escolhe e grava. Caso bom de piloto; a lógica geral nasce como **fato com opções conhecidas vs. fato aberto**.

**Ideia 2 — Respostas compostas da Fase 4 (§8.1) pra responder de memória.**
Pra dar resposta curta e fresca sobre preferência dela, usar respostas **parametrizadas compostas** (`[saudação] + [reação ao tópico] + [followup]`, §8.1) — peça do fato ("verde") + moldura sorteada → frase nova a cada vez, sem repetir banco e sem LLM. Combate o "primeiros 30s é bom, depois fica chato". O `CriarResposta` (`ControleResposta:668`, troca palavra por sinônimo) existe e hoje joga o retorno fora — é infra aliada, não a Fase 4 em si. **Markov (§8.2) fica em aberto:** validar o ganho real antes de decidir se entra.

**Ideia 3 — Aprendizado orgânico (sem "parar" a conversa).**
Hoje o modo ensino (#33) "para" a conversa: o próximo turno vira resposta sem aviso. Inverter: quando ela não entende um tópico, **ela pergunta**, e a resposta do usuário é absorvida pelo contexto (tópico + tipo da pergunta) direto pro intent certo — sem entrar em "modo". Mesma tese do desempate por contexto (#41) do outro lado: lá o contexto decide **qual intent responder**, aqui decide **onde guardar** o que o usuário disse. Por isso **#41 vem antes**.

**Ordem natural:** fechar #39–#41 → trilho #42–#45 (perguntas, fatos dela, Fase 4-piloto, aprendizado orgânico). A validar o ganho real do Markov e do piloto do dicionário (C3) antes de escalar qualquer um.

**Ganho real das 3 ideias (análise out/2026, base em 98 intenções / 2.420 exemplos / 1.291 respostas):**

> Análise feita contra o estado atual — cada ideia foi confrontada com o que **já existe**, para separar o ganho real do ganho imaginado. O achado transversal está no fim.

**Ideia 1 — fatos dela / consistência: ganho ALTO em personalidade, escopo MENOR do que parece.**
- *O que já existe (não é o novo):* a **tipagem da pergunta** já está feita por intents — `"qual a sua cor preferida"` é **exemplo exato** de `gostos_ia` (19 ex, 21 resp) e o avaliador congela esse caso como regressão; `"o que você gosta?"` cai em `gostos_ia`. O "tipo de pergunta" não é o buraco.
- *O buraco real:* (a) **memória das preferências dela** (`kaira.json` tem só `{nome, versao}`) e (b) o **fluxo interativo** (ela pergunta as opções → usuário lista → ela escolhe e grava).
- *Por que vale:* **consistência temporal** — hoje ela sorteia entre 21 respostas e pode dizer "verde" hoje e "azul" amanhã, o que quebra a ilusão de pessoa. Fato persistido = a mesma coerência que o `sessao.json` já deu ao usuário. Bônus: **inversão de papéis** (o usuário define o gosto dela) — mecânica de vínculo, simétrica ao "ela lembra de você".
- *Riscos:* **coerência de persona** (ela se define como IA "sem corpo, sem ver cores" — a resposta precisa reconciliar: "não vejo cores, mas se eu escolhesse, seria o verde que *você* me deu"); e o fluxo de opções é **multi-turno com estado** → é a **mesma máquina da Ideia 3/#41** (não construir duas).
- *Métrica:* coerência entre sessões (mesma pergunta em 2 execuções → mesma resposta) + taxa de conclusão do fluxo de opções.

**Ideia 2 — respostas compostas: ganho MÉDIO e LOCALIZADO.**
- *Onde é ouro:* nas famílias **resposta-por-fato** — `lembra_*` tem **4 respostas cada** e são templates com slot; `[moldura sorteada] + {fato:X}` multiplica 4 → dezenas de variações quase de graça, justamente onde a repetição dói (o usuário testa "você lembra?" várias vezes).
- *Onde NÃO vale:* como substituto geral dos pools — o ganho grande de variedade o projeto comprou **com dados** (a leva out/2026: +156 respostas, faixa-a-faixa). Composto por cima é multiplicador, não fonte primária.
- *Alerta:* **`CriarResposta` NÃO deve ser o motor** — trocar palavra por sinônimo aleatório é a antiga "roleta" que o diagnóstico matou (G2). O motor certo é **moldura curada + slot**.
- *Markov:* ver o refinamento na **[§8.2](#82-cadeia-de-markov-para-curiosidade)** (filler do ciclo; per-intent > base inteira; corpus pequeno não sustenta geração agora; conectores curados + `{topico}` pegam o efeito).
- *Métrica:* taxa de repetição intra-conversa — a infra do **#35** já mede.

**Ideia 3 — aprendizado orgânico: ganho ALTO — o maior dos três em UX percebida.**
- *Por quê:* mata o **sequestro do próximo turno** (hoje o fallback arma o `!` e a fala seguinte do usuário é consumida como resposta, sem aviso — se ele mudar de assunto, o assunto vira resposta); transforma a mina de dados do §11.1 em coleta natural; e casa com o **#41** (lá o contexto decide *qual* intent responde, aqui *onde* guardar — mesma tese invertida).
- *Reuso:* o #33 já resolveu o "onde guardar" por score (≥0,6 → intent certo).
- *Riscos:* **ruído de aprendizado** (se ela pergunta "o que é X?" e o usuário responde "sei lá, tô com sono", guardar envenena a base → precisa de porteiro: score do #33 + confirmação) e **ambiguidade de turno** (sem o modo explícito, como saber que a fala seguinte é a resposta? — é exatamente o que o **#41** resolve; a dependência declarada está certa).
- *Métrica:* queda do "turno sequestrado" + taxa de aprendizado aproveitado (a linha `APRENDE` do log já mede).

**Desenho escolhido — aprendizado orgânico (out/2026, decisão do autor; em avaliação com um segundo agente — mais opiniões são bem-vindas):**

*O problema hoje (medido no código):* o fallback chama `enviarResposta(null)` → `ativarAprendizado()` liga `ControleFace.verificarManter` **na UI**, e o `MandarFrase` intercepta a **próxima fala antes de tudo**: ela vira `manterResposta` → `chamadaFrase(buffer, fala)` incondicionalmente. Sequestro de 1 turno: se o usuário mudar de assunto, o assunto vira resposta de ensino, e comando/nome/aposta também são comidos (o `decifrar` nunca roda).

*Três decisões do autor (out/2026):*
1. **Roteamento — o pipeline julga.** Fala **entendida** (comando, resposta de nome, confirmação de aposta ou match ≥ `LIMIAR` 0.6) → responde **normal** e **cancela o ensino pendente**. Fala **não entendida** com ensino pendente → é a resposta do ensino → passa pelo porteiro.
2. **Fala ao absorver — ack curto curado** (entrada nova no pool de fallback; texto escrito pela casa, **autor aprova**).
3. **Porteiro por score** — só absorve se o score do #33 **sobre o buffer** ≥ `piso`; abaixo, **não guarda ainda**: ela pede confirmação na hora.

*Máquina (2 estados, 2 strings — sem flag nova de boolean):*
- **P1 (armado):** `bufferFrases != null && respostaEnsino == null` — esperando a resposta.
- **P2 (confirmação):** `bufferFrases != null && respostaEnsino != null` — a resposta chegou mas ficou **retida em espera** do porteiro.

*Fluxo — todo dentro de `decifrar` (estado sai da UI; lição do #45: UI nunca é pré-condição do dado):*
1. Fala não entendida, **sem** ensino pendente → armadura atual intacta (pool "me ensina?") → **P1**.
2. Fala não entendida com **P1** → `score = melhorIntencaoDetalhado(base, buffer).getScore()`:
   - `score ≥ piso` → absorve: `chamadaFrase(buffer, fala)` — o gate do #33 dentro de `ensinaEm` decide **onde** guardar (≥ 0.6 → intento certo, senão `auto_aprendizado`) — + **ack** + limpa estado;
   - `score < piso` → **P2**: `respostaEnsino = fala` (retida), **pergunta confirmação** (nada é gravado ainda).
3. **P2** + confirmação (vocabulário **existente** `ControleSessao.ehConfirmacao`, o mesmo do aposta #39) → absorve o par retido + ack + limpa.
4. **Qualquer fala entendida** (regra 1) → responde normal e **limpa** `bufferFrases`/`respostaEnsino` — o sequestro morre. Pontos de limpeza: `enviarResposta` com resposta não-nula (cobre nome, aposta e todos os degraus) + o ramo de comando.
5. `MandarFrase` **para de rotear**: sempre chama `decifrar`; `verificarManter` vira só brilho visual do botão.

*Peças reusadas — nenhuma máquina nova:* o gate do #33 (`ensinaEm`, LIMIAR 0.6) decide onde guardar · o pipeline de degraus inteiro decide "entendeu" · `ehConfirmacao` (#39) decide a confirmação · `bufferFrases` já é o estado · gravação (`intencoes.json` + `palavras.json`) e `invalidaCacheIdf` vêm de `chamadaFrase` como hoje.

*Em aberto (pontos de discussão pra segunda opinião — números e textos, não arquitetura):*
- **`piso`:** a faixa de fallback é `< LIMIAR_MEDIA` (0.3), então **todo buffer chega com score < 0.3** — o piso tem que separar **lixo puro** ("blorf quantum zebra") de **frase topical não casada** ("qual seu carro favorito"). Valor sai de **medição** (distribuição de scores por probe), não de chute.
- **Textos:** ack de absorção e pergunta de confirmação — curados, autor aprova.
- **Negação em P2:** "não" explícito → descartar o par (lista curada no padrão `CONFIRMACOES` do #39, autor aprova) vs. deixar cair no fluxo normal. Sem negação, qualquer fala não-entendida em P2 **descarta o par e vira novo P1** (a conversa segue; nada é gravado sem confirmação).
- **P2 × aposta pendente ao mesmo tempo:** estado raro (aposta só arma na faixa média); definir ordem de checagem — `entregaApostaPendente` já roda cedo no `decifrar`.

**Segunda opinião (agente de dados, out/2026) — veredito e calibragem:**

> 🔎 **Veredito: aprovado.** O desenho troca o certo pelo certo — "modo que suspende o pipeline" vira "estado que o pipeline consulta" — e mata a pior aresta viva (o sequestro de 1 turno). Minimalista no melhor sentido (2 states, peças reusadas, estado fora da UI). Os pontos abaixo são **calibragem e textos, não arquitetura** — na linha do que o autor já classificou.

> ✅ **Status: ADOTADO INTEGRALMENTE pelo autor (out/2026)** — inclusive a **saída (a)** no ponto sensível: regra 1 mantida (≥ 0.6 cancela) **+ linha de ack transparente** no cancel ("deixa pra lá") — a tentativa de ensino não morre mais calada. Vira escopo fechado da issue: log instrumentado (`ENSINO :` + score do porteiro), pools próprios de ack, as 3 specs E2E de aceitação e a invariante **"nada grava sem sim explícito"** no spec. Ficam só pra curadoria do autor, na hora da receita: os **textos** (ack de absorção/eco, ack de cancel, ack de negação, pergunta de confirmação) e o valor do **`piso`** (medido pelas 3 famílias acima).

- **⚠ O gatilho `match ≥ 0.6` na regra 1 é o ponto sensível.** Uma *resposta* a um "me ensina?" pode ser entendida pelo pipeline: ela pergunta sobre "carro"; o usuário responde "gosto muito de carro" → casa ≥ 0,6 → a regra 1 **cancela o ensino e responde normal**, e a tentativa de ensinar morre **calada**. Saídas: (a) aceitar e deixar o **ack dizer** ("beleza, deixa pra depois") — mantém "o pipeline julga", custo zero de estado; (b) em P1, tirar "≥0,6" dos cancelamentos e deixar o **porteiro** julgar (só comando / resposta de nome / aposta confirmada cancelam). Recomendação: **(a)**.
- **`piso` por medição de 3 famílias (método, não chute):** (i) lixo puro ("blorf quantum zebra") · (ii) topical não casado ("qual seu carro favorito" — casa fraco em `transito`) · (iii) os fallbacks **reais** do log (depois da higiene C2). Se houver **vão** entre (i) e (ii)/(iii), o piso sai dele.
- **O ack de absorção não deveria morar no pool de fallback.** Ele não é fallback (ela *entendeu o que aprendeu*): pool próprio (`FRASES_ABSORVIDO`, 3–4 frases) com anti-repetição no padrão `ultimoFallback`. Opcional de encanto barato: o ack **ecoa** o que aprendeu ("anotei: {fala} — obrigada!").
- **Negação em P2 — confirmar a invariante no spec:** *nada grava sem um "sim" explícito.* Negação → descarta o par, ack leve, segue (sem virar novo P1 silencioso).
- **Instrumentar o estado no log** (cultura #29/#40): linhas `ENSINO : P1/P2`, `absorvido/reteído/descartado` + o **score do porteiro**. É o que torna a métrica do ganho ("taxa de aprendizado aproveitado") medível e alimenta a calibração do piso.
- **Sequenciamento — boa notícia:** o desenho **quase não depende do #41**. Ele roteia por "o pipeline entendeu?", não pelo desempate por contexto; as peças que usa (cascata, #33, `ehConfirmacao` do #39, `bufferFrases`) **já existem**. A dependência real é só o **#39** (pro `ehConfirmacao`) — pode começar antes do que a nota original sugeria.
- **Aceitação = specs E2E** (o buraco que o `MAPA_DE_TESTES.md` mediu: ciclo de vida N→N+1): *fallback → resposta → absorvido* · *fallback → lixo → P2 → confirma/recusa* · **e o que mata o bug atual: fallback → usuário muda de assunto → NADA é gravado**. Passando esses três ponta a ponta, o desenho está provado.

**Status (out/2026): ENTREGUE na #48** - o desenho saiu como escrito, com a segunda opiniao adotada integralmente: maquina P1/P2 dentro do `decifrar` (novo campo `respostaEnsino`), porteiro com `piso = 0.10` (medido pelo ProbePiso: lixo puro 0.000, topical 0.192+), vocabulario proprio de sim/nao do ensino, acks curados em pools proprios, `MandarFrase` sem roteamento (so brilho visual) e log `ENSINO :` com o score do porteiro. Spec `EnsinoOrganicoTest` (6 E2E na base real isolada) cobre as 3 aceitacoes + a invariante "nada grava sem sim explicito"; a ordem P1/P2 antes da captura de nome ficou travada em spec. A conversa real de 18/10 (o loop do elefante e a recuperacao de musica) virou a spec `FluxoConversaReal18Test` (3 E2E na base real isolada, par medido no dicionario real), e o par ensinado ao vivo subiu por curadoria pro intento `curiosidade` com o erro de digitacao corrigido ("lebrar" -> "lembrar"), zerando a caixa de entrada (a guarda do #8 continuou verde, pegou o item no inbox). Suite em **50 classes / 248 testes**, regressao 14/14 - o loop completo (fallback, absorvido, respondeu) aconteceu no uso real de 18/10 e esta congelado em spec.

**Ranking e o achado transversal:**

| # | Ideia | Ganho real | Depende de |
|---|---|---|---|
| 1º | Aprendizado orgânico | Alto (UX imediata; o "hijack" do turno é a pior aresta de hoje) | #41 (na ordem certa) |
| 2º | Fatos dela / consistência | Alto (personalidade; escopo menor do que parece) | mesma máquina de contexto |
| 3º | Respostas compostas | Médio e localizado (famílias de fato; Markov ver §8.2) | #35 pra medir |

- **As três são da mesma família (estado/contexto) e precisam da MESMA peça ausente** — a máquina de multi-turno que o **#41 vai provar**. Feitas em paralelo = 3 máquinas; feitas depois do #41 = **1 máquina, 3 usos**. É o argumento mais forte para não furar a fila — a ordem do plano está certa.
- **Ajuste de expectativa:** a base acabou de resolver **variedade com dados**; essas ideias atacam **coerência/estado**. Eixo diferente, complementar — nenhuma "conserta" o que a base cobre; todas *dependem* de a base estar boa (agora está). O ganho delas é sobre **parecer alguém**, não sobre **entender mais**.

**Adiamentos (out/2026 — decisão do autor, o foco é a Ideia 3 acima):**

- **Desempate por intent/topo — adiado** (era o candidato a issue seguinte). Ideia do autor: no empate da correção, decidir pelo intent/topo dono da palavra usando o ranking do **1º degrau**, que **já rodou antes da correção** (estado zero novo — reuso puro, sem mexer no matcher). Dado na mesa (medido na #46, dicionário de 2115): empate em **6,4%** dos erros de 1 edição e, lá, a ordem só acerta **44%**. É **refino de algo que meio que já funciona**; a melhoria importante é o aprendizado. Se voltar: **re-medir o `ProbeEmpates` no dicionário de 20673** (o 6,4% é do arquivo antigo, sem os 18660 sinônimos).
- **Higienizacao da frase antes de gravar o ensino - adiada** (medido no uso real de 18/10: o autor escreveu "lebrar" e a KAIRA absorveu o erro, e o `chamadaFrase` ainda registrou a palavra errada no dicionario via `gravaPalavra`). Tratamento conservador: corrigir pergunta, resposta e o eco do ack ANTES de gravar, so com confianca do dicionario (mesma regua do #46); palavra genuinamente nova passa direto - o catch de desconhecida e o feature, nao o bug.
- **Uniformização de docs — adiada:** a célula de **UI** do MAPA (❌ — "não há teste do `decifrar` entregando na Face"), a cláusula **"não polui o tópico"** do ensino que a spec #45 **não** asserta (MAPA R1.3 — o teste isola a base justamente pra não vazar), e o **item stale** do §5.1 (o "Em correção pela casa" da âncora no match exato — o caminho do app já cobre, o texto não foi atualizado).

---

## Apêndice A — Esquema JSON proposto

### `intencoes.json` (substitui `respostas.json`)

```json
{
  "intencoes": [
    {
      "intento": "cumprimentar",
      "exemplos": [
        {"texto": "oi", "origem": "respostas.json"},
        {"texto": "ola", "origem": "respostas.json"},
        {"texto": "e ai", "origem": "respostas.json"},
        {"texto": "bom dia", "origem": "respostas.json"},
        {"texto": "oi tudo bem", "origem": "respostas.json"},
        {"texto": "salve", "origem": "autor"},
        {"texto": "opa", "origem": "autor"}
      ],
      "palavrasChave": ["oi", "ola", "salve", "opa", "bom dia"],
      "respostas": [
        {"texto": "Oi! Como você está hoje?", "origem": "respostas.json", "humorMin": 0, "humorMax": 10},
        {"texto": "Oiê! Que bom te ver por aqui!", "origem": "autor", "humorMin": 7, "humorMax": 10},
        {"texto": "oi.", "origem": "respostas.json", "humorMin": 0, "humorMax": 3}
      ],
      "followups": ["E você, como está?", "Como foi seu dia?"],
      "acoes": []
    },
    {
      "intento": "informar_versao",
      "exemplos": [
        {"texto": "qual a sua versao", "origem": "respostas.json"},
        {"texto": "versao atual", "origem": "autor"},
        {"texto": "que modelo voce e", "origem": "autor"}
      ],
      "palavrasChave": ["versao", "modelo", "atual"],
      "respostas": [{"texto": "Estou na versão {versao}!", "origem": "autor", "humorMin": 0, "humorMax": 10}],
      "followups": [],
      "acoes": ["MOSTRAR_VERSAO"]
    },
    {
      "intento": "elogio",
      "exemplos": [
        {"texto": "voce e legal", "origem": "respostas.json"},
        {"texto": "gosto de voce", "origem": "respostas.json"},
        {"texto": "voce e demais", "origem": "respostas.json"},
        {"texto": "amo voce", "origem": "autor"}
      ],
      "palavrasChave": ["legal", "demais", "amo", "gosto de voce"],
      "respostas": [{"texto": "Obrigada, {nome}! Isso me deixou feliz (o mais perto disso que consigo).", "origem": "autor", "humorMin": 4, "humorMax": 10}],
      "followups": ["O que mais você gosta em mim?"],
      "acoes": ["HUMOR_MAIS_1"]
    }
  ]
}
```

Notas do esquema:

- `origem` rastreia a proveniência de cada exemplo e resposta: `respostas.json` (original do projeto), nome do dataset + licença (ex.: `Tatoeba (CC BY 2.0 FR)`, `RichardSakaguchiMS/... (Apache-2.0)`) ou `autor` (frase escrita na curadoria). Além de depuração ("de onde veio essa frase?"), é o que garante a atribuição exigida pelas licenças — CC BY exige crédito item a item. `palavrasChave` e `followups` são derivados/escritos, não têm origem externa
- `palavrasChave` pontuam mais que `exemplos` (podem nem existir em `exemplos`)
- `humorMin/humorMax` filtram a resposta pela faixa de humor atual (Fase 3)
- `acoes` substituem os `assuntos` mágicos atuais (`"comando de desligamento"` → `System.exit(0)` escondido em string): ação explícita, auditável, testável
- `followups` alimentam a iniciativa da Fase 3

### `grupos_sinonimos.json` (substitui `sinonimos.json`)

```json
{
  "grupos": [
    ["oi", "ola", "salve", "opa", "eae", "e ai"],
    ["voce", "vc", "tu"],
    ["gostar", "curtir", "amar", "adorar"],
    ["legal", "bacana", "massa", "daora", "maneiro"],
    ["preferido", "favorito"],
    ["tchau", "xau", "flw", "ate mais", "ate logo"]
  ]
}
```

Convenção: **primeiro elemento = forma canônica** (é ela que fica no índice do classificador).

---

## Apêndice B — Exemplos de código

### B.1 Normalizador (Fase 0)

```java
public final class Normalizador {
    public static String texto(String entrada) {
        if (entrada == null) return "";
        String s = java.text.Normalizer.normalize(entrada, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")          // remove acentos
                .toLowerCase()
                .replace("?", " ").replace("!", " ").replace(",", " ")
                .replace(".", " ").replace(";", " ").replace(":", " ")
                .replaceAll("\\s+", " ").trim();
        return s;
    }
}
```

### B.2 Levenshtein (Fase 1 — substitui `calculoPorcentagemPalavra`)

```java
public static int levenshtein(String a, String b) {
    int[][] d = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) d[i][0] = i;
    for (int j = 0; j <= b.length(); j++) d[0][j] = j;
    for (int i = 1; i <= a.length(); i++) {
        for (int j = 1; j <= b.length(); j++) {
            int custo = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
            d[i][j] = Math.min(
                Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                d[i - 1][j - 1] + custo);
        }
    }
    return d[a.length()][b.length()];
}
// uso: levenshtein("ola", "ila") == 1 → provável erro de digitação de "ola"
```

### B.3 Classificação por Jaccard (Fase 1 — versão mínima)

```java
// frases já normalizadas + stemmed + sem stopwords → Set<String> de palavras
public record Match(String intento, double score) {}

public static Match classificar(Set<String> palavrasUsuario, List<Intencao> intencoes) {
    Match melhor = new Match(null, 0);
    for (Intencao intencao : intencoes) {
        Set<String> alvo = intencao.conjuntoDePalavras(); // exemplos + palavrasChave, stemmed
        Set<String> intersecao = new HashSet<>(palavrasUsuario);
        intersecao.retainAll(alvo);
        double uniao = palavrasUsuario.size() + alvo.size() - intersecao.size();
        double score = uniao == 0 ? 0 : (double) intersecao.size() / uniao;
        if (score > melhor.score()) melhor = new Match(intencao.getIntento(), score);
    }
    return melhor;
}
// >= 0.5 aciona | 0.25–0.5 aposta+confirma | < 0.25 fallback
```

Evolução: trocar Jaccard por **TF-IDF** — peso de cada palavra = `log(N / documentosQueContem(palavra))`, somado nos acertos; palavras raras discriminam melhor a intenção.

### B.4 Chamada ao Ollama (Fase 5)

```java
// POST http://localhost:11434/api/chat
{
  "model": "qwen2.5:7b",
  "stream": false,
  "messages": [
    { "role": "system", "content": "Você é a KAIRA, uma IA companheira carinhosa e curiosa. Humor atual: 7/10. O usuário se chama João." },
    { "role": "user", "content": "oi, lembra de mim?" }
  ]
}
```

```java
public class ClienteLLM {
    private final HttpClient http = HttpClient.newHttpClient();
    private final URI uri = URI.create("http://localhost:11434/api/chat");

    public String conversar(List<Msg> historico) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
            "model", "qwen2.5:7b",
            "stream", false,
            "messages", historico));
        HttpRequest req = HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .timeout(Duration.ofSeconds(60))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new ObjectMapper().readTree(resp.body()).path("message").path("content").asText();
    }
}
```

---

## Apêndice C — Esforço estimado

| Fase | Tempo (dedicação leve) | Risco/observação |
|---|---|---|
| 0 | 1–2 dias | Mexer em caminhos pode quebrar o 1º boot — testar instalação do zero |
| 1 | 1–2 semanas | A conversão das 250 frases é o trabalho chato; classificador é rápido |
| 2 | ~1 semana | Regex de nome/fatos pede paciência; começar só com nome + histórico |
| 3 | 1–2 semanas | Subjetivo — ajustar tons exige "provar" a KAIRA conversando |
| 4 | ~1 semana (opcional) | Não estender: retorno baixo se a meta é a Fase 5 |
| 5 | 2–5 dias de integração | Depende do hardware para o modelo local; API paga em 1 dia |

Total até conversa livre (Fases 0–3 + 5, sem a 4): **~4–7 semanas** em ritmo de projeto pessoal.

---

## Apêndice D — Checklist de decisões

Decisões que mudam o percurso — responder antes de começar:

- [ ] **Hardware:** a máquina roda um modelo 7–8B? (≈8 GB RAM livre; se não, plano B: modelo 3B, qualidade menor, ou API paga)
- [ ] **Privacidade:** tudo precisa ser 100% offline/local? (se sim → caminho A ou C da Fase 5)
- [ ] **Orçamento:** aceita pagar centavos por conversa? (se sim → caminho B viável)
- [ ] **Objetivo real:** (a) ter uma IA de conversa funcionando o quanto antes, ou (b) aprender NLP/ML no processo?
  - (a) → ir direto: Fases 0 → 1 → 2 → 5 (pular 3 e 4 no começo)
  - (b) → incluir Fases 3 e 4 de propósito — é onde se aprende os fundamentos
- [ ] **Idioma:** manter 100% PT-BR? (modelos 7–8B modernos são bons em PT, mas exemplos/avaliações em PT ajudam muito)
- [ ] **Escopo de "consciência":** deixar claro (pra você mesmo) que humor/memória são **modelagens** — úteis para a experiência, não sentimentos reais

---

*Documento vivo: atualizar conforme as fases forem concluídas. Cada fase concluída = tag git `v0.x-faseN`.*
