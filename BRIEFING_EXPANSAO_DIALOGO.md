# BRIEFING — Expansão de fluxo de diálogo do KAIRA

> **Para quem é este documento:** agente responsável pela próxima leva de dados/conteúdo do banco de intenções. Ele é auto-contido: não depende de conversa anterior.
> **Origem:** auditoria de sobreposição (out/2026, aprovada) + sonda de lacunas no classificador real (fumaca de frases, output colado abaixo).
> **Estado do projeto no momento:** 173 testes Java verdes + avaliador 14/14, com a base recém-expandida (90 intenções, 2.311 exemplos, 1.113 respostas, 195 followups). **Atualização pós-briefing:** as issues #24 (resposta ao pedido de nome) e #25 (iniciativa própria — `ControleSessao.perguntaInteresse()` anexa pergunta a cada 3 turnos, lendo os `followups` do tópico atual) foram fechadas; os specs `ControleSessaoNomeTest` e `IniciativaPropriaTest` estão verdes.

---

## 0. Contexto mínimo (leia antes de mexer)

- Arquivo da base: `KAIRA/resources/intencoes.json` (o **único** que o código carrega — `ControleResposta.carregaCache()`).
- Schema por intenção: `intento`, `exemplos:[{texto, origem}]`, `respostas:[{texto, origem, humorMin, humorMax}]`, `followups:[string]`, `acoes:[]`.
- Convenções da base: **exemplos em minúsculas e sem acento** (o `Normalizador` passa por cima de qualquer jeito, mas a convenção é essa); **respostas com acentuação e caixa corretas** (é o texto exibido); `origem` rastreia a proveniência item a item (ex.: `curadoria consultoria out/2026`).
- O classificador (`ControleResposta.melhorIntencaoDetalhado`): normaliza → stemma (12 regras) → remove stopwords (`KAIRA/resources/stopwords.txt`) → pontua por cobertura ponderada por IDF → casa também por **grupos de sinônimos** (`grupos_sinonimos.json`, dump do Wiktionary — cuidado: "série"~"trilogia" casa via grupo!).
- Faixas de confiança: ≥ 0,6 responde direto · 0,3–0,6 responde com "Foi isso que você quis dizer?" · < 0,3 fallback honesto (e ativa modo ensino).
- Empate de score = desempata por tokens excedentes e depois **ordem no arquivo** (quem vem primeiro vence).
- Frase do usuário entra no `fraseExata` ANTES do classificador: exemplo literal = resposta garantida.

### Regras não negociáveis

