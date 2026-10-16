package com.JCODE.controller;

import com.JCODE.domain.*;
import com.JCODE.util.Calculos;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Normalizador;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ControleResposta {

	static ObjectMapper objectMapper;

	static Intencoes intencoesCache;

	private static final double LIMIAR = 0.6;

	private static final double LIMIAR_MEDIA = 0.3;

	static Set<String> stopwords;

	private static final List<String> FRASES_FALLBACK = List.of(
			"Ainda não sei falar disso... me ensina? O que você quis dizer?",
			"Ainda tô aprendendo isso — me conta o que você quis dizer, e me ensina?",
			"Essa ainda não sei... me ensina? Quero aprender o que você quis dizer?");

	private static final List<String> FRASES_FALLBACK_SLOT = List.of(
			"Ainda não sei sobre {slot}... me ensina? O que você quis dizer?",
			"Ainda tô aprendendo sobre {slot} — me conta o que você quis dizer, e me ensina?",
			"Essa parte de {slot} ainda não sei... me ensina? Quero aprender o que você quis dizer?");

	private static String ultimoFallback = null;

	// #26 (briefing P7.2) - cache de IDF e de tokens por base: a base so
	// muda no carregaCache (instancia nova -> rebuild por identidade) ou
	// no ensinaEm (mutacao -> invalidaCacheIdf)
	private static Intencoes cacheRaizIdf = null;
	private static Map<String, Double> cachePesosIdf = null;
	private static Map<Exemplo, Set<String>> cacheTokensExemplos = null;
	private static double cacheTotalExemplos = 0;

	public static List<String> frasesFallback() {
		return FRASES_FALLBACK;
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

		int escolha = (int) (Math.random() * (qtdRespostas));

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

	}

	private static Set<String> tokensDe(String frase){
		return Arrays.stream(Normalizador.texto(frase).split(" "))
				.map(Normalizador::stem)
				.filter(it -> !it.isEmpty() && (stopwords == null || !stopwords.contains(it)))
				.collect(Collectors.toSet());
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
			cacheTokensExemplos.put(e, tokensDe(e.getTexto()));

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

	private static String comandosResposta(Intencao resposta){

		if(resposta==null)
			return null;

		List<String> pool = resposta.getRespostas().stream().map(RespostaIntencao::getTexto).toList();
		pool = porFaixaDeHumor(resposta, pool);
		List<String> prontas = pool.stream().filter(ControleResposta::fatosCompletos).toList();

		if (prontas.isEmpty() && pool.stream().anyMatch(t -> t.contains("{fato:")))
			return respostaFallback();

		String respostaEscolhida = escolheMultiRespostas(prontas.isEmpty() ? pool : prontas);

		if(resposta.getAcoes().contains("MOSTRAR_HUMOR")){
			respostaEscolhida= respostaEscolhida + "\nnivel de humor em " + ControleSessao.get().getHumor() + " de 10";
		}else if(resposta.getAcoes().contains("MOSTRAR_VERSAO")){
			respostaEscolhida= respostaEscolhida + "\nnumero de "+ ControleKaira.buscaDadosGerais().getVersao();
		}else if(resposta.getAcoes().contains("DESLIGAR")){
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
		return intencoesCache.getIntencoes().stream()
				.flatMap(i -> i.getExemplos().stream())
				.map(Exemplo::getTexto)
				.filter(texto -> Normalizador.texto(texto).equalsIgnoreCase(entradaNormalizada))
				.findFirst().orElse(null);
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

		Intencao resposta = null;

		resposta = intencoesCache.getIntencoes().stream().filter(intencao -> intencao.getExemplos().stream()
				.anyMatch(it -> Normalizador.texto(it.getTexto()).equalsIgnoreCase(entradaNormalizada))).findFirst().orElse(null);

		if(resposta==null) {

			var aux = melhorIntencao(entradaNormalizada);

			if(aux!=null){
				Iterator<Intencao> iterator = aux.values().iterator();
				if (iterator.hasNext()) {
					resposta = iterator.next();
				}

			}

		}

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

	public static MatchIntencao melhorIntencaoDetalhado(Intencoes raiz, String entradaNormalizada){
		Set<String> palavrasEntrada = tokensDe(entradaNormalizada);

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
				for (String e : palavrasEntrada)
					if (palavraFrase.stream().anyMatch(f -> ControleSinonimo.mesmaPalavra(e, f)))
						numerador += pesos.getOrDefault(e, pesoAusente);

				double score = denominador == 0
						? (double) intersecao / palavrasEntrada.size()
						: numerador / denominador;

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

		ControleSessao.topico(melhorIntencao.getIntento());
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

	public static void ensinaEm(Intencoes raiz, String frase, String resposta){

		Intencao intencao = raiz.getIntencoes().stream()
				.filter(i -> i.getIntento().equalsIgnoreCase("auto_aprendizado")).findFirst().orElse(null);

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