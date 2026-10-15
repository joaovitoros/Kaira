# KAIRA — Plano de Evolução

> KAIRA — assistente de conversa
>
> Versão do documento: **1.0** — Outubro/2026
> Escopo: diagnóstico da base atual + roadmap completo até conversação livre
> Estado do projeto na data deste documento: v0.0.1, ~250 frases, 102 sinônimos, 456 palavras registradas
>
> **Atualização de progresso (out/2026):** tarefas já entregues estão com `[x]` e nota com issue/commit. **Fase 0** concluída (menos G6 e Gradle); **Fase 1** concluída (§5.1–§5.7 — a última, frame+slot, na issue #18; §7.3 parcial na #17 e descartados à parte); **Fases 2–5** intactas. Issues #1–#18 fechadas (github.com/joaovitoros/Kaira), 25 commits em `master`, avaliador de regressão 14/14 com 0 gaps.

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
12. [Apêndice A — Esquema JSON proposto](#apêndice-a--esquema-json-proposto)
13. [Apêndice B — Exemplos de código](#apêndice-b--exemplos-de-código)
14. [Apêndice C — Esforço estimado](#apêndice-c--esforço-estimado)
15. [Apêndice D — Checklist de decisões](#apêndice-d--checklist-de-decisões)

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

> **Status (out/2026):** G1 resolvido · G2 resolvido · G3 resolvido · G4 pendente · G5 resolvido · G6 pendente · G7 resolvido · G8 resolvido · G9 parcial · G10 parcial

| Gargalo | Status | Resolvido em |
|---|---|---|
| G1 frase-exata | resolvido | matcher por cobertura ponderada por IDF sobre intenções (issues #5, #6, #11, #14) |
| G2 sinônimos roleta | resolvido | grupos no matcher (issue #9); procuraSinonimo deletado; geração com guarda de stopword (#13) |
| G3 sem normalização | resolvido | Normalizador.texto + stem de 12 regras + 54 stopwords nos dois lados (issues #2, #12) |
| G4 sem memória | pendente | Fase 2 (§6.1 Sessão) |
| G5 UI apaga conversa | resolvido | histórico acumula via append (issue #1); auto-scroll/separador/log pendentes |
| G6 Runtime.exec | pendente | backlog de segurança |
| G7 caminhos relativos | resolvido | Caminhos.recurso() resolve a base de qualquer diretório (issue #4, CaminhosTest) |
| G8 similaridades frágeis | resolvido | Levenshtein + frase palavra-a-palavra (issue #15) |
| G9 código morto/NPE | parcial | decisao passou a ser usado; prints e placeholder de humor seguem vivos |
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
- `"nivel de humor em... de 10"` — placeholder do humor que nunca foi implementado.

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
- [ ] Limpar código morto (G9): `decisao` não usada, branch `verificarCriarResp`, prints de debug → um `Logger` simples ou `System.Logger` — *(parcial: `decisao` virou uso real na geração; prints e placeholder de humor ainda vivos)*
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
- [ ] Definir limiar de confiança (ex.: ≥ 0,5 → acionar intenção; entre 0,3 e 0,5 → responder com a melhor aposta + "Foi isso que você quis dizer?"; < 0,3 → fallback) — *(faixas entregues: ALTA ≥ 0,6 responde direto · MÉDIA 0,3–0,6 aposta + "Foi isso que você quis dizer?" · BAIXA < 0,3 fallback — issue #16, commit 08e1666)*
- [ ] Empate entre intenções → desempatar pelo **contexto** da Fase 2 (tópico atual) — *(pendente: exige Sessão, §6.1)*

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

- [ ] Detectar **nome**: regex simples sobre padrões `"meu nome é X"`, `"me chamo X"`, `"sou o X"`, `"pode me chamar de X"` — e perguntar "Como posso te chamar?" no primeiro uso
- [ ] Detectar **fatos sobre o usuário** com pares pergunta/resposta da conversa: usuário diz "eu gosto de minecraft" → `fatos.put("gosta de", "minecraft")`. Nas próximas conversas: "Você me disse que gosta de minecraft — ainda joga?"
- [ ] **Tópico atual**: guardado a cada intenção classificada; usado para desempate e para "sobre isso?"
- [os] Persistir sessão entre execuções (`sessao.json` na pasta de dados) — "Bem-vindo de volta, João!" ao reabrir

### 6.2 Histórico visível (corrige G5)

- [x] `textAreaLoli.append(...)` em vez de `setText` — o chat vira um log real — *(issue #1; teste ControleFaceTest)*
- [ ] Auto-scroll para o fim; separador visual entre turnos
- [ ] (Opcional) `conversas.log` com timestamp — dados para análise futura e para treinar/tunar

### 6.3 Slots nas respostas

- [ ] Suporte a placeholders nas respostas: `{nome}`, `{topico}`, `{hora_do_dia}` ("bom dia" só antes do meio-dia, senão "boa tarde")
- [ ] Exemplo: `"Bom te ver de novo, {nome}! Da última vez a gente tava falando sobre {topico}."`

### Critérios de aceite

- Fechar e reabrir → KAIRA cumprimenta pelo nome e retoma o tópico
- "você lembra o que eu falei que gosto?" → resposta correta baseada em `fatos`
- A UI mostra a conversa inteira, não só a última resposta

---

## 7. Fase 3 — Personalidade viva

**Objetivo:** transformar respostas mortas em interação. O código já tem o gancho: `comandosResposta()` anexa "nivel de humor em... de 10" para o assunto "status" — vamos implementar o humor de verdade.

### 7.1 Sistema de humor

- [ ] Campo `humor` (0–10, inicial 5) na sessão, persistido
- [ ] **Modificadores**: elogio ("você é legal", "gosto de você") → +1; grosseria ("você é chata", xingamento) → −1; despedida carinhosa → +0
- [ ] **Faixas de tom** — mesma intenção, resposta diferente por humor:
  - 0–3: seca/cética — `"tô de boa."`
  - 4–6: neutra — `"estou bem, obrigada por perguntar."`
  - 7–10: animada — `"estou ótima! seu papo melhora meu dia!"`
- [ ] Implementação: cada intenção pode ter respostas marcadas por faixa (ver [Apêndice A](#apêndice-a--esquema-json-proposto)), ou um pós-processador ajusta a mensagem
- [ ] "Como está seu humor?" → responde o valor real + frase da faixa (finalmente conserta o placeholder)
- [ ] (Opcional) Decaimento: humor volta 1 ponto por dia sem interação

### 7.2 Iniciativa própria

- [ ] A cada N turnos (ex.: 3) sem KAIRA ter feito pergunta → usar `followups` da intenção
- [ ] Perguntas de interesse genérico num pool próprio: "E você, o que andou fazendo hoje?", "Assiste algo bom ultimamente?"
- [ ] (Opcional) `javax.swing.Timer` → silêncio de 60s+ gera um comentário leve ("tá aí? posso esperar, sou paciente... sou feita de paciência, literalmente")

### 7.3 Fallbacks inteligentes

Substituir o "Não sei como responder a isso" seco:

- [ ] Classificou algo com confiança média → aposta + confirmação: *"Sobre {palavrasChave}, é isso? Ainda tô aprendendo — me responde essa: ..."* — *(degrau da faixa média pronto — issue #16; a frase personalizada com {palavrasChave} e o followup ficam pra §7.1 humor)*
- [x] Confiança baixa → honesto + curioso: *"Ainda não sei falar disso... me ensina? O que você quis dizer?"* → cai no modo ensino atual (que já existe e funciona!) — *(pool de 3 frases em `FRASES_FALLBACK` + anti-repetição via `ultimoFallback` — issue #17; o modo ensino segue armado no `enviarResposta`)*
- [ ] Com a pré-análise da [§5.7](#57-pré-análise-frame--slot-tarefa-8) (task #8): quando o **frame** foi reconhecido mas o **slot** é novo, responder "ainda não sei sobre {slot}" em vez do genérico
- [ ] **Modo ensino turbinado**: ao ensinar, perguntar em qual intenção encaixa (ou criar nova) — em vez de gravar frase solta com assunto "Auto-Aprendizado". O dado coletado passa a alimentar o classificador — *(parcial: o ensino já grava na intenção `auto_aprendizado` estruturada sem mutar o cache — commit b89557d; falta perguntar/encaixar intenção)*

### Critérios de aceite

- Elogiar 3× seguidas muda o tom das respostas de forma perceptível
- KAIRA faz pelo menos 1 pergunta espontânea a cada ~5 turnos
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
5. **`Sessao` com nome do usuário** — pergunta no primeiro uso, usa `{nome}` nas respostas — *(próximo da fila — Fase 2)*

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
