# BRIEFING — Expansão de fluxo de diálogo do KAIRA

> **Para quem é este documento:** agente responsável pela próxima leva de dados/conteúdo do banco de intenções. Ele é auto-contido: não depende de conversa anterior.
> **Origem:** auditoria de sobreposição (out/2026, aprovada) + sonda de lacunas no classificador real (fumaca de frases, output colado abaixo).
> **Estado do projeto no momento:** 209 testes Java verdes + avaliador 14/14, com a base em **98 intenções / 2.420 exemplos / 1.291 respostas / 211 followups**. **Leva de dados DIALOGO out/2026 executada** (patches `intencoes-dialogo-*.json` + `merge_intencoes_consultoria.py`, origem 'curadoria dialogo out/2026' + literais de match exato em origem 'autor'): **P1 ✅** (pergunta_kaaira — literais 'e voce'/'e vc' + variantes, todos com >0,6 ou match exato), **P3 ✅** (reacao_curta), **P4 ✅** (trabalho social/marcos), **P5 ✅** (casa_usuario), **P6 ✅** (hobbies), **pools de resposta por faixa ✅** (as 33 de conversa diária saíram de 1 para ≥3 na faixa seca — +156 respostas), **P7.5 parcial** ({nome} em pools), **P7.3 parcial** (3 dos 8 empates dissolvidos; 5 documentados — avaliador-locked ou colapso de negação, ver §2-P7). **P7.1 (podar agradecimento) segue pendente.** **C1 do §6 concluído (issue #35 — anti-repetição de resposta).**
>
> **Issues da casa fechadas (contexto):** #24 (resposta ao pedido de nome), #25 (iniciativa própria — `perguntaInteresse()` lê os `followups` do tópico atual), #26 (P7.2 — cache de `pesosIdf`, suíte ~4x mais rápida), #27/#28 (P2 — `continuacao_topico` + pool segurado no turno do pedido), #32 (P7.4 — fatos novos: time, música, pet, sonho, "nasceu em", "meu X favorito é Y"), #33 (ensino turbinado). Specs verdes: `ControleSessaoNomeTest`, `IniciativaPropriaTest`, `ContinuacaoTopicoTest`, `ControleRespostaCacheIdfTest`, `FatosNovosTest`, `EnsinoTurbinadoTest`, `CuradoriaConsultoriaTest` (cobre as duas levas + literais de match exato).

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
3. **Nunca** adicionar exemplo cujo texto normalizado seja igual a uma entrada do `test/resources/avaliacao.json` ("oi", "ola", "tchau", "tudo bem", "bom dia", "como vc está", "e ai beleza", "qual seu jogo favorito"... — 14 casos, todos regressão) — isso quebra o avaliador. **Cuidado extra (zona de empate por token-set):** o que colide é o token-set **pós-stopwords**, não as palavras cruas. Caso concreto: `"e voce, como esta"` tem token-set `{esta}` ("voce" e "como" são stopwords) — a mesma zona de empate do caso congelado `"como vc está"` E do achado #2 da auditoria (status_geral × localizacao). Um exemplo novo `{esta}` disputa a rotação e, pior, se auto-classificaria em status_geral (empate resolvido pela ordem do arquivo) → matriz vermelha. No P1, **deixar de fora** variantes nessa forma (critério geral: conferir o token-set pós-stopwords de toda frase curta antes de incluir).
4. Não quebrar os 6 casos fuzzy do `CuradoriaTemasTest.frasesNaoExatasCaemNoTemaCerto` ("recomenda um filme bom pra hoje" → filme, etc.).
5. Toda leva nova deve passar a suíte completa: **199 testes** (`CuradoriaTemasTest`, `CuradoriaConsultoriaTest`, `ContinuacaoTopicoTest`, `ControleRespostaCacheIdfTest`, `FatosNovosTest`, `EnsinoTurbinadoTest`, `IniciativaPropriaTest`, `ControleSessaoNomeTest`, `AvaliadorTest` incluídos — todos verdes, issues #24–#28 e #32/#33 fechadas; não os mexa). Nota: `CuradoriaConsultoriaTest` existe só na árvore de trabalho local (não commitado no repo).
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
- **Regra técnica:** TODA variante só-stopword ("e voce", "e vc") precisa estar como literal. Variantes com token real ("o que voce fez hoje" = {fez, hoje}) precisam de exemplos que contenham esses tokens **nesta** intenção com resto menor que o do exemplo de pet: o literal "o que voce fez hoje" (resto 0) ganha de "meu cachorro fez bagunca hoje" (resto 2 — cachorro e bagunca sobram).
- **Respostas (a parte divertida):** na voz/personalidade dela — curiosa, brincalhona, autoconsciente de IA ("Sem corpo, sem sono e sem fim de semana... mas hoje eu processei nossas conversas e aprendi 3 fatos sobre polvos"). É o ganho de carisma mais barato do projeto.
- **Critérios de aceite (viram spec):** "e voce" → pergunta_kaaira (match exato); "o que voce fez hoje" → pergunta_kaaira com score 1,0; "voce tem algum hobby" → pergunta_kaaira.

### P2 — Continuação de assunto (`continuacao_topico`) — hoje está INVERTIDO

"Fala mais disso" / "conta mais sobre isso" / "e depois?" roteiam para **mudar_assunto** — o oposto do que o usuário quer. É a §12.2 do `PLANO_DE_EVOLUCAO.md` (usar o `topicoAtual`, que existe desde a issue #19, para responder sobre o último tema).

- **Dados:** intenção `continuacao_topico` com "conta mais sobre isso", "fala mais disso", "e depois o que aconteceu", "porque disso", "me explica melhor essa parte", "conta o resto", "sigue" (gíria), "e aí, continua".
- **Código (necessário):** a resposta consome `Sessao.topicoAtual` (via slot `{topico}` ou montagem por fragmento). Sem o código, a intenção só devolve frase genérica — aceitável como passo 1. **Nota (pós-briefing):** P2 é tarefa da casa (dado + código, fluxo de issues do projeto) — o padrão de ler `topicoAtual` já existe na `perguntaInteresse()` da issue #25; **não** fazer P2 nesta leva de dados. — *(entregue — issue #27: `continuacao_topico` com 10 literais + 6 respostas (2 com `{topico}`) + 2 followups; o hook de tópico pula o intento e o `topicoAtual` sobrevive; literal `"muda de assunto"` acrescentado a `mudar_assunto` pela lacuna da sonda)* + *(issue #28: copa ancorada em `{topico}` na voz dela, `perguntaInteresse` segura o pool genérico no turno do pedido (fonte 1 passa), literais `conta mais`/`me fale mais` — rastreado no `conversas.log`)*
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
2. **Cachear `pesosIdf`** (código): hoje é recalculado varrendo TODOS os exemplos em CADA classificação → cada mensagem é O(N²). Com 2.311 exemplos, dobra o custo de toda resposta — *(entregue — issue #26: cache por base de `pesosIdf` + tokens de exemplos (`garanteCacheIdf`, rebuild por identidade; `invalidaCacheIdf` no `ensinaEm`); scoring e desempate leem o cache)*
3. **Dissolver os 8 empates de token-único da base antiga** (tabela no relatório de auditoria `AppData\Local\Temp\opencode\auditoria_sobreposicao.md`). **Resultado (leva diálogo):** 3 dissolvidos em `reacao_diversa` ("legal"→"legal demais", "nao obrigado"→"nao obrigado agora", "mais nada"→"mais nada entao"); **5 ficam documentados como aceitos** e não se resolvem por dados: `{bem,tudo}` e `{esta}` são **avaliador-locked** (editar quebraria as regressões "tudo bem"/"como vc está"/"onde você está"); `{gosto}`, `{entende}` e `{robo}` sofrem **colapso de negação/lexical** ("eu nao gosto" ≡ "gosto"; "voce nao entende" ≡ "entende") — qualquer edição só move a ambiguidade e piora algum literal. **Fix de verdade é código** (o classificador tratar negação) — vale abrir issue própria.
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

1. Suíte Java completa verde: **199 testes** (`CuradoriaTemasTest`, `CuradoriaConsultoriaTest`, `ContinuacaoTopicoTest`, `ControleRespostaCacheIdfTest`, `FatosNovosTest`, `EnsinoTurbinadoTest`, `IniciativaPropriaTest`, `ControleSessaoNomeTest`, `AvaliadorTest` incluídos — todos verdes hoje; issues #24–#28 e #32/#33 fechadas).
2. Avaliador de regressão: **14/14**.
3. Sonda P1–P6: todas as frases do diagnóstico roteiam para a intenção certa, com score ≥ 0,6 (ou match exato).
4. Nova spec no estilo do projeto (`Curadoria*Test`): volume + matriz de auto-classificação + frases não-exatas caem no tema certo.
5. Auditoria de sobreposição refeita: zero duplicata dura entre intenções, zero empate novo de token-set.

## 5. Ordem sugerida

P1 (maior ganho percebido, só dados) → P3 (só dados, alivia misroutes irritantes) → P2 (dados + código pequeno) → P4/P5/P6 (dados, volume) → P7 (higiene/performance). Se for uma leva só de dados: P1+P3+P4+P5+P6 numa tacada, P7 separado.

**Divisão acordada (pós-briefing):** a leva de dados deste briefing fica com **P1 + P3 + P4 + P5 + P6 + P7.1 (podar agradecimento) + P7.3 (dissolver empates) + P7.5 (usar slots nas respostas)**; **P2, P7.2 (cache de pesosIdf) e P7.4 (fatos novos) são tarefas da casa** (dado+código no fluxo de issues do projeto) — não fazer aqui. **Concluídas da casa: P2 ✅, P7.2 ✅ e P7.4 ✅ (issue #32).**

---

## 6. Leva de CÓDIGO (tarefas da casa) — profundidade de resposta e higiene

> Medições feitas na revisão de out/2026, com a base em 95 intenções / 2.348 exemplos / 1.135 respostas.
> **Motivo:** a expansão dobrou o RECONHECIMENTO (1.129→2.348 exemplos, 2,08x) mas as RESPOSTAS cresceram menos (723→1.135, 1,57x). Para conversa longa, o que a usuária *ouve* importa mais que o que é entendido — e é onde o efeito "ela sempre diz a mesma coisa" aparece.

### C1 — Anti-repetição de resposta (código) — **prioridade alta — CONCLUÍDO (issue #35)**

- **Fato verificado:** `escolheMultiRespostas` (ControleResposta) é `Math.random` puro. Só existem anti-repetição em `ultimoFallback` (fallback) e `ultimoInteresse` (pergunta de interesse). **Resposta de intenção pode repetir imediatamente** — mencionar o mesmo assunto 3x na conversa pode sortear a MESMA frase duas vezes seguidas.
- **Medição de suporte:** 39 intenções têm alguma faixa de humor com **menos de 3 respostas**; as 33 de conversa diária têm **exatamente 1** na faixa seca (0–3).
- **Receita:** guardar as últimas N (2–3) respostas entregues por intenção (mapa intento→fila) e excluí-las do sorteio — mesmo espírito do `ultimoFallback`. Se o pool filtrado por humor esvaziar, cair no comportamento atual (nunca ficar sem resposta).
- **Critério de aceite:** spec nova (ex.: `AntiRepeticaoTurnoTest`) — 3 turnos seguidos no mesmo intento nunca entregam a mesma frase.

### C2 — Higiene do log: separar teste de uso real (código/processo) — **CONCLUÍDO (issue #36)**

- **Fato verificado:** o `conversas.log` tem 5,3 MB com só ~31 entradas de uso real — os testes escrevem nele (o `Registro` roda em contexto de teste; os milhares de `topico=` vêm do simulador/suíte). Hoje o log **não serve** para dirigir curadoria.
- **Receita:** silenciar/redirecionar o `Registro` durante execução de teste (flag de modo teste ou destino separado); manter o `conversas.log` só com uso real da janela.
- **Por que importa:** o plano (§11.1) declara o log a fonte mais valiosa de dados ("as conversas reais"). Com o log limpo, a próxima curadoria deixa de ser sonda e vira **relatório de falhas reais**: o que cai em fallback, o que cai em aposta, que assuntos repetem.
- **Receita de mineração (quando o log estiver limpo):** contar por `DEGRAU` (direto/aposta/aprender), listar as `ENTRADA` que viraram `aprender`, agrupar por intento — saída = lista priorizada de buracos.

### C3 — Base de conhecimento real atrás do `{topico}` (§12.1 do plano) — **a fronteira de profundidade — PILOTO ENTREGUE (issue #37)**

- **Fato que motiva:** o P2 (continuação, #27/#28) já funciona — "conta mais" cai em `continuacao_topico` e a resposta usa `{topico}`. Mas **não há conteúdo atrás do tópico**: por isso a resposta é genérica.
- **Receita:** o recorte de bases livres já levantadas no plano (OpenWordnet-PT CC BY 4.0 + lead da Wikipédia PT) → `KAIRA/resources/conhecimentos.json` no padrão do projeto (origem por item, como sempre). A intenção de continuação passa a montar a resposta do verbete do tópico atual em vez da frase genérica.
- **Dependência:** vem depois de C1/C2 e do fechamento da leva de dados — é o item que separa "classificador bom" de "conversa de verdade".
- **Resultado (piloto, issue #37):** `conhecimentos.json` com 15 entradas-curadoria (Wikipédia PT CC BY-SA 3.0, texto adaptado, origem por item), `ControleConhecimento` (null-seguro) e encaixe no `continuacao_topico` — pedir "conta mais" sobre trabalho/esporte/música... agora fala a substância do tópico. Próximas fatias: "explica melhor" (campo `texto` já existe no dicionário), extrator automático de resumo (stopwords/IDF), escala da base + OpenWordnet-PT.