1. **Não expandir** `cumprimentar`, `agradecimento`, `despedida` (já dominam sem sustentar conversa; agradecimento tem 619 exemplos com ~46% de redundância interna).
2. `auto_aprendizado` **nasce vazio** (0 exemplos, 0 respostas) — caixa de entrada do modo ensino.
3. **Nunca** adicionar exemplo cujo texto normalizado seja igual a uma entrada do `test/resources/avaliacao.json` ("oi", "ola", "tchau", "tudo bem", "bom dia", "como vc está", "e ai beleza", "qual seu jogo favorito"... — 14 casos, todos regressão) — isso quebra o avaliador. **Cuidado extra (roubo por token-set):** mesmo sem igualdade literal, exemplo novo com o MESMO token-set de um caso congelado disputa a rotação e pode quebrar a regressão. Caso concreto: `"e voce, como esta"` tem os tokens {voce, como, esta} — os mesmos do caso `"como vc está"`; no P1, **deixar de fora** variantes nessa forma (ou rodar o avaliador e conferir 14/14 antes de aceitar a leva).
4. Não quebrar os 6 casos fuzzy do `CuradoriaTemasTest.frasesNaoExatasCaemNoTemaCerto` ("recomenda um filme bom pra hoje" → filme, etc.).
5. Toda leva nova deve passar a suíte completa: **173 testes** (`CuradoriaTemasTest`, `CuradoriaConsultoriaTest`, `IniciativaPropriaTest`, `ControleSessaoNomeTest`, `AvaliadorTest` incluídos — os dois últimos eram vermelhos por TDD quando este briefing foi escrito e hoje estão **verdes**, issues #24/#25 fechadas; não os mexa). Nota: `CuradoriaConsultoriaTest` existe só na árvore de trabalho local (não commitado no repo).
6. Exemplos de token-set idêntico em intenções diferentes = matriz vermelha (o teste pega). Frase só com stopwords = classificador devolve null.
7. **Toda intenção nova deve nascer com `followups`** (1–3 perguntas na voz da KAIRA): o mecanismo da §7.2 está vivo desde a issue #25 (`perguntaInteresse()` lê os `followups` do tópico atual e anexa à resposta a cada 3 turnos). Intenção sem followups só recebe pergunta de interesse do pool genérico — desperdiça a personalidade do banco.

### Como rodar a validação (CWD na raiz do projeto)

```
javac -encoding UTF-8 -cp "out\opencode-build;externallibs\*" -d out\opencode-build (todos os .java de src e test)
java -cp "out\opencode-build;<libs explícitas>" org.junit.runner.JUnitCore <classes de teste>
```

**Atenção:** `externallibs` tem DOIS `jackson-annotations` (2.2.3 e 2.22). O wildcard `externallibs\*` pega o **2.2.3** (alfabético) e explode com `NoSuchFieldError`. Use classpath explícito: `jackson-annotations-2.22.jar;jackson-core-2.22.3.jar;jackson-databind-2.22.3.jar;commons-io-2.13.0.jar;junit-4.13.2.jar;hamcrest-core-1.3.jar`.

**Workflow de dados recomendado:** escrever patches em `KAIRA/resources/intencoes-consultoria-*.json` e rodar `python merge_intencoes_consultoria.py` (idempotente, valida duplicata/stopword-only/entrada-do-avaliador antes de gravar). Para correções pontuais na base antiga, edição direta + suíte. Ferramenta de sonda pronta: `test/com/JCODE/tools/FumacaDiaria.java`.

---

## 1. Diagnóstico com prova — o que a sonda real mostrou

O núcleo **temático** está sólido (emoções, social, rotina, trabalho, dinheiro, mídia). O que falta é **fluxo de diálogo** — os movimentos que fazem um papo andar. Sonda rodada no classificador real (base atual):

```
e voce                                 -> FALLBACK (nao sei)
e vc, o que fez hoje                   -> pet (1,00)     ← absurdo: "meu cachorro fez bagunca hoje" contém {fez, hoje}
o que voce fez hoje                    -> pet (1,00)     ← idem
voce tem algum hobby                  -> pet (0,57)     ← frame "tem algum X" colide com "tem algum pet"
fala mais disso                        -> mudar_assunto (0,52)  ← SEMÂNTICA INVERTIDA
conta mais sobre isso                  -> convite_conversa (0,55)
e depois o que aconteceu               -> cotidiano (0,50)
nossa                                  -> agradecimento (1,00)
serio isso                             -> namoro_usuario (1,00)  ← "estou namorando serio" cobre {serio}
verdade                                -> elogio (1,00)
concordo com voce                      -> score 0,00 → fallback
meu colega de trabalho e engracado     -> transito_transporte (0,34)
happy hour com a equipe hoje           -> agradecimento (0,10)
fui promovido esse mes                 -> amigos_usuario (0,32)
reformei a cozinha                     -> cozinhar_usuario (0,50)
mudanca de apartamento esse mes        -> aparencia_usuario (0,30)
curso online de fotografia             -> jogar (0,33)
certificacao nova esse ano             -> gostos_ia (0,28)
```

**Duas fragilidades estruturais que os dados novos precisam respeitar:**

- **Frases só-stopword são invisíveis ao classificador.** "e voce" → tokens = {} → `melhorIntencaoDetalhado` = null → **só o match exato pega**. Toda frase nessa condição precisa dos literais como exemplos.
- **Frases de 1–2 tokens são o ponto fraco do modelo.** Score = cobertura dos tokens DA ENTRADA; qualquer exemplo longo que *contenha* esses tokens ganha 1,0 (é por isso que "o que voce fez hoje" cai em pet). Intenções de frases curtas precisam de: literais exatos + variantes com um token distintivo que não exista em outras intenções.

---

## 2. Trabalho priorizado

### P1 — Intenção `pergunta_kaaira` ("e você?" / perguntas de retorno) — **a maior lacuna**

O movimento mais comum da conversa diária não existe: o usuário conta algo, a Kaira responde, e o usuário **devolve a pergunta**. Hoje isso é fallback ou resposta de cachorro.

- **Exemplos (dados):** "e voce", "e vc", "e voce?", "e ai voce", ~~"e voce, como esta"~~ (**NÃO incluir nesta leva**: token-set colide com o caso congelado "como vc está" do avaliador — ver regra 3), "e vc, o que fez hoje", "o que voce fez hoje", "o que voce anda fazendo", "conta sua rotina", "como e seu dia", "voce fez o que hoje", "o que voce faz quando eu nao estou aqui" (hoje: 0,71 em rotina_usuario — trazer pra cá), "voce tem algum hobby" (hoje: 0,57 em pet — trazer pra cá), "me conta sua vida".
- **Regra técnica:** TODA variante só-stopword ("e voce", "e vc") precisa estar como literal. Variantes com token real ("o que voce fez hoje" = {fez, hoje}) precisam de exemplos que contenham esses tokens **nesta** intenção com resto menor que o exemplo de pet ("meu cachorro fez bagunca hoje" tem resto 2 — um "o que voce fez hoje hoje nao" hmm, basta "o que voce fez hoje" literal + "voce fez o que hoje" etc. cobrem com resto 0).
- **Respostas (a parte divertida):** na voz/personalidade dela — curiosa, brincalhona, autoconsciente de IA ("Sem corpo, sem sono e sem fim de semana... mas hoje eu processei nossas conversas e aprendi 3 fatos sobre polvos"). É o ganho de carisma mais barato do projeto.
- **Critérios de aceite (viram spec):** "e voce" → pergunta_kaaira (match exato); "o que voce fez hoje" → pergunta_kaaira com score 1,0; "voce tem algum hobby" → pergunta_kaaira.

### P2 — Continuação de assunto (`continuacao_topico`) — hoje está INVERTIDO

"Fala mais disso" / "conta mais sobre isso" / "e depois?" roteiam para **mudar_assunto** — o oposto do que o usuário quer. É a §12.2 do `PLANO_DE_EVOLUCAO.md` (usar o `topicoAtual`, que existe desde a issue #19, para responder sobre o último tema).

- **Dados:** intenção `continuacao_topico` com "conta mais sobre isso", "fala mais disso", "e depois o que aconteceu", "porque disso", "me explica melhor essa parte", "conta o resto", "sigue" (gíria), "e aí, continua".
- **Código (necessário):** a resposta consome `Sessao.topicoAtual` (via slot `{topico}` ou montagem por fragmento). Sem o código, a intenção só devolve frase genérica — aceitável como passo 1. **Nota (pós-briefing):** P2 é tarefa da casa (dado + código, fluxo de issues do projeto) — o padrão de ler `topicoAtual` já existe na `perguntaInteresse()` da issue #25; **não** fazer P2 nesta leva de dados.
- **Cuidado de colisão:** "falar de outra coisa" (mudar_assunto) vs "falar mais disso" — distinguem por um token; garantir que os exemplos de mudar_assunto não cubram os tokens das variantes de continuar, e vice-versa.
- **Critério de aceite:** "fala mais disso" → continuacao_topico (hoje: mudar_assunto 0,52) e "falar de outra coisa" → mudar_assunto continua.

### P3 — Intenção `reacao_curta` (interjeições: "nossa", "sério?!", "verdade", "concordo")

Reações curtas são o combustível do papo e hoje cada uma cai num lugar absurdo (agradecimento / namoro / elogio / fallback).

- **Exemplos:** "nossa", "nossa isso", "serio isso", "nao acredito", "verdade", "e verdade", "concordo com voce", "discordo", "exato", "isso mesmo", "boa ideia", "entendi agora", "faz sentido", "puxa vida", "que doideira".
- **Regra técnica (a fragilidade de 1–2 tokens):** todo literal curto precisa estar exato; e cada exemplo com token real precisa de token distintivo não presente em outras intenções (ex.: "concordo com voce" = {concordo} — não pode existir {concordo} solto em outra intenção).
- **Empates existentes:** há 8 empates de token-único na base antiga (ver §4-P7); "que legal" hoje empata elogio×reacao_diversa. Não criar novos.
- **Respostas:** curtas, na vibe dela ("Sério! Sério. Te conto mais se você quiser, hehe").
- **Critério de aceite:** "nossa" → reacao_curta (hoje: agradecimento), "serio isso" → reacao_curta (hoje: namoro 1,00), "concordo com voce" → reacao_curta (hoje: fallback).

### P4 — Tecido social do trabalho + marcos de carreira (expandir `trabalho_usuario` ou criar intents irmãs)

`trabalho_usuario` cobre chefe/reunião/cansaço, mas não o social nem os marcos:

- **Exemplos:** "meu colega de trabalho e engracado" (hoje: transito 0,34), "happy hour com a equipe hoje" (hoje: agradecimento 0,10), "fui promovido esse mes" (hoje: amigos 0,32), "ganhei um aumento", "me demitiram", "comecei num emprego novo" (existe — não duplicar), "meu colega me ajudou hoje", "almoco com a equipe", "primeiro dia no emprego novo", "aposentadoria chegando", "feira de trabalho no sabado".
- **Respostas:** marcos (promoção/aumento/demissão) merecem reação à altura — comemorar ou acolher conforme o caso.

### P5 — Casa/moradia

- **Exemplos:** "reformei a cozinha" (hoje: cozinhar 0,50), "mudanca de apartamento esse mes" (hoje: aparencia 0,30), "to reformando a casa", "pintei a parede", "aluguel subiu de novo", "meu vizinho barulhento", "mudei de bairro", "arrumando o apartamento novo", "mobilia nova chegou".
- Ou intenção nova (`casa_usuario`) ou expansão de `rotina_usuario` — decisão do agente; só não pode colidir com `planos_futuro` (planos de mudar ≠ mudança que aconteceu).

### P6 — Hobbies/prática criativa + aprendizado de adulto

- **Exemplos:** "curso online de fotografia" (hoje: jogar 0,33), "certificacao nova esse ano" (hoje: gostos_ia 0,28), "comecei a desenhar" (hoje: atividades 0,60 — já razoável), "minha planta floresceu" (hoje: atividades 0,40), "aprendendo a tocar violao" (atenção: colide com `planos_futuro` "quero aprender a tocar violao" — usar formas de **prática em andamento** vs **plano**), "tricô da vovó", "dança de salao na quarta", "colecionando moedas antigas".
- Expandir `atividades_usuario` resolve a maioria; para "curso/certificação", cuidado com `estudos` (formal) vs hobbies — tokens distintos ("certificação", "curso online" são da vida adulta, mas podem viver em estudos se o agente preferir).

### P7 — Higiene e performance (transversal, prioridade entre P1–P6 ou depois)

1. **Podar `agradecimento`** (619 → ~250): ~46% de redundância interna (família "obrigado pela ajuda"); efeitos: base menor, classificação mais rápida, menos ruído.
2. **Cachear `pesosIdf`** (código): hoje é recalculado varrendo TODOS os exemplos em CADA classificação → cada mensagem é O(N²). Com 2.311 exemplos, dobra o custo de toda resposta.
3. **Dissolver os 8 empates de token-único da base antiga** (tabela no relatório de auditoria `AppData\Local\Temp\opencode\auditoria_sobreposicao.md` — os piores: "você entende" → critica; agradecimento variante → reacao_diversa). Método: enriquecer o exemplo de 1 token com um 2º token distintivo (ex.: "voce nao entende" → "voce nao entende nada").
4. **Fatos novos na sessão** (código `ControleSessao` FATOS + intents `lembra_*`): hoje só gosta/mora/trabalha — adicionar time de futebol, música favorita, nome do pet, sonho. Estrutura idêntica às existentes.
5. **Usar os slots nas respostas** (dados): `{nome}` quase não é usado, `{topico}` zero — inserir em saudações/retornos (render já existe, `ControleResposta.preencheSlots`).

---

## 3. O que NÃO precisa ser feito (evitar desperdício)

- Núcleo emocional/social/rotina/mídia: **sólido** — não expandir por expandir.
- Cumprimento/agradecimento/despedida: **proibido expandir**.
- Filosofia/religião, sonhos, notícias, redes, idiomas, semana, horário: cobertos pela leva anterior.
- Novos temas "de dicionário" (base de conhecimento geral estilo Wikipédia): é outra frente (§12.1 do plano, bases livres licenciadas) — fora do escopo deste briefing.

---

## 4. Critérios de aceite globais (a leva só está pronta quando)

1. Suíte Java completa verde: **173 testes** (`CuradoriaTemasTest`, `CuradoriaConsultoriaTest`, `IniciativaPropriaTest`, `ControleSessaoNomeTest`, `AvaliadorTest` incluídos — todos verdes hoje; specs do nome e da iniciativa fecharam nas issues #24/#25).
2. Avaliador de regressão: **14/14**.
3. Sonda P1–P6: todas as frases do diagnóstico roteiam para a intenção certa, com score ≥ 0,6 (ou match exato).
4. Nova spec no estilo do projeto (`Curadoria*Test`): volume + matriz de auto-classificação + frases não-exatas caem no tema certo.
5. Auditoria de sobreposição refeita: zero duplicata dura entre intenções, zero empate novo de token-set.

## 5. Ordem sugerida

P1 (maior ganho percebido, só dados) → P3 (só dados, alivia misroutes irritantes) → P2 (dados + código pequeno) → P4/P5/P6 (dados, volume) → P7 (higiene/performance). Se for uma leva só de dados: P1+P3+P4+P5+P6 numa tacada, P7 separado.

**Divisão acordada (pós-briefing):** a leva de dados deste briefing fica com **P1 + P3 + P4 + P5 + P6 + P7.1 (podar agradecimento) + P7.3 (dissolver empates) + P7.5 (usar slots nas respostas)**; **P2, P7.2 (cache de pesosIdf) e P7.4 (fatos novos) são tarefas da casa** (dado+código no fluxo de issues do projeto) — não fazer aqui.
