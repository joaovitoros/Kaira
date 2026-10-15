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
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ControleResposta {

	static ObjectMapper objectMapper;

	static Intencoes intencoesCache;

	private static final double LIMIAR = 0.6;

	static Set<String> stopwords;

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

	public static Map<String, Double> pesosIdf(Intencoes raiz){

		List<Exemplo> todos = new ArrayList<>();
		for(Intencao i : raiz.getIntencoes())
			todos.addAll(i.getExemplos());

		int total = todos.size();
		Map<String, Double> pesos = new HashMap<>();

		if(total == 0)
			return pesos;

		for(Exemplo e: todos){
			for(String palavra: tokensDe(e.getTexto())){
				pesos.merge(palavra, 1.0, Double::sum);
			}
		}

		pesos.replaceAll((palavra, df) ->
				Math.log(total / Math.max(df, 1.0)));

		return pesos;
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

	private static String comandosResposta(Intencao resposta){

		if(resposta==null)
			return null;

		String respostaEscolhida = escolheMultiRespostas(resposta.getRespostas().stream().map(RespostaIntencao::getTexto).toList());

		if(resposta.getAcoes().contains("MOSTRAR_HUMOR")){
			respostaEscolhida= respostaEscolhida + "\nnivel de humor em...  de 10";
		}else if(resposta.getAcoes().contains("MOSTRAR_VERSAO")){
			respostaEscolhida= respostaEscolhida + "\nnumero de "+ ControleKaira.buscaDadosGerais().getVersao();
		}else if(resposta.getAcoes().contains("DESLIGAR")){
			System.exit(0);
		}

		return respostaEscolhida;
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
		Set<String> palavrasEntrada = tokensDe(entradaNormalizada);

		if(palavrasEntrada.isEmpty())
			return null;

		Map<String, Double> pesos = pesosIdf(raiz);

		double n = raiz.getIntencoes().stream().mapToInt(i -> i.getExemplos().size()).sum();
		double pesoAusente = Math.log(Math.max(n, 1.0));

		double denominador = 0;
		for (String p : palavrasEntrada)
			denominador += pesos.getOrDefault(p, pesoAusente);

		Intencao melhorIntencao = null;
		String melhorFraseIntencao = null;
		double melhorScore = 0;

		for(Intencao intencao: raiz.getIntencoes()){

			for(Exemplo frase: intencao.getExemplos()) {
				Set<String> palavraFrase = tokensDe(frase.getTexto());
				long intersecao = palavrasEntrada.stream()
						.filter(e -> palavraFrase.stream().anyMatch(f -> ControleSinonimo.mesmaPalavra(e,f))).count();

				double numerador = 0;
				for (String e : palavrasEntrada)
					if (palavraFrase.stream().anyMatch(f -> ControleSinonimo.mesmaPalavra(e, f)))
						numerador += pesos.getOrDefault(e, pesoAusente);

				double score = denominador == 0
						? (double) intersecao / palavrasEntrada.size()
						: numerador / denominador;
				if (score >= LIMIAR) {
					if (score > melhorScore) {
						melhorScore = score;
						melhorFraseIntencao = frase.getTexto();
						melhorIntencao = intencao;
					} else if (score == melhorScore) {
						long restoAnterior = tokensDe(melhorFraseIntencao).stream()
								.filter(f -> !palavrasEntrada.stream().anyMatch(e -> ControleSinonimo.mesmaPalavra(e,f))).count();
						long resto = palavraFrase.stream()
								.filter(f -> !palavrasEntrada.stream().anyMatch(e -> ControleSinonimo.mesmaPalavra(e,f))).count();

						if (resto < restoAnterior) {
							melhorScore = score;
							melhorFraseIntencao = frase.getTexto();
							melhorIntencao = intencao;
						}
					}
				}
			}
		}

		Map<String, Intencao> retorno = new HashMap<>();

		if(melhorScore >= LIMIAR)
			retorno.put(melhorFraseIntencao, melhorIntencao) ;

		return !retorno.isEmpty() ? retorno : null;
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