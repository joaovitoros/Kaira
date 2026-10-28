# RESUMO DE FUNCIONAMENTO — K.A.I.R.A.

> Consulta rápida pra não esquecer os mecanismos do projeto. Tudo abaixo foi
> conferido no código (não é de memória). Última revisão: out/2026, head
> `e4b199e` — suíte **67 classes verde**, avaliador **44/44**, replay do corpus
> **5,4 / GRAVE 0**. As inteligências da leva #50–#63 e da #41 reaberta estão
> aqui (as seções marcadas com ⭐ são as desta leva).
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
| `controller/ControleSessao.java` | **Memória do turno**: nome, fatos do usuário, humor, tópico, contadores, followups, **a pergunta pendente dela** (#41: `familiaPergunta`/`perguntaPendente`) e padrões regex. |
| `controller/ControleKaira.java` | **⭐ A memória dela (#50/#56)**: lê/salva o `kaira.json` (relações `gosta`/`nao_gosta`), migra o formato plano no load e guarda segredo (#53) e não-contradição (#56). |
| `controller/ControleCorrecao.java` | Degrau de correção (palavra errada / frase conhecida com erro). |
| `controller/ControlePreAnalise.java` | Pré-análise: frame + slot da mensagem (alimenta fallback com `{slot}`). |
| `controller/ControleSinonimo.java` | Grupos de sinônimos (desempate do matcher + `CriarResposta`). |
| `domain/` | `Intencoes`, `Intencao`, `Exemplo`, `RespostaIntencao`, `Sessao`, `MatchIntencao`, **`FatoKaira`/`MolduraKaira`** (#56), `Conhecimento(s)` (#37)… |
| `util/Tema.java` | Paleta única preto/verde (#31) — única fonte de cor da UI. |
| `util/Caminhos.java` | `recurso(nome)` → `KAIRA/resources/`. **Nunca escreva o caminho literal** (teste `SemCaminhoHardcodedTest` flagra, inclusive em comentário). |
| `util/Registro.java` | `linha(evento)` → `Registro.arquivo()` (gitignored). **#36 (higiene):** `-Dkaira.log.destino` redireciona — a suíte grava em arquivo de teste e o `conversas.log` fica **só com uso real** (insumo de curadoria: fallback, aposta, temas repetidos). À prova de falha: log nunca derruba o fluxo. |
| `KAIRA/resources/intencoes.json` | **O banco** (UTF-8 sem BOM): 98 intents com exemplos/respostas/followups/acoes. |
| `KAIRA/resources/conhecimentos.json` | **#37 (C3 piloto):** dicionário — termo (= nome de intent) → `resumo` (falado em "conta mais") / `texto` (reserva "explica melhor"), `origem` com fonte+licença por item (Wikipédia PT CC BY-SA, adaptado). Lookup `ControleConhecimento.resumo()` — null-seguro. |
| `KAIRA/resources/kaira.json` | **⭐ #56 (modelo v2):** os fatos da persona com **relações** — `"cor": {"gosta": ["verde"], "nao_gosta": ["cinza"]}` — e moldura por relação (`{v}`). Arquivo plano antigo (v0.0.1) **migra no load**; o primeiro `gravaFato` salva em v2 (0.0.2). |
| `KAIRA/resources/avatar/` | `kaira_conversa`, `kaira_idle`, `kaira_baixa`, `kaira_alta` (+ `avatar_01..09` arquivadas). |
| `sessao.json` | Persistência da sessão (nome, humor, tópico, turnos, fatos do usuário — ainda no formato plano; o **v2 da sessão** é fila). |

---

## 2. O turno, passo a passo (`FormulaResposta.decifrar`)

1. Log `ENTRADA   :` → `ControleSessao.processa(fala)` que **a cada mensagem**:
   incrementa `turnosDesdePergunta`, zera a flag de explicacao (#54), roda
   `detectaNome` + `detectaFato` (**#51: fala-pergunta não grava fato**) +
   `detectaHumor` (#51: valor-pergunta também não grava) e anota o turno.
2. **Resposta ao nome** (`respostaDeNome`): se a mensagem só capturou o nome
   → responde "Prazer em conhecer…" e acaba o turno (DEGRAU `resposta-nome`).
3. `tiraNome` → `Normalizador.texto` (a fala vira minúscula/sem pontuação).
4. **⭐ Ensino dela (#50)** — com o P1 armado: a forma `(sua|seu) CHAVE … e
   VALOR` grava na memória dela (o score da frase **não** decide). Recusas com
   ack próprio: **segredo** (#53: senha/cartão/cpf/chave pix) e **contradição**
   (#56: valor já está na relação oposta). Sem P1 armado, nada grava.
5. **P2 do ensino (#49)** — resposta retida pelo porteiro: só um **SIM**
   explícito absorve; um NÃO explícito descarta com ack.
6. **Aposta pendente (#39)** — "isso" entrega o candidato proposto na hora.
7. **⭐ Máquina da pergunta pendente (#41)** — se ela perguntou um followup no
   turno anterior: fala **entendida** (pergunta de outro assunto ou score
   ≥ 0,6) cancela com ack transparente; **resposta vaga** (0,10–0,60, a banda
   da aposta) **absorve na família que perguntou**; **lixo** (score < 0,10) vai
   pro P2 do #48. Comando também cancela (o pipeline responde).
8. **Comando** (`ControleComandos`): **#62 (G6) — neutralizado**: a frase que
   começa com "executar" recebe a recusa curada ("Essa eu nao faco…") e o turno
   é consumido (DEGRAU `comando`) — **nada roda no PC**, sem popup; "executar"
   no meio da frase segue o pipeline normal.
9. **Cascata de degraus** (o primeiro que achar resposta vence):

   | DEGRAU | O que roda | Regra |
   |---|---|---|
   | `triagem` | **⭐ `respostaPorTriagem`** — porta DELA (memória `kaira.json`) + porta USUÁRIO (fatos) + negativa (#56) | possessivo/relação, antes do classificador |
   | `direto` | `lerResposta` — classificador exato/fuzzy | score **≥ 0.6** (`LIMIAR`) |
   | `corrigida-palavra` | `ControleCorrecao.corrigirPalavras` (digitação errada) | — |
   | `corrigida-frase` | `ControleCorrecao.corrigirFrases` (frase conhecida com erro) | — |
   | `aposta` | `respostaAposta` — responde e pergunta "Foi isso que você quis dizer?" | **0.3 ≤ score < 0.6** |
   | `aprender` | resposta = null → fallback; **⭐ quem julga é o porteiro (#49)**: score do buffer ≥ piso 0,10 → absorve; senão P2 (retém + confirma) | abaixo de 0.3 |

10. Logs `DEGRAU    :`/`MATCH     :` (#29) e o `criacaoLeituraRespostas` (legado
    do `CriarResposta`, retorno descartado) → `enviarResposta`.
11. **`enviarResposta`** monta a fala final:
    - resposta = null → **P1** (#48): `bufferFrases` = a frase, acende o `!`,
      responde o fallback com `{slot}` (log `APRENDENDO:`);
    - resposta OK → **cancela o ensino pendente** com ack (#48) e **cancela a
      pergunta pendente** com ack (#41), se houver;
    - **⭐ anexa a pergunta de interesse** se couber (§5) — e **arma a nova
      pergunta pendente** (#41);
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
- **⭐ #49 (gate de resposta, não de medição):** quem responde não pode
  entregar match de 1 token; o porteiro do ensino (`scoreDo`) e o `ensinaEm`
  veem o match fraco honesto.
- **⭐ #41 (frase genérica):** frase só de token genérico ("e qual seu
  preferido?") segue o `topicoAtual` (`melhorDoTopico`) em vez de decidir por
  token solto.
- **⭐ A triagem roda ANTES do classificador** (§7.5): as portas dela/do usuário
  interceptam quando a memória tem a chave; o classificador fica para o geral.
- **#42/#43 (desempate):** empate de score resolvido por **grupo de sinônimo e
  flexão de gênero** (favorito/favorita, preferido/preferida), e o log `MATCH`
  **diz o caminho** do desempate.
- **#44:** frase genérica **sem "favorito"** no tópico não apaga mais o tópico
  (a borda do `melhorDoTopico`).
- **#46 (correção):** o desempate da correção **não segue mais a ordem do
  `palavras.json`** — o score decide.
- **#47:** o `palavras.json` **regenera sozinho** quando o arquivo falta.
- **#49 (gate de tokens de peso):** o gate da resposta aplica três réguas —
  tokens **comuns na entrada**, **grupo parcial** de sinônimo e **flexão
  cheia** — quem responde não entrega match de um token só.

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
| **Comando de SO (neutralizado, #62)** | mensagem que **começa** com "executar" → **recusa curada** e turno consumido (**nada executa**, sem popup); "executar" no meio segue o pipeline | `ControleComandos` |
| **⭐ Pergunta sobre ela** | fala-pergunta + possessivo dela ("qual sua cor favorita?") → responde da **memória dela** (`kaira.json`, relações `gosta`/`nao_gosta`) com a moldura | `respostaPorTriagem` (porta DELA) |
| **⭐ Pergunta sobre o usuário** | pergunta + `meu/minha` + o fato existe na sessão → "pelo que você me contou, o/a seu/sua CHAVE é VALOR!" (artigo pelo gênero da chave, #55) | porta USUÁRIO |
| **⭐ "O que ela NÃO gosta?"** | "qual cor você não gosta?" → lê `nao_gosta` (#56); lista vazia → "Ainda não tenho nenhuma nessa lista!" | porta DELA negativa |
| **⭐ Miss honesto / espelho** | pergunta dela sem memória → honesto ("me ensina?"); se o **usuário** tem o mesmo fato → espelho ("mas o seu time eu guardei: Gremio!") | #52 |
| **⭐ Ensino dela** | P1 armado + "sua CHAVE … e VALOR" → grava na memória dela; **segredo** (#53) e **contradição** (#56) recusam com ack próprio | #50/#53/#56 |
| **⭐ Followup pendente** | ela perguntou um followup no turno anterior → a próxima fala é a resposta (cancela/absorve/P2) | #41 |
| **⭐ Explica melhor** | fala com `explica/detalh/aprofund` e o tópico tem `texto` no dicionário → fala o TEXTO (o "conta mais" segue no resumo) | #54 |
| **⭐ "Qual meu nome?"** | nome conhecido → "Seu nome é X!" (nunca o honesto falso) | #55 |
| **⭐ Gênero da moldura** | chave com sufixo `favorita`/`preferida` → "a sua"; demais → "o seu" | #55 |
| **⭐ Fluxo de opções** | pergunta dela sem memória → convite + estado `chaveOpcoes`; a lista seguinte (2+ itens) sorteia e grava em `gosta` | #57 |

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

### ⭐ O followup agora LEMBRA — a pergunta pendente (#41)

- Quando a resposta sai **com followup**, o estado arma `familiaPergunta`
  (quem perguntou; `null` = pool genérico) + `perguntaPendente` (o texto) e
  loga `PENDENTE : familia=…`.
- Na fala seguinte:
  - **entendida** (pergunta de outro assunto ou score ≥ 0,6) → o pipeline
    responde e a pendente **cancela com ack transparente** ("deixa pra lá o
    que te perguntei antes!");
  - **vaga** (0,10–0,60 — a banda onde a aposta adivinhava) → **absorve na
    família** (exemplo = a resposta do usuário, resposta = o followup; o gate
    do #33: ≥0,6 no intento do match, senão a família/`auto_aprendizado`) +
    ack "Anotado…";
  - **lixo** (< 0,10, ex. "kkk") → **P2** (retém o par, pede confirmação;
    nada grava sem "sim") — e o ensino **não** é armado (o sequestro morreu).
- Comando/aposta confirmada respondem normal e cancelam a pendente.
- Spec `MaquinaContextoTest`. Lição medida: o 1º desenho absorvia também a
  faixa confiante e o replay acusou (GERAL 4,8, conversa engolida); a regra
  "entendida cancela" devolveu o placar (5,3).

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
  **#63 (higiene das baterias):** o nome **pelado** (janela da pergunta) tem
  blocklist (`tudo`, `esquece`, saudações, risos, `show/legal/beleza`…) e os
  padrões rejeitam **profissões** ("sou enfermeira" não vira *"Prazer,
  enfermeira!"*). Captura → `tiraNome` remove da fala → turno vira só o
  agradecimento.
- **Fatos**: `PADROES_FATO` — `gosta de`, `mora em`, `trabalha com`,
  `nasceu em`, `meu X favorito é Y` (genérico: grava `"X favorito"`),
  os fixos do #32: **`time`** (2 variantes), **`musica favorita`**,
  **`pet`**, **`sonho`** — e o **feminino do #52** (`"minha X favorita e Y"` →
  chave com o sufixo do gênero) — sempre com **artigo cortado** ("o São Paulo"
  → "São Paulo") e chave ASCII.
- **Guardas do #51**: fala-pergunta **não grava** (interrogativos na fala ou no
  valor), fato existente **não é sobrescrito** por valor diferente (log
  `fato X preservado`) e mesmo valor com outra caixa preserva o canônico.
- **⭐ Modelo v2 da sessão (#58):** os fatos do usuário ganharam as relações
  `gosta`/`nao_gosta` (o mesmo schema dela), **captura negativa** de 1ª pessoa
  ("eu não gosto de X", "não curto X", "odeio X") e de dimensão ("minha cor
  favorita não é azul"), a **porta negativa** do usuário ("qual cor eu não
  gosto?") e a **migração do `sessao.json`** no load. A conjugação decide o
  dono da pergunta: *gosto/curto* = usuário, *gosta/curte* = ela.
  `getFatos()` segue como **visão plana** (compat: get = `gosta[0]`) e
  `getFatosV2()` expõe as relações. Spec `FatosSessaoV2Test` (10).
- Uso nas respostas: slot **`{fato:CHAVE}`**; resposta cujo fato está
  pendente sai do pool (#21); perguntar de volta é a intenção `lembra_*`
  (ex.: `lembra_time` — 6 exemplos, 4 respostas, todas ancoradas no slot).

---

## 7.5 ⭐ A memória dela (`kaira.json`) — a triagem (#50–#56)

O que ela sabe **sobre si** — relações com listas, não valores soltos:

- **Schema v2 (#56):** `"cor": {"gosta": ["verde"], "nao_gosta": ["cinza"]}`
  + **moldura por relação** com `{v}`. Arquivo plano antigo (v0.0.1) **migra
  no load**; o primeiro `gravaFato` salva v0.0.2.
- **Porta DELA (#50/#52):** pergunta + possessivo dela + chave na memória →
  responde o valor com a moldura. Muito tolerante: chave em qualquer posição,
  plural ("jogos"→"jogo"), fillers ("de verdade", "hein", "mesmo") e
  interrogativo não-inicial ("me conta qual…"). **MISS honesto**: sem memória
  → "me ensina?"; se o **usuário** tem o mesmo fato → **espelho** ("mas o seu
  time eu guardei: Gremio!").
- **Porta DELA negativa (#56):** "qual cor você **não** gosta?" (sem possessivo)
  → lê `nao_gosta` com a moldura da relação; lista vazia → "Ainda não tenho
  nenhuma nessa lista!".
- **Porta USUÁRIO (#50/#52/#55):** pergunta + `meu/minha` + fato da sessão →
  "pelo que você me contou, **a sua/o seu** CHAVE é VALOR!" (artigo pelo gênero
  da chave). **Miss honesto:** "Você ainda não me contou sua cor favorita!";
  "qual meu nome?" com nome conhecido → **"Seu nome é X!"**.
- **Ensino dela (#50):** com o P1 armado, "sua CHAVE … e VALOR" grava em
  `gosta` com o ack "Anotado…" — o score da frase **não** decide.
- **Não-contradição (#56):** um valor nunca está nos dois lados — escrita
  contrária é recusada com ack próprio ("não vou me contradizer"), e dado sujo
  no load é deduplicado (positivo vence); comparação normalizada.
- **Segredo (#53):** senha/cartão/cpf/chave pix **não entram** (nem no
  `kaira.json` nem no `intencoes.json`) — recusa curada ("prefiro não anotar").
- **⭐ Fluxo de opções (#57):** pergunta dela sem memória → **convite** ("Ainda
  não tenho essa definida! Me diz quais são as opções?") + estado
  `chaveOpcoes`; a fala seguinte é a **LISTA** (2+ itens curtos com
  vírgula/`e`/`ou`, lida na fala **crua** — as vírgulas importam) → sorteia um
  (item da relação oposta sai do pool, #56), grava em `gosta` e confirma
  ("Então a minha é X! Anotei."); não-lista rejeita e mantém; 2ª falha cancela;
  fala entendida fora da forma de lista cancela e o pipeline responde;
  palavra genérica ("preferida") **não** convida (é do tópico).
- **⭐ Captura negativa dela (#59):** "sua cor **não** é roxo" grava em
  `nao_gosta` — a extração detecta a negação **antes** do padrão positivo (que
  engolia o "não"); a escrita é o `gravaFato(chave, valor, relação)` unificado
  (delegados de 2 args para compat), a não-contradição é **simétrica** (dizer
  que não gosta de um valor que está em `gosta` é recusado com o ack próprio)
  e o segredo é checado **antes** da escrita.
- **⭐ Moldura vira POOL (#60, Ideia 2 v1):** cada relação tem **2-3 molduras
  curadas** com `{v}` (kaira.json v0.0.3), sorteadas com **anti-repetição** por
  chave+relação (padrão #35); a moldura do usuário varia o **prefixo** ("pelo
  que voce me contou" / "como voce me disse" / "voce me contou que") na
  positiva **e** na negativa, com o artigo do #55 intocado. O motor é
  **moldura curada + slot** — não o `CriarResposta` (a roleta antiga).
- **⭐ Composição (#61, Ideia 2 v2):** as respostas de memória (as quatro
  portas) podem vir com uma **abertura curada** ("Ah, essa eu sei;" / "Essa e
  facil;" / "Deixa comigo;" / "Boa pergunta;") sorteada (~metade) com
  anti-repetição — a emenda usa o `;` pra não mexer na caixa do fato; no
  máximo 1 `?` por resposta (o followup do #25); misses/honestos não compõem.
- Specs: `TriagemPerguntaTest` (7), `IntegridadeFatoTest` (9),
  `TriagemVariacoesTest` (15), `EnsinoSegredoTest` (5),
  `PortaUsuarioPolimentoTest` (6), `FatosModeloV2Test` (8),
  `OpcoesMemoriaTest` (8), `EnsinoNegativoTest` (6),
  `MoldurasSorteadasTest` (7), `ComposicaoRespostaTest` (7).

---

## 8. Aprendizado — o ensino com porteiro (#33, #45, #48, #49) e a memória dela (#50)

1. Ela não achou resposta → o fallback **arma o P1** (`bufferFrases` = a frase;
   o `!` acende em NEON) e loga `ENSINO : P1 armado`. **#45: o estado mora no
   `decifrar`, não na UI** — `MandarFrase` sempre chama o `decifrar` (o
   `verificarManter` é só brilho visual; `manterResposta` virou legado).
2. A próxima fala passa pelo **pipeline (#48)** — o sequestro de turno morreu:
   - **entendida** (comando, nome, aposta, match ≥ 0,6) → responde normal e
     **cancela o ensino com ack transparente** ("deixa pra lá…") — nada grava;
   - **não entendida** → **o porteiro (#49)**: `scoreDo(buffer)` ≥ **piso
     0,10** → absorve; < piso → **P2** (retém a resposta e pede confirmação:
     só um "sim" grava; "não" descarta com ack).
3. Absorção: `chamadaFrase(buffer, resposta)` → **`ensinaEm` (#33: classifica
   ANTES de gravar; score ≥ 0,6 → o par cai no intento vencedor; senão
   `auto_aprendizado`)** → grava `intencoes.json` + `invalidaCacheIdf()`.
   Logs: `APRENDE : intento=…`/`auto_aprendizado` e `ENSINO : absorvido/
   retido/descartado score=…`.
4. **Guardas na gravação:** **segredo (#53)** e **contradição de relação
   (#56)** recusam com ack próprio — no ensino orgânico **e** no ensino dela
   (#50, que grava em `kaira.json` com o ack "Anotado…").
5. Invariante: **nada grava sem confirmação explícita ou porteiro aprovando**;
   fala entendida nunca vira dado silencioso (specs `EnsinoOrganicoTest`,
   `MaquinaContextoTest`, `EnsinoSegredoTest`). **#63:** par com **> 50
   palavras** (o teto do banco: max 11/exemplo, 46/resposta) **não é
   absorvido** — recusa curada ("Calma, isso e' um textao!"); o ensino
   curto/médio segue.
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
- ⭐ **A substância do tópico (#37/#54):** quando o tópico tem entrada no
  dicionário (`conhecimentos.json`), o "conta mais" fala o `resumo`; o
  **"explica melhor" (#54: fala com `explica/detalh/aprofund`)** fala o
  `texto` (flag per-turn `pedeExplicacao`); sem entrada ou sem texto, cai no
  resumo e depois no banco ("fallback nunca piora").

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
`KAIRA` (fala final) · `SESSAO` (tópico/nome/humor/followup/fato preservado) ·
`APRENDE` (#33) · `APRENDENDO` (fallback armando o ensino) · `ENSINO` (#48:
P1/P2, porteiro, absorvido/retido/descartado) · **`TRIAGEM`** (#50/#52/#56:
portas, memória, miss) · **`CONHECE`** (#37/#54: dicionário resumo/texto) ·
**`PENDENTE`/`USUARIO`** (#41: pergunta pendente armada/cancelada/absorvida) ·
`AVATAR` (#30/#34).
Tudo em `conversas.log` (gitignored) + tela ao vivo no botão **Log** (#29).

---

## 12. Como testar

- `powershell -NoProfile -ExecutionPolicy Bypass -File run_tests.ps1`
  → compila `src` + `test`, roda a suíte headless (JUnitCore, auto-descoberta das
  classes `*Test`) e o **avaliador 44/44** (conjunto congelado — regressão
  nunca pode cair). Flag `-SkipBuild` reusa o build anterior. O log da suíte vai
  pra `%TEMP%\kaira_suites_conversas.log` (#36) — o `conversas.log` nunca é
  tocado por teste.
- **⭐ O aceite completo (ciclo da casa) tem 4 portas:** spec da issue → suíte
  inteira (**67 classes verde**) → avaliador **44/44** → **replay do corpus**
  (`ReplayBaterias` + `score_replay.ps1`) com o alvo **GERAL ≥ 5,2 / GRAVE = 0**,
  comparando antes×agora turno a turno. Os scripts de validação vivem em
  `%TEMP%\validate_*.ps1`.
- Specs por issue vivem em `test/com/JCODE/...` — as da leva #50–#63:
  `TriagemPerguntaTest`, `IntegridadeFatoTest`, `TriagemVariacoesTest`,
  `EnsinoSegredoTest`, `ExplicaMelhorTest`, `PortaUsuarioPolimentoTest`,
  `MaquinaContextoTest`, `FatosModeloV2Test`, `OpcoesMemoriaTest`,
  `FatosSessaoV2Test`, `EnsinoNegativoTest`, `MoldurasSorteadasTest`,
  `ComposicaoRespostaTest`, `ComandoSeguroTest`, `HigieneBateriasTest`
  (+ anteriores: `FatosNovosTest`, `EnsinoTurbinadoTest`, `EnsinoOrganicoTest`,
  `DesempateContextoTest`, `AncoraTopicoTest`, `ContinuacaoTopicoTest`, …).
- Fluxo da casa: issue → **sonda (medir antes)** → spec vermelho → receita em
  código real → verde → `closes #N` → docs (PLANO §12.3 / RELATORIO).
- **⭐ O instrumento do replay:** `score_replay.ps1` mede antes×agora com a
  régua BOM=1 / PARCIAL=0,3 / RUIM=0 / GRAVE=−1; o degrau `triagem` conta
  **BOM** (mesma régua do `score_baterias.ps1`); o proxy **não vê o texto** da
  resposta — serve pra medir **direção**, não valor absoluto.

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
| Novo tipo de score/limiar | constantes `LIMIAR` (0.6) e `LIMIAR_MEDIA` (0.3) em `ControleResposta`; `PISO` (0.10) e `LIMIAR_CONFIANCA` (0.6) em `FormulaResposta` |
| **⭐ Fato/moldura dela** | `KAIRA/resources/kaira.json` (relações `gosta`/`nao_gosta` + moldura por relação com `{v}`); toda escrita passa por `ControleKaira.gravaFato` (guardas de segredo/contradição) |
| **⭐ Segredo novo na blocklist** | `SEGREDOS` em `ControleKaira` (termos + heurístico de 9+ dígitos) |
| **⭐ Porta de triagem** | `ControleResposta.respostaPorTriagem` (possessivos/relações + `ehPergunta`) |
| **⭐ Entrada nova no dicionário C3** | `conhecimentos.json` (termo = intent real, `resumo` falado e `texto` do "explica melhor", origem com fonte+licença) |
| **⭐ Máquina da pergunta pendente** | `ControleSessao.armaPergunta` + o bloco #41 no `decifrar` (limiar 0,6 / piso 0,10) |
| **⭐ Modelo de fato novo (relação)** | `FatoKaira`/`MolduraKaira` + migração em `ControleKaira.carregaCacheKAIRA` |
| Degrau novo de resposta | `decifrar` (cascata) + `Classifica` do `score_replay.ps1`/`score_baterias.ps1` (nota própria) |

---

## 14. Pendências conhecidas (para retomar)

- **Leva de dados do briefing — EXECUTADA** (DIALOGO out/2026): P1 ✅ (pergunta_kaaira),
  P3 ✅ (reacao_curta), P4 ✅ (trabalho social/marcos), P5 ✅ (casa_usuario), P6 ✅ (hobbies),
  pools de resposta por faixa ✅ (+156 respostas; faixa seca das intenções diárias de 1 → ≥3),
  P7.5 parcial ✅ (`{nome}` em pools), P7.3 parcial (3 empates dissolvidos; 5 aceitos/documentados).
  **P7.1: EXECUTADO e REVISADO** — a poda por *subconjunto* foi revertida (removia os âncoras canônicos: `obrigado`/`valeu`/`de nada` caíam em `elogio`); mantida a curadoria semântica (12 falsos amigos do `obrigado` adjetivo; ~607 exemplos).
- **#35/#36/#37/#38 — CONCLUÍDAS** (anti-repetição, higiene do log, C3 piloto, âncora de
  tópico). **C3: piloto (#37) + "explica melhor" (#54)** entregues — próximas fatias:
  extrator automático de resumo (stopwords/IDF) e escala + OpenWordnet-PT (CC BY 4.0).
- **Leva #50–#56 + #41 ENTREGUE (26/10)** — triagem + memória dela (modelo v2 com
  relações), integridade do fato, triagem v2, segredo no ensino, explica melhor,
  polimento da porta e a máquina da pergunta pendente. Estado: suíte 60 classes,
  avaliador 44/44, replay **5,3 / GRAVE 0**.
- **Ideia 1 COMPLETA (26–27/10):** triagem/memória com portas (#50/#52), integridade
  (#51), segredo (#53), modelo v2 dos **dois lados** (#56/#58), fluxo de opções
  (#57) e a captura negativa do ensino dela (#59).
- **Ideia 2 v1 ENTREGUE (#60):** molduras em pool + sorteio com anti-repetição
  (dela e do usuário, positiva e negativa). **v2 ENTREGUE (#61):** a composição
  `[abertura] + [fato] + [followup]` nas quatro portas de memória. **Fila:** a
  reação ao tópico (era do C3), C3 escala; **Markov (§8.2) em aberto** por decisão.
- **Leva de higiene das baterias ENTREGUE (#63):** nome blocklist + profissões,
  gênero do miss do usuário pelo possessivo (e o trecho da fala preserva o
  plural) e o teto de 50 palavras no ensino. Replay: **GERAL 5,4** — a
  blocklist converteu 9 turnos de `resposta-nome`.
- Classificador: **negação é cega** ("não" é stopword) — causa raiz dos empates
  `{gosto}/{entende}`; fix de código, não de dados. **Fila:** colisão `fala/fale`
  ("fale mais sobre isso" → `convite_conversa` por token-set `[fale, sobre]` —
  vaza do `continuacao_topico`).
- §7.3 restante: degrau médio com `{palavrasChave}` + followup (issue #16
  parcial); frame reconhecido/slot novo (§5.7).
- **O que o replay ainda mede como RUIM (245→207):** o fallback honesto ("me
  ensina") é RUIM na régua; a direção do trabalho é reduzir o fallback **sem
  engolir conversa** — o `aposta→aprender` de 63 turnos é herança do gate #49
  (medida e aceita).
- Opcional: theming dos `JOptionPane` (saem no estilo do sistema);
  decaimento do humor por dia sem interação (§7.1 opcional);
  `CriarResposta` — retorno descartado (decidir: usar ou remover).
