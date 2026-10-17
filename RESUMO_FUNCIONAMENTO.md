# RESUMO DE FUNCIONAMENTO — K.A.I.R.A.

> Consulta rápida pra não esquecer os mecanismos do projeto. Tudo abaixo foi
> conferido no código (não é de memória). Última revisão: out/2026, head
> `283fc17` — suíte 199 testes + avaliador 14/14.
>
> Pra detalhes de planejamento/decisões: `PLANO_DE_EVOLUCAO.md` e
> `BRIEFING_EXPANSAO_DIALOGO.md`. Pra rodar: `run_tests.ps1` (fim do arquivo).

---

## 1. Mapa dos arquivos

| Arquivo | Papel |
|---|---|
| `view/Face.java` | Janela (Swing): botões, campo, avatar, tema. Não tem lógica de bot. |
| `view/TelaLog.java` | Tela de log ao vivo (#29): fila UTF-8, tailed a cada 1s, últimas 2000 linhas. |
| `controller/ControleFace.java` | Ponte da UI: HTML do chat (balões), estados do `!`, manda pro `decifrar`. |
| `controller/FormulaResposta.java` | **Orquestra o turno**: `decifrar` (cascata de degraus) → `enviarResposta` (monta a fala final). |
| `controller/ControleResposta.java` | **O cérebro**: classificador (IDF/stem/stopwords), pools de resposta, slots, fallback, `ensinaEm`. |
| `controller/ControleSessao.java` | **Memória do turno**: nome, fatos, humor, tópico, contadores, followups, padrões regex. |
| `controller/ControleCorrecao.java` | Degrau de correção (palavra errada / frase conhecida com erro). |
| `controller/ControlePreAnalise.java` | Pré-análise: frame + slot da mensagem (alimenta fallback com `{slot}`). |
| `controller/ControleSinonimo.java` | Grupos de sinônimos (desempate do matcher + `CriarResposta`). |
| `domain/` | `Intencoes`, `Intencao`, `Exemplo`, `RespostaIntencao`, `Sessao`, `MatchIntencao`… |
| `util/Tema.java` | Paleta única preto/verde (#31) — única fonte de cor da UI. |
| `util/Caminhos.java` | `recurso(nome)` → `KAIRA/resources/`. **Nunca escreva o caminho literal** (teste `SemCaminhoHardcodedTest` flagra, inclusive em comentário). |
| `util/Registro.java` | `linha(evento)` → `Registro.arquivo()` (gitignored). **#36 (higiene):** `-Dkaira.log.destino` redireciona — a suíte grava em arquivo de teste e o `conversas.log` fica **só com uso real** (insumo de curadoria: fallback, aposta, temas repetidos). À prova de falha: log nunca derruba o fluxo. |
| `KAIRA/resources/intencoes.json` | **O banco** (UTF-8 sem BOM): 98 intents com exemplos/respostas/followups/acoes. |
| `KAIRA/resources/conhecimentos.json` | **#37 (C3 piloto):** dicionário — termo (= nome de intent) → `resumo` (falado em "conta mais") / `texto` (reserva "explica melhor"), `origem` com fonte+licença por item (Wikipédia PT CC BY-SA, adaptado). Lookup `ControleConhecimento.resumo()` — null-seguro. |
| `KAIRA/resources/avatar/` | `kaira_conversa`, `kaira_idle`, `kaira_baixa`, `kaira_alta` (+ `avatar_01..09` arquivadas). |
| `sessao.json` | Persistência da sessão (nome, humor, tópico, turnos). |

---

## 2. O turno, passo a passo (`FormulaResposta.decifrar`)

1. Log `ENTRADA   :` → `ControleSessao.processa(fala)` que **a cada mensagem**:
   incrementa `turnosDesdePergunta`, roda `detectaNome` + `detectaFato` + `detectaHumor`, anota o turno.
2. **Resposta ao nome** (`respostaDeNome`): se a mensagem só capturou o nome
   ("meu nome é X") → responde "Prazer em conhecer…" e **acaba o turno**
   (DEGRAU `resposta-nome`), sem classificador.
3. `tiraNome` (remove o trecho do nome da fala) → `Normalizador.texto`.
4. **Comando** (`ControleComandos`): "Executar <programa>" roda direto e pula
   tudo (DEGRAU `comando`). Legado — o `if (false)` do TODO está ali.
5. **Cascata de degraus** (o primeiro que achar resposta vence):

   | DEGRAU | O que roda | Limiar |
   |---|---|---|
   | `direto` | `lerResposta` — classificador exato/fuzzy | score **≥ 0.6** (`LIMIAR`) |
   | `corrigida-palavra` | `ControleCorrecao.corrigirPalavras` (digitação errada) | — |
   | `corrigida-frase` | `ControleCorrecao.corrigirFrases` (frase conhecida com erro) | — |
   | `aposta` | `respostaAposta` — responde e pergunta "Foi isso que você quis dizer?" | **0.3 ≤ score < 0.6** |
   | `aprender` | resposta = null → fallback + **arma o modo ensino** | abaixo de 0.3 |

6. Logs `DEGRAU    :` e `MATCH     :` (intento, score, frase-exemplo, tokens — #29).
7. `criacaoLeituraRespostas`: sorteia 1–4 e em ≥2 (75%) chama
   `CriarResposta` (troca palavras por sinônimos) — **mas hoje o retorno é
   descartado** (não atribui em nada): é legado, efeito visível só no
   `System.out`. Depois manda pra `enviarResposta`.
8. **`enviarResposta`** monta a fala final:
   - resposta = null → `ativarAprendizado()` (acende o `!`), guarda a frase em
     `bufferFrases`, responde fallback com `{slot}` da pré-análise
     (log `APRENDENDO:`);
   - resposta OK → **anexa a pergunta de interesse** se couber (ver §5);
   - `preencheSlots` (`{nome}` `{topico}` `{hora_do_dia}` `{fato:CHAVE}`);
   - anexa "Como posso te chamar?" se ainda não sabe o nome;
   - log `KAIRA     :` e entrega pro chat.

---

## 3. Classificador (como ele pontua)

- `tokensDe`: `Normalizador.texto` (minúsculas/acentos) → stem → remove
  stopwords. **Entrada crua é segura** (a normalização é interna).
- IDF: peso `log(N/df)` por token; **cache por identidade da base** (#26) —
  mesma `Intencoes` devolve o mesmo mapa; `ensinaEm` muta →
  `invalidaCacheIdf()` zera tudo e a próxima chamada recalcula pesos **e**
  tokens.
- Score = intersecao / soma dos pesos da entrada. Empate: vence quem tem
  menos tokens "sobrando", e aí o frame da pré-análise.
- `melhorIntencaoDetalhado` **também** seta `topicoAtual` (menos pro intento
  `continuacao_topico`) e escreve o `ultimoMatchLog`.

---

## 4. ⭐ Tabela de gatilhos — "o que dispara o quê"

| Efeito | Gatilho exato | Onde |
|---|---|---|
| **Pergunta de interesse (followup)** | ≥ **3 turnos** do usuário sem ela perguntar **E** ela acertou uma resposta (turno de fallback não conta) | `FormulaResposta.enviarResposta` → `ControleSessao.perguntaInteresse()` |
| **De onde sai a pergunta** | 1º: `followups` do **tópico atual** no banco; 2º: pool `PERGUNTAS_INTERESSE` (6 frases) | `perguntaInteresse()` |
| **Contador zera** | quando a pergunta dispara, quando ela pergunta o nome, e no `reset()` | `ControleSessao` |
| **Humor +1** | 1ª frase que casar: "você é legal/show/incrível/ótimx/linda/maravilhosa" ou "te amo / amo você / adorei" | `PADROES_HUMOR` → `detectaHumor` |
| **Humor −1** | "você é chata/burra/idiota/lixo/insuportável/irritante" ou "odeio você / seu lixo / me arrependo de te conhecer" | idem (1 regra por turno) |
| **Tom muda de verdade** | humor cruza faixa: 0–3 / 4–6 / 7–10 — a resposta só entra no pool se `humorMin ≤ h ≤ humorMax`; **nenhuma casa → pool inteiro** (nunca fica sem resposta) | `porFaixaDeHumor` |
| **3 elogios = cara alta** | cada elogio +1: 5 → 8 cruza pra ≥7 (o teste do #23 é exatamente isso) | + `avatar kaira_alta` na próxima tecla |
| **Avatar troca** | interação (tecla/Enviar) → banda 0–3 `kaira_baixa` / 4–6 `kaira_conversa` / 7–10 `kaira_alta`; **30s ocioso → `kaira_idle` (manda em cima de tudo)** | `Face.reativaConversa` |
| **Modo ensino arma** | qualquer turno de fallback (score < 0.3) → `!` pro estado "manter" (NEON) | `ativarAprendizado()` |
| **Ensinamento** | com `!` armado, o que você digita vira resposta **e** é gravado: `manterResposta` → `chamadaFrase` → `ensinaEm` | ver §8 |
| **Pergunta de nome** | 1ª resposta com nome ainda desconhecido → anexa "Como posso te chamar?" **e zera o contador de followups** | `perguntaDeNome()` |
| **Fato anotado** | mensagem casa com regex de `PADROES_NOME`/`PADROES_FATO` (roda em todo turno, antes do classificador) | `ControleSessao.processa` |
| **Resposta com fato pendente descarta** | template com `{fato:X}` e fato incompleto → fora do pool → fallback | `comandosResposta` |
| **Ações do JSON** | `acoes` na resposta: `MOSTRAR_HUMOR` (anexa "nivel de humor em X de 10"), `MOSTRAR_VERSAO`, `DESLIGAR` (fecha o app!) | `comandosResposta` |
| **"Conta mais / e depois?"** | literais → intento `continuacao_topico`; o hook **não** troca o `topicoAtual` por ele; no turno dela o pool de interesse fica de fora | `melhorIntencaoDetalhado` + `pedeContinuacao` |
| **Comando de SO** | mensagem começa com "Executar" → roda o programa, pula o classificador | `ControleComandos` |

---

## 5. Followups / iniciativa própria (#25, §7.2) — em detalhe

O objetivo: ela pergunta alguma coisa de vez em quando, **sem parecer um
robô com timer**. Mecanismo real:

- Contador `turnosDesdePergunta` (`ControleSessao`), **limite `TURNOS_SEM_PERGUNTA = 3`**.
- Sobe +1 em **cada mensagem do usuário** (`processa`).
- A verificação acontece **só quando ela vai falar uma resposta certa**
  (`FormulaResposta`): se o contador ≥ 3, chama `perguntaInteresse()` e
  **anexa a pergunta no fim da resposta dela**.
  - Turno de fallback (ela não entendeu) **não** anexa — senão ela perguntaria
    enquanto ensina.
- `perguntaInteresse()`:
  1. contador < 3 → devolve null (não é hora);
  2. zera o contador;
  3. **fonte 1**: se o `topicoAtual` tem `followups` no banco → sorteia um deles
     (os dados: ~195 followups em ~74 intents, escritos pela curadoria);
  4. se o turno é de **continuação** ("conta mais") → a fonte 2 é segurada
     (#28: o turno é dela, a continuação já é engajamento);
  5. **fonte 2**: sorteia do pool genérico `PERGUNTAS_INTERESSE`.
- Anti-repetição: não repete a última pergunta (`ultimoInteresse`, estilo do
  `ultimoFallback`).
- O contador **zera** em 3 situações: quando a pergunta dispara, quando ela
  pergunta seu nome, e no `reset()`.

> Resumo em uma frase: *"3 turnos seus sem ela perguntar → ela pergunta; a
> pergunta sai dos followups do assunto que vocês estão falando, ou de um
> pool genérico; se você acabou de pedir 'conta mais', o pool fica de fora."*

---

## 6. Humor (#23, §7.1)

- `Sessao.humor`, **0–10, default 5** (clamp no setter — não estoura).
- Modificadores em `PADROES_HUMOR` (regex na fala crua, com ou sem acento):
  +1 elogio, +1 declaração, −1 ofensa, −1 odeio. **1 regra por turno** (a
  primeira que casa já `return`).
- Efeito real em 3 lugares: **pool de respostas** (`humorMin`/`humorMax`),
  **texto** (`MOSTRAR_HUMOR` anexa o nível) e **avatar** (bandas da §4).
- Log: `SESSAO    : humor 5->6 (+1)`.

---

## 7. Nome e fatos (§6.1, #21, #32)

- **Nome**: 4 padrões (`meu nome é`, `me chamo`, `pode me chamar de`,
  `sou o/a`), até 3 palavras com lookahead (para em "e/que/com/pra/vou/quero").
  Captura → `tiraNome` remove da fala → turno vira só o agradecimento.
- **Fatos**: `PADROES_FATO` — `gosta de`, `mora em`, `trabalha com`,
  `nasceu em`, `meu X favorito é Y` (genérico: grava `"X favorito"`),
  e os fixos do #32: **`time`** (2 variantes), **`musica favorita`**,
  **`pet`**, **`sonho`** — sempre com **artigo cortado** ("o São Paulo" →
  "São Paulo") e chave ASCII.
- Uso nas respostas: slot **`{fato:CHAVE}`**; resposta cujo fato está
  pendente sai do pool (#21); perguntar de volta é a intenção `lembra_*`
  (ex.: `lembra_time` — 6 exemplos, 4 respostas, todas ancoradas no slot).

---

## 8. Aprendizado (`!`) — #8 e #33

1. Ela não achou resposta → fallback **arma** o `!` (estado "manter", NEON;
   desarmado é `NEON_APAGADO`; o estado vermelho "criar" existe na UI mas o
   caminho `manterCriacao` está comentado no `ControleFace` — legado).
2. Você digita a resposta certa → `manterResposta(resposta)`:
   - `chamadaFrase(bufferFrases, resposta)` → **`ensinaEm` (#33: antes de
     gravar, classifica a frase ensinada; score ≥ 0.6 → o par cai DENTRO do
     intento vencedor; dúvida/nulo → `auto_aprendizado`)**;
   - grava `intencoes.json`, `ControlePalavra.gravaPalavra` e
     `invalidaCacheIdf()` (próxima classificação recalcula);
   - devolve a resposta na tela.
3. Linha no log: `APRENDE   : intento=esporte score=0,91` ou
   `APRENDE   : auto_aprendizado score=0,24`.
- A ordem importa: classificar **antes** do `invalidaCacheIdf` (senão NPE de
  token no ensino em cadeia).

---

## 9. Tópico e continuação (#19, #27, #28)

- `topicoAtual` = intento do último match com score ≥ limiar (exceto
  `continuacao_topico`, que preserva o anterior) — usado no slot `{topico}`
  e como **fonte 1 dos followups**.
- "fala mais disso / conta mais / e depois?" → intento `continuacao_topico`
  (os literais estão no banco); nesse turno `pedeContinuacao()` segura o pool
  genérico de perguntas.

---

## 10. Fallback e anti-repetição (#16, #17)

- `FRASES_FALLBACK` (sem slot) e `FRASES_FALLBACK_SLOT` (com `{slot}` da
  pré-análise) — sorteio **nunca repete a última** (`ultimoFallback`).
- Degrau médio (0.3–0.6) não é fallback: responde com aposta +
  *"Foi isso que você quis dizer?"*.
- Idem `ultimoInteresse` pros followups.

---

## 11. Logs (prefixo = 10 colunas até os `:`)

`ENTRADA` · `DEGRAU` · `MATCH` · `RESPOSTA` (template antes dos slots) ·
`KAIRA` (fala final) · `SESSAO` (tópico/nome/humor/followup) · `APRENDE`
(#33) · `APRENDENDO` (fallback armando o ensino) · `AVATAR` (#30/#34).
Tudo em `conversas.log` (gitignored) + tela ao vivo no botão **Log** (#29).

---

## 12. Como testar

- `powershell -NoProfile -ExecutionPolicy Bypass -File run_tests.ps1`
  → compila `src` + `test`, roda a suíte headless (JUnitCore, auto-descoberta das
  classes `*Test`) e o **avaliador 14/14** (conjunto congelado de 14 frases —
  regressão nunca pode cair). Flag `-SkipBuild` reusa o build anterior. O log
  da suíte vai pra `%TEMP%\kaira_suites_conversas.log` (#36) — o `conversas.log`
  nunca é tocado por teste.
- Specs por issue vivem em `test/com/JCODE/...` (`FatosNovosTest`,
  `EnsinoTurbinadoTest`, `CuradoriaTemasTest`, `IniciativaPropriaTest`, …).
- Fluxo da casa: issue → spec vermelho → receita → verde → `Fixes #N` → docs.

---

## 13. Receitas — "quero mudar X, mexo em Y"

| Quero | Onde |
|---|---|
| Frase/resposta nova num tema | `KAIRA/resources/intencoes.json` (contrato do `CuradoriaTemasTest`: ≥6 exemplos, ≥4 respostas, humor 0–10, `acoes` não-nula, auto-classificação ≥0.99) |
| Followup novo de um tema | campo `followups` do intento no JSON |
| Fato novo tipo "X é Y" | regex em `PADROES_FATO` (chave fixa ASCII + artigo cortado) + intento `lembra_<chave>` ancorado em `{fato:chave}` |
| Regra de humor | `PADROES_HUMOR` (formato `{"+1", regex}`) |
| Frase de fallback/pool | `FRASES_FALLBACK` / `PERGUNTAS_INTERESSE` em `ControleResposta`/`ControleSessao` |
| Faixas do avatar | `Face.avatarPorHumor` (0–3 / 4–6 / 7–10) + PNGs em `KAIRA/resources/avatar/` |
| Cor da UI | **só** `util/Tema.java` |
| Novo tipo de score/limiar | constantes `LIMIAR` (0.6) e `LIMIAR_MEDIA` (0.3) em `ControleResposta` |

---

## 14. Pendências conhecidas (para retomar)

- **Leva de dados do briefing — EXECUTADA** (DIALOGO out/2026): P1 ✅ (pergunta_kaaira),
  P3 ✅ (reacao_curta), P4 ✅ (trabalho social/marcos), P5 ✅ (casa_usuario), P6 ✅ (hobbies),
  pools de resposta por faixa ✅ (+156 respostas; faixa seca das intenções diárias de 1 → ≥3),
  P7.5 parcial ✅ (`{nome}` em pools), P7.3 parcial (3 empates dissolvidos; 5 aceitos/documentados).
  **Pendente: P7.1 (podar `agradecimento` de 619 → ~250).**
- **#35 (anti-repetição de resposta, C1 do briefing §6): CONCLUÍDA** — fila por
  intento (`ultimasRespostas`, últimas 2 frases fora do sorteio) em
  `ControleResposta.comandosResposta`; spec `AntiRepeticaoTurnoTest` verde
  (sorteio determinístico via o gancho `semeador`).
- **C2 (higiene do log, briefing §6): CONCLUÍDA — issue #36** — suíte roda com
  `-Dkaira.log.destino` (arquivo em `%TEMP%`) e o `conversas.log` fica só com
  uso real. **Legado: o log atual (6,3 MB) é mistura teste+real — minerar só o
  segmento novo (pós-#36) ou filtrar os ~31 reais.**
- **C3 (dicionário de conhecimentos, §12.1): PILOTO ENTREGUE — issue #37** —
  15 entradas em `conhecimentos.json` (Wikipédia PT CC BY-SA, texto adaptado,
  origem por item), `ControleConhecimento` (null-seguro) e encaixe no
  `continuacao_topico`: pedir "conta mais" sobre trabalho/esporte/música...
  agora fala a substância do tópico. **Próximas fatias:** "explica melhor"
  (campo `texto` já existe), extrator automático de resumo (stopwords/IDF),
  escala da base + OpenWordnet-PT (CC BY 4.0).
- §7.3 restante: degrau médio com `{palavrasChave}` + followup (issue #16 parcial);
  frame reconhecido/slot novo (§5.7).
- Classificador: **negação é cega** ("não" é stopword) — causa raiz dos empates `{gosto}/{entende}`;
  fix de código, não de dados.
- Opcional: theming dos `JOptionPane` (saem no estilo do sistema);
  decaimento do humor por dia sem interação (§7.1 opcional);
  `CriarResposta` — retorno descartado (decidir: usar ou remover).
