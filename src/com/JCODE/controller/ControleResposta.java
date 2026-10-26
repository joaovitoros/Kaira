package com.JCODE.controller;

import com.JCODE.domain.*;
import com.JCODE.util.Calculos;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Normalizador;
import com.JCODE.util.Registro;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ControleResposta {

	static ObjectMapper objectMapper;

	static Intencoes intencoesCache;

	private static final double LIMIAR = 0.6;

	private static final double LIMIAR_MEDIA = 0.3;

	// #41/#42 - tokens que nao carregam assunto sozinhos, agora em GRUPOS de
	// variante (genero/numero/sinonimo). A fraseSoGenerica usa o flatten (mesmo
	// comportamento de antes), mas o desempate (#41) casa variante com variante:
	// "seu"~"sua" e "preferido"~"favorito". E o stem que NAO faz (medido:
	// stem("seu")="seu", stem("sua")="sua"), e o sinonimo do dump tambem nao
	// cobria - o caso vivo de 17/10 ("qual o seu preferido?" com topico musica)
	// caia no gostos_ia 1.00 por isso.
	private static final List<List<String>> TOKENS_GENERICOS_GRUPOS = List.of(
			List.of("seu", "sua", "seus", "suas"),
			List.of("qual", "quais"),
			List.of("isso", "esse", "essa", "este", "esta", "aquilo", "algo"),
			List.of("coisa", "coisas", "assunto", "assuntos"),
			List.of("preferido", "preferida", "favorito", "favorita",
					"preferidos", "preferidas", "favoritos", "favoritas"),
			List.of("gostar", "querer", "falar", "dizer", "contar"),
			List.of("sobre", "mais", "menos", "tambem", "mesmo"),
			List.of("outra", "outro"));

	private static Set<String> tokensGenericos = null;
	// #42 - token generico stemmado -> tokens stemmados do seu grupo (pro
	// desempate casar variante com variante)
	private static Map<String, Set<String>> gruposGenericos = null;

	static Set<String> stopwords;

	static Set<String> tokensComuns;

	private static final List<String> FRASES_FALLBACK = List.of(
			"Ainda não sei falar disso... me ensina? O que você quis dizer?",
			"Ainda tô aprendendo isso — me conta o que você quis dizer, e me ensina?",
			"Essa ainda não sei... me ensina? Quero aprender o que você quis dizer?");

	private static final List<String> FRASES_FALLBACK_SLOT = List.of(
			"Ainda não sei sobre {slot}... me ensina? O que você quis dizer?",
			"Ainda tô aprendendo sobre {slot} — me conta o que você quis dizer, e me ensina?",
			"Essa parte de {slot} ainda não sei... me ensina? Quero aprender o que você quis dizer?");

	private static String ultimoFallback = null;

	/** #35 - gancho de sorteio: teste injeta ordem deterministica (refactor). */
	static IntUnaryOperator semeador = n -> (int) (Math.random() * n);

	/** #35 - ultimas frases entregues por intento (as N ultimas saem do sorteio). */
	private static final Map<String, List<String>> ultimasRespostas = new HashMap<>();
	private static final int ULTIMAS_GUARDADAS = 2;

	// #26 (briefing P7.2) - cache de IDF e de tokens por base: a base so
	// muda no carregaCache (instancia nova -> rebuild por identidade) ou
	// no ensinaEm (mutacao -> invalidaCacheIdf)
	private static Intencoes cacheRaizIdf = null;
	private static Map<String, Double> cachePesosIdf = null;
	private static Map<Exemplo, Set<String>> cacheTokensExemplos = null;
	private static double cacheTotalExemplos = 0;

	// #52 - triagem v2: interrogativo em QUALQUER posicao, possessivo em
	// qualquer posicao, plural simples e fillers ("de verdade", "hein",
	// "mesmo", "e qual?") nao matam mais.
	private static final Pattern INTERROGATIVO = Pattern.compile(
			"(?i).*\\b(?:qual|quais|quem|quando|onde|como|quanto|quantos|quantas)\\b.*");
	private static final Pattern POSSE_DELA = Pattern.compile(
			"(?i)\\b(sua|seu|suas|seus)\\s+([\\p{L}]+)(?:\\s+(favorit[oa]|preferid[oa]))?");
	private static final Pattern POSSE_USUARIO = Pattern.compile(
			"(?i)\\b(meu|minha|meus|minhas)\\s+([\\p{L}]+)(?:\\s+(favorit[oa]|preferid[oa]))?");
	private static final String MOLDURA_USUARIO =
			"pelo que voce me contou, o seu %s e %s!";

	// #52 - pergunta de verdade: "?" ou interrogativo que ABRE a frase ou
	// lead imperativo ("me conta qual..."); "qual/quais/quem" valem em
	// qualquer posicao. "quando/onde/como" soltos no meio de uma declaracao
	// ("meu sonho e viajar quando ele crescer") NAO fazem pergunta.
	private static final Pattern ABRE_PERGUNTA = Pattern.compile(
			"(?i)^(?:qual|quais|quem|quando|onde|como|quanto|quantos|quantas"
					+ "|me\\s+conta|me\\s+diz|diz|fala|quero\\s+saber)\\b");
	private static final Pattern QUALQUER_POSICAO = Pattern.compile(
			"(?i)\\b(?:qual|quais|quem)\\b");

	public static List<String> frasesFallback() {
		return FRASES_FALLBACK;
	}

	public static String respostaPorTriagem(String fala) {
		if (fala == null || !ehPergunta(fala))
			return null;

		// PORTA DELA: a memoria pessoal dela
		Matcher dela = POSSE_DELA.matcher(fala);
		if (dela.find()) {
			for (String chave : candidatosDe(dela.group(2), dela.group(3))) {
				String valor = ControleKaira.buscaFato(chave);
				if (valor == null)
					continue;
				String moldura = ControleKaira.molduraDe(chave);
				Registro.linha("TRIAGEM   : memoria dela chave=" + chave);
				return moldura != null ? moldura.replace("{v}", valor) : valor;
			}
			// MISS dela: se o USUARIO contou o mesmo fato, responde espelho -
			// o pipeline nunca le o "seu" como sendo do usuario (#52/B1)
			Map<String, String> fatos = ControleSessao.get().getFatos();
			for (String chave : candidatosDe(dela.group(2), dela.group(3))) {
				if (fatos == null || !fatos.containsKey(chave))
					continue;
				String poss = dela.group(1).toLowerCase(java.util.Locale.ROOT).startsWith("sua")
						? "a sua" : "o seu";
				Registro.linha("TRIAGEM   : miss dela, espelho usuario chave=" + chave);
				return "Essa eu ainda nao sei - mas " + poss + " " + chave
						+ " eu guardei: " + fatos.get(chave) + "!";
			}
			return null;
		}

		// PORTA USUARIO: os fatos que ele contou
		Matcher usu = POSSE_USUARIO.matcher(fala);
		if (usu.find()) {
			Map<String, String> fatos = ControleSessao.get().getFatos();
			for (String chave : candidatosDe(usu.group(2), usu.group(3))) {
				if (fatos == null || !fatos.containsKey(chave))
					continue;
				Registro.linha("TRIAGEM   : fato usuario chave=" + chave);
				// #55 - o artigo sai do genero da chave ("favorita"/"preferida" -> a sua)
				String artigo = chave.toLowerCase(java.util.Locale.ROOT).endsWith("favorita")
						|| chave.toLowerCase(java.util.Locale.ROOT).endsWith("preferida")
						? "a sua" : "o seu";
				return "pelo que voce me contou, " + artigo + " " + chave + " e "
						+ fatos.get(chave) + "!";
			}
			String pedaco = usu.group(3) != null
					? usu.group(2) + " " + usu.group(3) : usu.group(2);
			// #55 - "qual meu nome?" com o nome ja conhecido: ela SABE - nunca o honesto falso
			if ("nome".equals(pedaco) || "nome completo".equals(pedaco)) {
				String nome = ControleSessao.get().getNomeUsuario();
				if (nome != null && !nome.isBlank())
					return "Seu nome e " + nome + "!";
			}
			Registro.linha("TRIAGEM   : miss usuario chave=" + pedaco);
			return "Voce ainda nao me contou " + "seu" + " " + pedaco + "!";
		}
		return null;
	}

	public static boolean ehPergunta(String fala) {
		return fala.indexOf('?') >= 0
				|| ABRE_PERGUNTA.matcher(fala).find()
				|| QUALQUER_POSICAO.matcher(fala).find();
	}

	/** #52 - candidatos de chave na ordem de tentativa ("jogo favorito", "jogo"...). */
	private static List<String> candidatosDe(String base, String sufixo) {
		List<String> fora = new java.util.ArrayList<>();
		String b = base.toLowerCase(java.util.Locale.ROOT);
		if (b.endsWith("es") && b.length() > 4)
			fora.add(b.substring(0, b.length() - 2));
		if (b.endsWith("s") && b.length() > 3)
			fora.add(b.substring(0, b.length() - 1));
		fora.add(b);
		List<String> completos = new java.util.ArrayList<>();
		for (String c : fora) {
			if (sufixo != null)
				completos.add(c + " " + sufixo.toLowerCase(java.util.Locale.ROOT));
			completos.add(c);
		}
		return completos;
	}

	public static String lerResposta(String falaUsuario) {

		Intencao resposta;

		if (intencoesCache == null)
			carregaCache();

		resposta = buscaResposta(falaUsuario);

		return resposta != null ? comandosResposta(resposta) : null;
	}

	private static String escolheMultiRespostas(List<String> respostas) {

		if (respostas.isEmpty())
			return null;

		int qtdRespostas = respostas.size();

		int escolha = semeador.applyAsInt(qtdRespostas);

		return respostas.get(escolha);

	}

	public static void carregaCache() {

		try {
			if (objectMapper == null)
				objectMapper = new ObjectMapper();

			FileInputStream fileInputStream = new FileInputStream(Caminhos.recurso("intencoes.json").toFile());
			InputStreamReader reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);

			intencoesCache = objectMapper.readValue(reader, Intencoes.class);

		} catch (Exception e) {
			e.printStackTrace();
		}

		stopwords = new HashSet<>();
		try {
			FileInputStream fileInputStream = new FileInputStream(Caminhos.recurso("stopwords.txt").toFile());
			BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8));

			String linha;
			while ((linha = reader.readLine()) != null) {
				String t = linha.trim();
				if (t.isEmpty() || t.startsWith("#"))
					continue;

				stopwords.add(Normalizador.stem(Normalizador.texto(t)));
			}
		} catch (Exception e) {
			stopwords = null;   // sem arquivo: sem filtro, sem explodir
		}

		// #49 - tokens comuns (issue #49): mesmas regras de leitura das stopwords
		tokensComuns = new HashSet<>();
		try {
			FileInputStream fileInputStream = new FileInputStream(Caminhos.recurso("tokens_comuns.txt").toFile());
			BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8));

			String linha;
			while ((linha = reader.readLine()) != null) {
				String t = linha.trim();
				if (t.isEmpty() || t.startsWith("#"))
					continue;

				tokensComuns.add(Normalizador.stem(Normalizador.texto(t)));
			}
		} catch (Exception e) {
			tokensComuns = null;   // sem arquivo: sem filtro, sem explodir
		}

	// #41 - genericos stemmados no MESMO pipeline dos tokens da frase.
	// #42 - o flatten vira o set de "e generico" (igual antes) e, junto, o
	// mapa token->grupo pro desempate casar variante com variante
		tokensGenericos = new HashSet<>();
		gruposGenericos = new HashMap<>();
		for (List<String> grupo : TOKENS_GENERICOS_GRUPOS) {
			Set<String> stemmado = new HashSet<>();
			for (String g : grupo)
				stemmado.add(Normalizador.stem(Normalizador.texto(g)));
			tokensGenericos.addAll(stemmado);
			for (String g : stemmado)
				gruposGenericos.put(g, stemmado);
		}

	}

	/** #41 - a frase, sem stopwords, so tem token generico (nenhuma palavra de
	 * assunto)? Frase com palavra especifica nunca cai no desempate. */
	private static boolean fraseSoGenerica(Set<String> palavrasEntrada) {
		return !palavrasEntrada.isEmpty() && tokensGenericos.containsAll(palavrasEntrada);
	}

	/** #41 - devolve a intencao do topico atual quando a frase e so generica e o
	 * topico tem algum exemplo que casa (score > 0); senao null (nao mexe). */
	private static Intencao desempatePorContexto(Intencoes raiz, String entradaNormalizada) {
		return desempatePorContexto(raiz, entradaNormalizada, ControleSessao.get().getTopicoAtual());
	}

	/** #41 - mesma regra, mas com o topico passado explicitamente: o chamador
	 * guarda o topico ANTERIOR a classificacao (que seta topico como efeito
	 * colateral) e consulta o contexto sem risco de o vencedor ruidoso trocar
	 * o assunto no meio do caminho. */
	private static Intencao desempatePorContexto(Intencoes raiz, String entradaNormalizada, String topico) {
		if (topico == null || topico.isBlank())
			return null;

		Set<String> palavrasEntrada = tokensDe(entradaNormalizada);
		if (!fraseSoGenerica(palavrasEntrada))
			return null;

		Intencao candidata = raiz.getIntencoes().stream()
				.filter(i -> topico.equalsIgnoreCase(i.getIntento()))
				.findFirst().orElse(null);
		if (candidata == null)
			return null;

		garanteCacheIdf(raiz);
		for (Exemplo frase : candidata.getExemplos()) {
			Set<String> palavraFrase = cacheTokensExemplos.get(frase);
			if (palavraFrase == null)
				continue;
			boolean casa = palavrasEntrada.stream()
					.anyMatch(e -> palavraFrase.stream()
							.anyMatch(f -> ControleSinonimo.mesmaPalavra(e, f)
									|| mesmoGrupoGenerico(e, f)));
			if (casa)
				return candidata;
		}
		return null;
	}

	// #42 - no desempate, variante do mesmo grupo casa com variante ("seu"~
	// "sua", "preferido"~"favorito"). SO no desempate: o classificador continua
	// so com mesmaPalavra, porque um grupo largo ali desloca os pesos e amarra
	// os 14 casos congelados do avaliador.
	private static boolean mesmoGrupoGenerico(String a, String b) {
		Set<String> grupo = gruposGenericos.get(a);
		return grupo != null && grupo.contains(b);
	}

	private static Set<String> tokensDe(String frase){
		return tokensDe(frase, false);
	}

	// #39/#40 - piso: quando TODAS as palavras sao stopword, sobram os radicais
	// de conteudo (ex.: "quem" de "quem e voce"). Sem isso a frase vira conjunto
	// vazio e o match morre. USADO TAMBEM pros exemplos (L228): a entrada e o
	// exemplo tem que ser tokenizados IGUAIS, senao "quem" some dos exemplos,
	// a intersecao zera e o identidade nunca vence.
	private static Set<String> tokensDe(String frase, boolean comFallback){
		Set<String> principais = new java.util.LinkedHashSet<>();
		Set<String> deConteudo = new java.util.LinkedHashSet<>();
		for (String bruta : Normalizador.texto(frase).split(" ")) {
			if (bruta.isEmpty())
				continue;
			String radical = Normalizador.stem(bruta);
			boolean ehStop = stopwords != null && stopwords.contains(radical);
			if (!ehStop) {
				principais.add(radical);
				deConteudo.add(radical);
			}
			else if (comFallback && Normalizador.ehRadicalDeConteudo(radical)) {
				deConteudo.add(radical);   // guarda, so usa se zerar tudo
			}
		}
		return principais.isEmpty() && comFallback ? deConteudo : principais;
	}

	/** #26 - cache por base: mesma Intencoes devolve o mesmo mapa (imutavel). */
	public static Map<String, Double> pesosIdf(Intencoes raiz){
		garanteCacheIdf(raiz);
		return cachePesosIdf;
	}

	private static void garanteCacheIdf(Intencoes raiz){
		if (raiz == cacheRaizIdf && cachePesosIdf != null)
			return;

		List<Exemplo> todos = new ArrayList<>();
		for(Intencao i : raiz.getIntencoes())
			todos.addAll(i.getExemplos());

		cacheTokensExemplos = new HashMap<>();
		for(Exemplo e : todos)
			cacheTokensExemplos.put(e, tokensDe(e.getTexto(), true));

		int total = todos.size();
		cacheTotalExemplos = total;

		Map<String, Double> pesos = new HashMap<>();
		if(total > 0){
			for(Exemplo e: todos){
				for(String palavra: cacheTokensExemplos.get(e)){
					pesos.merge(palavra, 1.0, Double::sum);
				}
			}
			pesos.replaceAll((palavra, df) ->
					Math.log(total / Math.max(df, 1.0)));
		}

		cacheRaizIdf = raiz;
		cachePesosIdf = Collections.unmodifiableMap(pesos);
	}

	/** #26 - base mutada em runtime (ensino): proxima chamada recalcula. */
	public static void invalidaCacheIdf(){
		cacheRaizIdf = null;
		cachePesosIdf = null;
		cacheTokensExemplos = null;
	}


	public static String correcaoFrases(String frase){
		double porcentagem = 0;
		String palavras;

		Map<String, Double> bufferCalc = new HashMap<>();

		for(Intencao intencao: intencoesCache.getIntencoes()) {
			for (int i = 0; i < intencao.getExemplos().size(); i++) {
				palavras = intencao.getExemplos().get(i).getTexto();

				//porcentagem = Calculos.calculoPorcentagemGeral(frase, palavras);
				porcentagem = Calculos.calculoPorcentagemFrase(frase, palavras, null);
				if (porcentagem == 100) {
					System.out.println(porcentagem + " / " + palavras);
					return palavras;
				} else if (porcentagem >= 60) {
					System.out.println(porcentagem + " / " + palavras);
					bufferCalc.put(palavras, porcentagem);
				}

			}
		}

		bufferCalc = organizaBufferPalavras(bufferCalc, frase);

		List<Map.Entry<String, Double>> entryList = new ArrayList<>(bufferCalc.entrySet());

		Collections.sort(entryList, new Comparator<Map.Entry<String, Double>>() {
			public int compare(Map.Entry<String, Double> entry1, Map.Entry<String, Double> entry2) {
				return Double.compare(entry2.getValue(), entry1.getValue());
			}
		});

		if(!entryList.isEmpty()) {
			String melhorPalavra = entryList.get(0).getKey();

			Double melhorPorcentagem = entryList.get(0).getValue();

			//precissão de 67% é o minimo para encontrar palavras basicas
			if (melhorPorcentagem >= 67)
				return melhorPalavra;
		}

		return frase;
	}

	private static Map<String, Double> organizaBufferPalavras(Map<String, Double> bufferCalc, String frase){

		Map<String, Double> bufferCalculado = new HashMap<>();

		bufferCalc.forEach( (bufferPalavra, bufferPorcentagem) ->{
			int acertos = 0;
			int qtdFrase =  frase.split(" ").length;

			for(var fraseSeparada: frase.split(" ")) {
				for (var bufferSeparado : bufferPalavra.split(" ")) {
					if(fraseSeparada.equalsIgnoreCase(bufferSeparado)) {
						acertos++;
						break;
					}
				}
			}

			Double percAux = Calculos.calculoPorcentagem(acertos, qtdFrase);

			bufferPorcentagem = bufferPorcentagem*(percAux/100);

			bufferCalculado.put(bufferPalavra, bufferPorcentagem);
		});

		return bufferCalculado;
	}

	public static String preencheSlots(String texto) {
		if (texto == null)
			return null;
		if (!texto.contains("{"))
			return texto;

		String nome = ControleSessao.get().getNomeUsuario();
		if (nome == null || nome.isBlank())
			nome = "amigo";

		String topico = ControleSessao.get().getTopicoAtual();
		if (topico == null || topico.isBlank())
			topico = "tudo um pouco";

		String periodo = LocalTime.now().getHour() < 12 ? "bom dia" : "boa tarde";

		String saida = texto
				.replace("{nome}", nome)
				.replace("{topico}", topico)
				.replace("{hora_do_dia}", periodo);

		Matcher m = Pattern.compile("\\{fato:([^}]+)\\}").matcher(saida);
		StringBuffer buf = new StringBuffer();
		while (m.find()) {
			String valor = ControleSessao.get().getFatos().get(m.group(1).trim());
			m.appendReplacement(buf, Matcher.quoteReplacement(valor != null ? valor : m.group()));
		}
		m.appendTail(buf);
		return buf.toString();
	}

	public static boolean fatosCompletos(String texto) {
		if (texto == null || !texto.contains("{fato:"))
			return true;
		Matcher m = Pattern.compile("\\{fato:([^}]+)\\}").matcher(texto);
		while (m.find())
			if (ControleSessao.get().getFatos().get(m.group(1).trim()) == null)
				return false;
		return true;
	}

	/**
	 * §7.1 - so responde da faixa de tom do humor da sessao (0-3 seca,
	 * 4-6 neutra, 7-10 animada), usando o humorMin/humorMax do
	 * Apendice A. Nenhuma resposta na faixa -> devolve o pool inteiro
	 * (nunca fica sem resposta).
	 */
	private static List<String> porFaixaDeHumor(Intencao intencao, List<String> pool) {
		int humor = ControleSessao.get().getHumor();
		List<String> faixa = new ArrayList<>();
		for (int i = 0; i < pool.size(); i++)
			if (intencao.getRespostas().get(i).getHumorMin() <= humor
					&& humor <= intencao.getRespostas().get(i).getHumorMax())
				faixa.add(pool.get(i));
		return faixa.isEmpty() ? pool : faixa;
	}

	/** #35 - tira as ultimas N frases entregues deste intento; se esvaziar,
	 * devolve a lista original (nunca fica sem resposta). */
	private static List<String> semUltimas(String intento, List<String> candidatas) {
		if (intento == null)
			return candidatas;
		List<String> ultimas = ultimasRespostas.get(intento);
		if (ultimas == null || ultimas.isEmpty())
			return candidatas;
		List<String> livres = new ArrayList<>(candidatas);
		livres.removeAll(ultimas);
		return livres.isEmpty() ? candidatas : livres;
	}

	/** #35 - anota a frase entregue (template, antes dos slots) na fila do intento. */
	private static void anotaUltima(String intento, String frase) {
		if (intento == null || frase == null)
			return;
		List<String> ultimas = ultimasRespostas.computeIfAbsent(intento, k -> new ArrayList<>());
		ultimas.add(frase);
		while (ultimas.size() > ULTIMAS_GUARDADAS)
			ultimas.remove(0);
	}

	// #35 - package-private: spec chama direto (fixture isolada)
	static String comandosResposta(Intencao resposta){

		// #37 (C3 piloto): continuacao_topico com conhecimento cadastrado fala
		// a substancia do topico (resumo do dicionario) em vez do bank generico;
		// sem entrada, cai no bank atual (fallback nunca piora)
		if ("continuacao_topico".equals(resposta.getIntento())) {
			// #39 - continuacao logo apos aposta segue o candidato proposto, nao
			// o topico antigo; confirmou por continuacao e vira topico
			String pendente = ControleSessao.consomeApostaPendente();
			String topico = pendente != null
					? pendente
					: ControleSessao.get().getTopicoAtual();
			if (pendente != null)
				ControleSessao.topico(pendente);

			// #54 - "explica melhor" fala o TEXTO do dicionario; "conta mais"
			// segue no resumo. Sem texto, o resumo ainda da a substancia.
			String via = "texto";
			String substancia = ControleSessao.explicacaoPedida()
					? ControleConhecimento.texto(topico) : null;
			if (substancia == null) {
				substancia = ControleConhecimento.resumo(topico);
				via = "resumo";
			}
			if (substancia != null) {
				Registro.linha("CONHECE  : termo=" + topico + " (" + via + ")");
				String falado = preencheSlots(substancia);
				Registro.linha("RESPOSTA  : " + falado);
				return falado;
			}
		}

		if(resposta==null)
			return null;

		List<String> pool = resposta.getRespostas().stream().map(RespostaIntencao::getTexto).toList();
		pool = porFaixaDeHumor(resposta, pool);
		List<String> prontas = pool.stream().filter(ControleResposta::fatosCompletos).toList();

		if (prontas.isEmpty() && pool.stream().anyMatch(t -> t.contains("{fato:")))
			return respostaFallback();

		// #35 (C1): as ultimas 2 frases do intento saem do sorteio; se a
		// lista esvaziar, volta pro sorteador normal (nunca fica sem resposta).
		List<String> disponiveis = prontas.isEmpty() ? pool : prontas;
		String respostaEscolhida = escolheMultiRespostas(semUltimas(resposta.getIntento(), disponiveis));
		anotaUltima(resposta.getIntento(), respostaEscolhida);

		// #29 - template escolhido antes dos slots (o KAIRA : ja mostra o final)
		Registro.linha("RESPOSTA  : " + respostaEscolhida);

		// #27 hotfix - intencao sem "acoes" no JSON derrubava aqui com NPE ao vivo
		List<String> acoes = resposta.getAcoes() == null ? List.of() : resposta.getAcoes();

		if(acoes.contains("MOSTRAR_HUMOR")){
			respostaEscolhida= respostaEscolhida + "\nnivel de humor em " + ControleSessao.get().getHumor() + " de 10";
		}else if(acoes.contains("MOSTRAR_VERSAO")){
			respostaEscolhida= respostaEscolhida + "\nnumero de "+ ControleKaira.buscaDadosGerais().getVersao();
		}else if(acoes.contains("DESLIGAR")){
			System.exit(0);
		}

		return preencheSlots(respostaEscolhida);
	}

	public static void chamadaFrase(String frase, String resposta) {

		if (intencoesCache == null)
			carregaCache();

		if (objectMapper == null)
			objectMapper = new ObjectMapper();

		ensinaEm(intencoesCache, frase, resposta);

		try (FileWriter fileWriter = new FileWriter(Caminhos.recurso("intencoes.json").toFile(), StandardCharsets.UTF_8)) {

			fileWriter.write(objectMapper.writeValueAsString(intencoesCache));
		} catch (IOException e) {
			e.printStackTrace();
		}

		ControlePalavra.gravaPalavra(frase);

	}

	public static String fraseExata(String entradaNormalizada) {
		if (intencoesCache == null) carregaCache();

		Intencao intencao = intencoesCache.getIntencoes().stream()
				.filter(i -> i.getExemplos().stream().anyMatch(e -> Normalizador.texto(e.getTexto()).equalsIgnoreCase(entradaNormalizada)))
				.findFirst().orElse(null);

		if(intencao!=null){
			String resposta = intencao.getExemplos().stream()
					.map(Exemplo::getTexto)
					.filter(texto -> Normalizador.texto(texto).equalsIgnoreCase(entradaNormalizada))
					.findFirst().orElse(null);

			if(resposta!=null) {
				logaFraseExata(intencao); // #40 - frase exata tambem loga
				return resposta;
			}
		}

		return null;
	}

	public static String resolver(String entradaNormalizada){
		String exata = fraseExata(entradaNormalizada);
		if(exata != null) return exata;

		var resposta = melhorIntencao(entradaNormalizada);

		if(resposta!=null) {
			return resposta.keySet().stream().findFirst().orElse(null);
		}

		return null;

	}

	public static Intencao buscaResposta(String entradaNormalizada){
		if(intencoesCache==null)
			carregaCache();
		return buscaResposta(intencoesCache, entradaNormalizada);
	}

	public static Intencao buscaResposta(Intencoes raiz, String entradaNormalizada){

		// #54 - "explica/detalha/aprofunda" marcam o turno: o dicionario
		// responde o TEXTO em vez do resumo (flag vive so o turno, #28)
		if (entradaNormalizada != null
				&& entradaNormalizada.matches("(?i).*(?:explica\\p{L}*|detalh\\p{L}*|aprofund\\p{L}*|mais fundo).*"))
			ControleSessao.pedeExplicacao();

		if(raiz==null) {
			if(intencoesCache==null)
				carregaCache();
			raiz = intencoesCache;
		}

		// #41 - o topico ANTERIOR a classificacao: melhorIntencaoDetalhado seta
		// topico como efeito colateral em qualquer match real (>= LIMIAR), entao
		// tem que guardar antes, senao o vencedor de token generico (ex. gostos_ia)
		// sequestra o assunto antes de o contexto ser consultado
		String topicoAnterior = ControleSessao.get().getTopicoAtual();

		// #41 - frase so com palavras genericas (nenhuma palavra de assunto)?
		// Nesse caso o topico ANTERIOR decide antes de qualquer match ruidoso
		boolean genericaSemDesempate = false;
		if (fraseSoGenerica(tokensDe(entradaNormalizada))) {
			Intencao doTopico = desempatePorContexto(raiz, entradaNormalizada, topicoAnterior);
			if (doTopico != null) {
				// entrega o topico escolhido pelo contexto; o classificador
				// normal nao roda nesse ramo entao nao tem ruido de score
				if (!"continuacao_topico".equals(doTopico.getIntento()))
					ControleSessao.topico(doTopico.getIntento());
				logaDesempate(doTopico); // #43 - rotulo proprio, nao "frase exata"
				return doTopico;
			}
			// §5.2 (borda): a frase e generica E o topico nao tem nenhum
			// exemplo de favorito/preferido. O desempate devolveu null e o
			// classificador vai entrar - mas ele NAO PODE herdar o topico:
			// a frase nao tem palavra de assunto, entao o match dele e ruido
			// de token generico. Sem isto, "historia"/"clima" viravam
			// "gostos_ia" e o tema do usuario sumia da conversa.
			genericaSemDesempate = true;
		}

		Intencao resposta = raiz.getIntencoes().stream().filter(intencao -> intencao.getExemplos().stream()
				.anyMatch(it -> Normalizador.texto(it.getTexto()).equalsIgnoreCase(entradaNormalizada))).findFirst().orElse(null);

		if(resposta!=null) {
			logaFraseExata(resposta); // #40 - frase exata tambem loga
			ancoraTopico(resposta);   // #41 - frase exata e match de verdade
			return resposta;
		}

		var aux = melhorIntencao(raiz, entradaNormalizada);

		if(aux!=null){
			Iterator<Intencao> iterator = aux.values().iterator();
			if (iterator.hasNext()) {
				resposta = iterator.next();
			}
		}

		// §5.2 - generica sem desempate nao pode ancorar topico novo: o match
		// do classificador ali em cima e ruido de token generico, nao assunto.
		// So restaura quando HAVIA topico antes (nada a preservar = deixa o
		// comportamento de sempre)
		if (genericaSemDesempate && topicoAnterior != null)
			ControleSessao.get().setTopicoAtual(topicoAnterior);

		return resposta;
	}

	public static Map<String, Intencao> melhorIntencao(String entradaNormalizada){
		if(intencoesCache == null)
			carregaCache();

		return melhorIntencao(intencoesCache, entradaNormalizada);

	}

	public static Map<String, Intencao> melhorIntencao(Intencoes raiz, String entradaNormalizada){

		MatchIntencao match = melhorIntencaoDetalhado(raiz, entradaNormalizada);

		if(match == null || match.getScore() < LIMIAR)
			return null;

		Map<String, Intencao> retorno = new HashMap<>();

		retorno.put(match.getFrase(), match.getIntencao());

		return retorno;
	}

	// #29 - detalhe da ultima classificacao pro log do turno; sem I/O
	// aqui (o classificador roda 2-3x por turno e em massa nos testes)
	private static String ultimoMatchLog;

	public static String getUltimoMatchLog() {
		return ultimoMatchLog;
	}

	// #40 - frase exata casou: o log do turno precisa do match FINAL, senao vaza
	// o 0.00 morto do primeiro passo (caso "conte mais" corrigido ao vivo)
	private static void logaFraseExata(Intencao intencao) {
		ultimoMatchLog = "intento=" + intencao.getIntento()
				+ " score=1.00 (frase exata)";
	}

	// #43 - o desempate nao e frase exata: o log tem que dizer o caminho que
	// resolveu. Ao vivo (17/10 16:12:26) "qual sua preferida?" casou pelo
	// CONTEXTO e o log dizia "(frase exata)" - mentira de log, o mesmo peco que
	// o #40 matou.
	private static void logaDesempate(Intencao intencao) {
		ultimoMatchLog = "intento=" + intencao.getIntento()
				+ " score=1.00 (desempate por topico)";
	}

	// #38/#39/#41 - a UNICA regra de ancoragem do topico. Ela tava duplicada no
	// classificador e na frase exata, e a frase exata ESQUECEU de ancorar (ao
	// vivo 17/10: "voce gosta de filmes?" casou exato sem gravar topico, e
	// "qual seu preferido?" virou gostos_ia com 1.00 - sem contexto pra brigar).
	// Regra: so match de verdade de assunto novo vira topico; continuacao_topico
	// nao e assunto (segura o pool); match do proprio candidato da aposta vira
	// topico mas NAO consome a pendencia (confirmacao explicita fica pendente)
	private static void ancoraTopico(Intencao intencao) {
		if (!"continuacao_topico".equals(intencao.getIntento())) {
			if (!intencao.getIntento().equals(ControleSessao.getApostaPendente()))
				ControleSessao.consomeApostaPendente();
			ControleSessao.topico(intencao.getIntento());
		}
		else
			ControleSessao.pedeContinuacao(); // #28 - o turno e dela: segura o pool
	}

	// #49 - o gate e' regra de RESPOSTA, nao de medicao. Quem responde
	// (buscaResposta) nao pode entregar match de 1 token; mas o porteiro do
	// ensino (scoreDo, piso 0.1) e o ensinaEm (0.6) precisam ver o match
	// fraco honesto - sem o gate "zork blorp quizzle" volta 0.192 e e'
	// absorvido; com o gate volta 0.0 e o ensino morre (medido: 4 testes).
	public static MatchIntencao melhorIntencaoDetalhado(Intencoes raiz, String entradaNormalizada) {
		return melhorIntencaoDetalhado(raiz, entradaNormalizada, true);
	}

	public static MatchIntencao melhorIntencaoDetalhado(Intencoes raiz, String entradaNormalizada,
														boolean comGate) {
		ultimoMatchLog = null;
		Set<String> palavrasEntrada = tokensDe(entradaNormalizada, true);

		if (tokensComuns != null)
			palavrasEntrada.removeIf(tokensComuns::contains);

		if(palavrasEntrada.isEmpty())
			return null;

		// #26 - IDF e tokens vem do cache; recalcula so na 1a chamada da base
		garanteCacheIdf(raiz);
		Map<String, Double> pesos = cachePesosIdf;

		double n = cacheTotalExemplos;
		double pesoAusente = Math.log(Math.max(n, 1.0));

		double denominador = 0;
		for (String p : palavrasEntrada)
			denominador += pesos.getOrDefault(p, pesoAusente);

		Intencao melhorIntencao = null;
		String melhorFraseIntencao = null;
		// #26 - tokens do melhor exemplo ate agora: o cache e por objeto
		// Exemplo, e o desempate precisa dos tokens do texto vencedor
		Set<String> melhorTokensFrase = null;
		double melhorScore = -1;

		for(Intencao intencao: raiz.getIntencoes()){

			for(Exemplo frase: intencao.getExemplos()) {
				Set<String> palavraFrase = cacheTokensExemplos.get(frase);
				long intersecao = palavrasEntrada.stream()
						.filter(e -> palavraFrase.stream().anyMatch(f -> ControleSinonimo.mesmaPalavra(e,f))).count();

				double numerador = 0;
				for (String e : palavrasEntrada) {
					if (palavraFrase.contains(e)) {
						// direto (lados dois stemmados pelo mesmo pipeline): peso cheio
						numerador += pesos.getOrDefault(e, pesoAusente);
					} else if (palavraFrase.stream().anyMatch(f -> ControleSinonimo.mesmaPalavra(e, f))) {
						// #49 - flexao (genero/numero) e' o MESMO radical: peso
						// cheio. Sinonimo de verdade fica com 0.7. Sem isso a
						// correcao de typo congela em 0.88 ("favorita"~"favorito")
						// e o match deixa de ser o 1.00 que o fluxo exige.
						if (palavraFrase.stream().anyMatch(f -> ControleSinonimo.eFlexao(e, f)))
							numerador += pesos.getOrDefault(e, pesoAusente);
						else
							numerador += 0.7 * pesos.getOrDefault(e, pesoAusente);
					}
				}

				double score = denominador == 0
						? (double) intersecao / palavrasEntrada.size()
						: numerador / denominador;

				// #49 - gate: parcial de frase COMPLEXA exige >= 2 tokens cobertos;
				// frase curta (<= 2 tokens depois de comuns/stopwords) tem pouco
				// conteudo - 1 coberto ja' e' a maior parte dela. Sem isso,
				// "qual sua idade?" e "voce joga videogame?" viram fallback.
				if (comGate && score < 1.0 && intersecao < 2 && palavrasEntrada.size() > 2)
					continue;

				if (score > melhorScore) {
					melhorScore = score;
					melhorFraseIntencao = frase.getTexto();
					melhorTokensFrase = palavraFrase;
					melhorIntencao = intencao;
				} else if (score == melhorScore) {
					long restoAnterior = melhorTokensFrase.stream()
							.filter(f -> !palavrasEntrada.stream().anyMatch(e -> ControleSinonimo.mesmaPalavra(e,f))).count();
					long resto = palavraFrase.stream()
							.filter(f -> !palavrasEntrada.stream().anyMatch(e -> ControleSinonimo.mesmaPalavra(e,f))).count();

					if (resto < restoAnterior) {
						melhorScore = score;
						melhorFraseIntencao = frase.getTexto();
						melhorTokensFrase = palavraFrase;
						melhorIntencao = intencao;
					} else if (resto == restoAnterior
							&& vencePorFrame(entradaNormalizada, melhorFraseIntencao, frase.getTexto())) {
						melhorScore = score;
						melhorFraseIntencao = frase.getTexto();
						melhorTokensFrase = palavraFrase;
						melhorIntencao = intencao;
					}

				}
			}
		}

		if (melhorIntencao == null)
			return null;

		// #38 - so um match de verdade vira topico: score < LIMIAR (frase sem
		// match nenhum ou aposta ainda nao confirmada) nao pode trocar o assunto
		if (melhorScore >= LIMIAR) {
			ancoraTopico(melhorIntencao); // regra unica no helper (ver L604)
		}
		// #29 - linha de detalhe pro log (decifrar imprime 1x por turno)
		// #40 - score 0.00 nao e match fraco: e ausencia de match; o log tem que
		// dizer isso em vez de "intento=X score=0.00" (que enganava o diagnostico)
		String rotulo = melhorScore <= 0.0
				? "sem match (melhor=" + melhorIntencao.getIntento() + ")"
				: "intento=" + melhorIntencao.getIntento();
		ultimoMatchLog = rotulo
				+ String.format(Locale.ROOT, " score=%.2f", melhorScore)
				+ " frase=\"" + melhorFraseIntencao + "\""
				+ " tokens=" + palavrasEntrada;
		return new MatchIntencao(melhorFraseIntencao, melhorIntencao, melhorScore);
	}

	private static boolean vencePorFrame(String entrada, String atual, String candidata) {
		String frameEntrada = ControlePreAnalise.preAnalise(entrada).getFrame();
		if (frameEntrada == null)
			return false;
		if (frameEntrada.equals(ControlePreAnalise.preAnalise(atual).getFrame()))
			return false;
		return frameEntrada.equals(ControlePreAnalise.preAnalise(candidata).getFrame());
	}

	public static String respostaAposta(String entradaNormalizada) {
		if (intencoesCache == null)
			carregaCache();

		return respostaAposta(intencoesCache, entradaNormalizada);
	}

	public static String respostaAposta(Intencoes raiz, String entradaNormalizada){
		MatchIntencao match = melhorIntencaoDetalhado(raiz, entradaNormalizada);

		if(match == null || match.getScore() < LIMIAR_MEDIA || match.getScore() >= LIMIAR)
			return null;

		String aposta = comandosResposta(match.getIntencao());

		return aposta == null ? null : aposta + " Foi isso que você quis dizer?";
	}

	// #39 - confirmacao da aposta: entrega a resposta real do candidato e ancora
	// o topico; null quando nao e confirmacao (o fluxo normal segue)
	static String entregaApostaPendente(String falaNormalizada) {
		String pendente = ControleSessao.getApostaPendente();
		if (pendente == null || !ControleSessao.ehConfirmacao(falaNormalizada))
			return null;

		Intencao alvo = getIntencoes().stream()
				.filter(i -> pendente.equals(i.getIntento()))
				.findFirst().orElse(null);

		ControleSessao.confirmaApostaPendente(); // consumiu + ancora o candidato
		if (alvo == null)
			return null;

		String entrega = comandosResposta(alvo);
		if (entrega == null)
			return null;
		Registro.linha("CONHECE  : intento=" + pendente);
		Registro.linha("RESPOSTA  : " + entrega);
		return entrega;
	}

	public static String respostaFallback() {
		List<String> opcoes = new ArrayList<>(FRASES_FALLBACK);
		if (ultimoFallback != null)
			opcoes.remove(ultimoFallback);

		if (opcoes.isEmpty())
			opcoes = new ArrayList<>(FRASES_FALLBACK);

		String escolhida = opcoes.get(new Random().nextInt(opcoes.size()));
		ultimoFallback = escolhida;
		return escolhida;
	}

	public static String respostaFallback(String slot) {
		if (slot == null || slot.isBlank())
			return respostaFallback();

		List<String> opcoes = new ArrayList<>();
		for (String template : FRASES_FALLBACK_SLOT)
			opcoes.add(template.replace("{slot}", slot));

		if (ultimoFallback != null)
			opcoes.remove(ultimoFallback);

		String escolhida = opcoes.get(new Random().nextInt(opcoes.size()));
		ultimoFallback = escolhida;
		return escolhida;
	}

	public static List<Intencao> getIntencoes(){

		if(intencoesCache==null)
			carregaCache();

		return intencoesCache.getIntencoes();

	}

	// #49 - o porteiro do aprendizado organico: o MESMO score do #33,
	// devolvido pro FormulaResposta decidir absorver (>= piso) ou reter.
	// Precisa morar aqui porque o cache de intencoes e privado.
	public static double scoreDo(String fraseNormalizada) {

		if (intencoesCache == null)
			carregaCache();

		MatchIntencao encaixe = melhorIntencaoDetalhado(intencoesCache, fraseNormalizada, false);
		return encaixe == null ? 0.0 : encaixe.getScore();
	}

	/** #41 - absorve o par (exemplo = a resposta do usuario, resposta = o
	 *  followup dela) no intento informado; sem intento, o gate do #33 decide. */
	public static void absorveNoIntento(String intento, String frase, String resposta) {
		if (intencoesCache == null)
			carregaCache();
		Intencao alvo = intencoesCache.getIntencoes().stream()
				.filter(i -> i.getIntento().equalsIgnoreCase(intento == null ? "" : intento))
				.findFirst().orElse(null);
		if (alvo == null) {
			chamadaFrase(frase, resposta);
			return;
		}
		if (objectMapper == null)
			objectMapper = new ObjectMapper();
		Registro.linha("APRENDE   : intento=" + alvo.getIntento() + " (familia da pergunta)");
		alvo.getExemplos().add(new Exemplo(frase, "autor"));
		alvo.getRespostas().add(new RespostaIntencao(resposta, "autor", 0, 10));
		invalidaCacheIdf();
		try (FileWriter fileWriter = new FileWriter(
				Caminhos.recurso("intencoes.json").toFile(), StandardCharsets.UTF_8)) {
			fileWriter.write(objectMapper.writeValueAsString(intencoesCache));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void ensinaEm(Intencoes raiz, String frase, String resposta){

		// #33 - ensino turbinado: classifica ANTES de gravar; a regua e a
		// mesma LIMIAR (0.6) com que ela ja responde com confianca. A
		// classificacao roda antes do invalidaCacheIdf de proposito: com o
		// cache invalido na frente, a 2a chamada recalcula com a base ja
		// mutada (sem NPE de token, sem IDF desatualizado no ensino em cadeia)
		MatchIntencao encaixe = melhorIntencaoDetalhado(raiz, frase, false);
		Intencao intencao;

		if (encaixe != null && encaixe.getScore() >= LIMIAR) {
			// score acima do limiar: o par ensinado alimenta o intento certo
			intencao = encaixe.getIntencao();
			Registro.linha("APRENDE   : intento=" + intencao.getIntento()
					+ String.format(Locale.ROOT, " score=%.2f", encaixe.getScore()));
		} else {
			// duvida (ou nada): caixa de entrada como sempre
			intencao = raiz.getIntencoes().stream()
					.filter(i -> i.getIntento().equalsIgnoreCase("auto_aprendizado")).findFirst().orElse(null);
			Registro.linha("APRENDE   : auto_aprendizado"
					+ (encaixe == null ? "" : String.format(Locale.ROOT, " score=%.2f", encaixe.getScore())));
		}

		if(intencao==null) {
			intencao = new Intencao("auto_aprendizado", new ArrayList<>(), null, new ArrayList<>(),null,null);
			raiz.getIntencoes().add(intencao);
		}

		intencao.getExemplos().add(new Exemplo(frase, "autor"));
		intencao.getRespostas().add(new RespostaIntencao(resposta, "autor",0,10));
		invalidaCacheIdf(); // #26 - a base mudou: proxima classificacao recalcula
	}

	public static String CriarResposta(String resposta) {

		if(resposta==null)
			return null;

		List<String> respostaSeparada = List.of(resposta.split(" "));
		String novaFrase = "";

		for(String palavra: respostaSeparada){

			List<String> sinonimos = ControleSinonimo.buscaGrupoSinonimo(palavra).stream().toList();

			String sinonimo = sinonimos.isEmpty() ? null : sinonimos.get(new Random().nextInt(sinonimos.size()));

			if(!novaFrase.equalsIgnoreCase(""))
				novaFrase = novaFrase.concat(" ");

			if(sinonimo==null || (stopwords != null && stopwords.contains(Normalizador.stem(palavra))))
				novaFrase = novaFrase.concat(palavra);
			else
				novaFrase = novaFrase.concat(sinonimo);

		}

		System.out.println("nova resposta " + novaFrase + "/ resposta antiga "+ resposta);

		return novaFrase;
	}


}