package com.JCODE.controller;

import com.JCODE.domain.Falas;
import com.JCODE.domain.Resposta;
import com.JCODE.util.Calculos;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Normalizador;
import com.fasterxml.jackson.databind.ObjectMapper;

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

	static Falas falasCache;

	private static final double LIMIAR = 0.6;

	public static String lerResposta(String falaUsuario) {

		Resposta resposta = new Resposta();

		if (falasCache == null)
			carregaCache();

		resposta = falasCache.getFalas().stream().filter(frase -> falaUsuario.equalsIgnoreCase(frase.getFrase()))
				.findFirst().orElse(new Resposta());

		return falaUsuario.equalsIgnoreCase(resposta.getFrase()) ? comandosResposta(resposta) : null;
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

			FileInputStream fileInputStream = new FileInputStream(Caminhos.recurso("respostas.json").toFile());
			InputStreamReader reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);

			falasCache = objectMapper.readValue(reader, Falas.class);

			falasCache.getFalas().stream().forEach( resposta -> {

				resposta.setFrase(Normalizador.texto(resposta.getFrase()));

			});
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static String correcaoFrases(String frase){
		double porcentagem = 0;
		String palavras;

		Map<String, Double> bufferCalc = new HashMap<>();

		for (int i = 0; i < falasCache.getFalas().size(); i++) {
			palavras = falasCache.getFalas().get(i).getFrase();

			//porcentagem = Calculos.calculoPorcentagemGeral(frase, palavras);
			porcentagem = Calculos.calculoPorcentagemFrase(frase, palavras, null);
			if (porcentagem==100) {
				System.out.println(porcentagem+" / "+ palavras);
				return palavras;
			}else if(porcentagem>=60){
				System.out.println(porcentagem+" / "+ palavras);
				bufferCalc.put(palavras, porcentagem);
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

	private static String comandosResposta(Resposta resposta){

		String respostaEscolhida = escolheMultiRespostas(resposta.getRespostas());

		if(resposta.getAssunto().equalsIgnoreCase("status")){
			respostaEscolhida= respostaEscolhida + "\nnivel de humor em...  de 10";
		}else if(resposta.getAssunto().equalsIgnoreCase("informações técnicas")){
			respostaEscolhida= respostaEscolhida + "\nnumero de "+ ControleKaira.buscaDadosGerais().getVersao();
		}else if(resposta.getAssunto().equalsIgnoreCase("comando de desligamento")){
			System.exit(0);
		}

		return respostaEscolhida;
	}

	public static void chamadaFrase(String frase, String tipo, String resposta) {

		List<String> reps = new ArrayList<>();

		reps.add(resposta);

		if (objectMapper == null)
			objectMapper = new ObjectMapper();

		falasCache.getFalas().add( new Resposta(frase,reps, tipo));

		try (FileWriter fileWriter = new FileWriter(Caminhos.recurso("respostas.json").toFile(), StandardCharsets.UTF_8)) {

			fileWriter.write(objectMapper.writeValueAsString(falasCache));
		} catch (IOException e) {
			e.printStackTrace();
		}

		ControlePalavra.gravaPalavra(frase);

	}

	public static String fraseExata(String entradaNormalizada) {
		if (falasCache == null) carregaCache();
		return falasCache.getFalas().stream()
				.filter(f -> entradaNormalizada.equalsIgnoreCase(f.getFrase()))
				.map(Resposta::getFrase)
				.findFirst().orElse(null);
	}

	public static String resolver(String entradaNormalizada){
		String exata = fraseExata(entradaNormalizada);
		if(exata != null) return exata;
		return melhorFrasesPorSimilaridade(entradaNormalizada);
	}

	private static String melhorFrasesPorSimilaridade(String entradaNormalizada) {
		Set<String> palavrasEntrada = Arrays.stream(entradaNormalizada.trim().split("\\s+"))
				.filter(t -> !t.isEmpty()).collect(Collectors.toSet());

		if(palavrasEntrada.isEmpty())
			return null;

		String melhorFrase = null;
		double melhorScore = 0;

		for(Resposta frase: falasCache.getFalas()){
			Set<String> palavraFrase = new HashSet<>(Arrays.asList(frase.getFrase().split(" ")));
			long intersecao = palavrasEntrada.stream().filter((palavraFrase::contains)).count();
			double score = (double) intersecao / palavrasEntrada.size();
			if (score > LIMIAR) {
				if(score > melhorScore) {
					melhorScore = score;
					melhorFrase = frase.getFrase();
				} else if (score == melhorScore) {
					long restoAnterior = Arrays.stream(melhorFrase.split(" ")).filter(Predicate.not(palavrasEntrada::contains)).count();
					long resto = palavraFrase.stream().filter(Predicate.not(palavrasEntrada::contains)).count();

					if(resto < restoAnterior){
						melhorScore = score;
						melhorFrase = frase.getFrase();
					}
				}
			}
		}

		return melhorScore >= LIMIAR ? melhorFrase : null;

	}


}