package com.JCODE.tools;

import com.JCODE.controller.ControleCorrecao;
import com.JCODE.controller.ControlePalavra;
import com.JCODE.controller.ControlePreAnalise;
import com.JCODE.controller.ControleResposta;
import com.JCODE.controller.ControleSessao;
import com.JCODE.controller.ControleSinonimo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Sessao;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Normalizador;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulador de conversa (ferramenta, NAO e teste - o harness compila mas
 * nao roda, por nao terminar em Test).
 *
 * "Conversa" com a KAIRA de verdade pelo CASCADE REAL (lerResposta ->
 * corrigirPalavras -> corrigirFrases -> respostaAposta -> fallback), num
 * roteiro LONGO de cotidiano (nome, fatos, musica, filmes, series,
 * curiosidade, typos, frases sem sentido) e gera duas saidas em
 * saidas/:
 *
 * 1. simulacao_conversa.json - o TRACO: cada turno com o degrau que
 *    respondeu e o estado da sessao (o que ela absolveu);
 * 2. aprendizado_simulado.json - NO MESMO PADRAO do intencoes.json
 *    ({"intencoes":[{intento,exemplos,respostas,acoes}]}) com TUDO que
 *    ela aprendeu na conversa: auto_aprendizado (as frases que ela nao
 *    soube - iriam pra caixa de ensino), fatos_aprendidos (cada fato
 *    com a frase que o ensinou) e perfil_usuario (nome).
 *
 * Somente LEITURA da base: nada de caminho de aprendizado/gravacao nos
 * arquivos reais.
 *
 * Uso (raiz do projeto, libs no classpath):
 *   java -cp "bin;externallibs/*" com.JCODE.tools.SimuladorDeConversa
 */
public class SimuladorDeConversa {

	private static final String ORIGEM = "simulacao out/2026";

	private static final String[] ROTEIRO = {
			"bom dia",
			"meu nome é João",
			"como você está?",
			"eu gosto de música",
			"qual sua música favorita",
			"indica uma música boa",
			"eu moro em São Paulo",
			"onde fica o museu de arte",
			"qual seu filme favorito",
			"vi um filme incrível ontem",
			"me recomenda uma série boa",
			"terminei uma série hoje",
			"conta uma curiosidade",
			"me conta algo interessante",
			"qual a capital da frança",
			"como fazer bolo de cenoura",
			"que horas",
			"meu jogo favorito é minecraft",
			"quem é você",
			"voce e legal",
			"ola tud bem",
			"estou entediado vamos conversar",
			"como foi o seu dia",
			"gosto de gatos",
			"quem inventou o rádio",
			"me conta uma piada",
			"nasci em Belo Horizonte",
			"eu trabalho com programação",
			"quem é o buffy",
			"asdkjhqwe",
			"quanto tempo voce leva pra ver uma série",
			"tchau",
			"até logo"
	};

	public static void main(String[] args) throws Exception {
		ControleSinonimo.carregaCache();
		ControleResposta.carregaCache();
		ControlePalavra.carregaCache();
		ControleSessao.reset();

		System.out.println("=== simulando conversa com a KAIRA (base real) ===");
		List<Map<String, Object>> turnos = new ArrayList<>();

		// o que ela aprendeu no caminho
		List<String> frasesNaoSabe = new ArrayList<>();
		List<String> frasesFatos = new ArrayList<>();
		List<String> respostasFatos = new ArrayList<>();
		List<String> frasesNome = new ArrayList<>();
		List<String> respostasNome = new ArrayList<>();

		for (int n = 1; n <= ROTEIRO.length; n++) {
			String fala = ROTEIRO[n - 1];

			Map<String, String> fatosAntes = new LinkedHashMap<>(ControleSessao.get().getFatos());
			String nomeAntes = ControleSessao.get().getNomeUsuario();

			// mesmo cascade do FormulaResposta.decifrar, sem UI e sem gravacao
			ControleSessao.processa(fala);
			String entrada = Normalizador.texto(fala);

			String degrau = "aprender";
			String resposta = ControleResposta.lerResposta(entrada);
			if (resposta != null)
				degrau = "direto";
			if (resposta == null) {
				resposta = ControleCorrecao.corrigirPalavras(entrada);
				if (resposta != null)
					degrau = "corrigida-palavra";
			}
			if (resposta == null) {
				resposta = ControleCorrecao.corrigirFrases(entrada);
				if (resposta != null)
					degrau = "corrigida-frase";
			}
			if (resposta == null) {
				resposta = ControleResposta.respostaAposta(entrada);
				if (resposta != null)
					degrau = "aposta";
			}
			if (resposta == null) {
				String slot = ControlePreAnalise.preAnalise(entrada).getSlot();
				resposta = ControleResposta.respostaFallback(slot);
			}

			boolean perguntouNome = false;
			String chamada = ControleSessao.perguntaDeNome();
			if (chamada != null) {
				resposta = resposta + " " + chamada;
				perguntouNome = true;
			}
			ControleSessao.anotaResposta(resposta);

			// diff de aprendizado deste turno
			if ("aprender".equals(degrau))
				frasesNaoSabe.add(fala);
			for (Map.Entry<String, String> e : ControleSessao.get().getFatos().entrySet())
				if (!e.getValue().equals(fatosAntes.get(e.getKey()))) {
					frasesFatos.add(fala);
					respostasFatos.add(e.getKey() + " = " + e.getValue());
				}
			String nomeNovo = ControleSessao.get().getNomeUsuario();
			if (nomeNovo != null && !nomeNovo.equals(nomeAntes)) {
				frasesNome.add(fala);
				respostasNome.add("nome = " + nomeNovo);
			}

			Sessao s = ControleSessao.get();
			System.out.println(String.format("[%02d] voce     : %s", n, fala));
			System.out.println(String.format("[%02d] kaira(%s): %s", n, degrau, resposta));

			Map<String, Object> t = new LinkedHashMap<>();
			t.put("n", n);
			t.put("entrada", fala);
			t.put("degrau", degrau);
			t.put("resposta", resposta);
			t.put("perguntouNome", perguntouNome);
			Map<String, Object> snapshot = new LinkedHashMap<>();
			snapshot.put("nome", s.getNomeUsuario());
			snapshot.put("topico", s.getTopicoAtual());
			snapshot.put("turnos", s.getTurnos());
			snapshot.put("fatos", new LinkedHashMap<>(s.getFatos()));
			snapshot.put("historico", s.getHistorico().size());
			t.put("sessao", snapshot);
			turnos.add(t);
		}

		Sessao fim = ControleSessao.get();
		System.out.println();
		System.out.println("=== o que ela absorveu ===");
		System.out.println("nome   : " + fim.getNomeUsuario());
		System.out.println("topico : " + fim.getTopicoAtual());
		System.out.println("turnos : " + fim.getTurnos());
		System.out.println("fatos  : " + fim.getFatos());
		System.out.println("historico (corte 20): " + fim.getHistorico().size() + " turnos guardados");
		System.out.println("nao soube (" + frasesNaoSabe.size() + "): " + frasesNaoSabe);
		System.out.println("fatos aprendidos (" + respostasFatos.size() + "): " + respostasFatos);

		Path pasta = Caminhos.base().resolve("saidas");
		Files.createDirectories(pasta);

		Path traco = pasta.resolve("simulacao_conversa.json");
		Files.write(traco, montaTraco(turnos, fim).getBytes(StandardCharsets.UTF_8));

		Path aprendido = pasta.resolve("aprendizado_simulado.json");
		Files.write(aprendido, montaAprendizado(frasesNaoSabe, frasesFatos, respostasFatos,
				frasesNome, respostasNome).getBytes(StandardCharsets.UTF_8));

		System.out.println();
		System.out.println("traço gravado em: " + traco);
		System.out.println("aprendizado (padrao intencoes.json) em: " + aprendido);
	}

	/** traco da conversa: turnos + estado da sessao a cada passo. */
	private static String montaTraco(List<Map<String, Object>> turnos, Sessao fim) {
		StringBuilder b = new StringBuilder();
		b.append("{\n");
		b.append("  \"gerado por\": \"com.JCODE.tools.SimuladorDeConversa (roteiro longo de cotidiano)\",\n");

		int totalEx = 0, totalResp = 0;
		for (Intencao i : ControleResposta.getIntencoes()) {
			totalEx += i.getExemplos().size();
			totalResp += i.getRespostas().size();
		}
		b.append("  \"base\": {\"intencoes\": ").append(ControleResposta.getIntencoes().size())
				.append(", \"exemplos\": ").append(totalEx)
				.append(", \"respostas\": ").append(totalResp).append("},\n");

		b.append("  \"turnos\": [\n");
		for (int i = 0; i < turnos.size(); i++) {
			Map<String, Object> t = turnos.get(i);
			b.append("    {\"n\": ").append(t.get("n"))
					.append(", \"entrada\": \"").append(escapa((String) t.get("entrada")))
					.append("\", \"degrau\": \"").append(t.get("degrau"))
					.append("\", \"resposta\": \"").append(escapa((String) t.get("resposta")))
					.append("\", \"perguntouNome\": ").append(t.get("perguntouNome"))
					.append(", \"sessao\": ").append(jsonSessao((Map<?, ?>) t.get("sessao")))
					.append("}").append(i < turnos.size() - 1 ? "," : "").append("\n");
		}
		b.append("  ],\n");
		Map<String, Object> snapshotFinal = new LinkedHashMap<>();
		snapshotFinal.put("nome", fim.getNomeUsuario());
		snapshotFinal.put("topico", fim.getTopicoAtual());
		snapshotFinal.put("turnos", fim.getTurnos());
		snapshotFinal.put("fatos", new LinkedHashMap<>(fim.getFatos()));
		snapshotFinal.put("historico", fim.getHistorico().size());
		b.append("  \"sessaoFinal\": ").append(jsonSessao(snapshotFinal)).append("\n");
		b.append("}\n");
		return b.toString();
	}

	/**
	 * Tudo que ela aprendeu, no MESMO PADRAO do intencoes.json - da pra
	 * ate colar por cima da base real se quiser ensinar de vez.
	 */
	private static String montaAprendizado(List<String> frasesNaoSabe, List<String> frasesFatos,
			List<String> respostasFatos, List<String> frasesNome, List<String> respostasNome) {
		List<String> respostasVazias = new ArrayList<>();
		StringBuilder b = new StringBuilder();
		b.append("{\n  \"intencoes\": [\n    ");
		b.append(jsonIntencao("auto_aprendizado", frasesNaoSabe, respostasVazias)).append(",\n    ");
		b.append(jsonIntencao("fatos_aprendidos", frasesFatos, respostasFatos)).append(",\n    ");
		b.append(jsonIntencao("perfil_usuario", frasesNome, respostasNome)).append("\n  ]\n}\n");
		return b.toString();
	}

	private static String jsonIntencao(String nome, List<String> exemplos, List<String> respostas) {
		StringBuilder b = new StringBuilder();
		b.append("{\"intento\": \"").append(escapa(nome)).append("\", \"exemplos\": [");
		for (int i = 0; i < exemplos.size(); i++) {
			if (i > 0)
				b.append(", ");
			b.append("{\"texto\": \"").append(escapa(exemplos.get(i)))
					.append("\", \"origem\": \"").append(ORIGEM).append("\"}");
		}
		b.append("], \"respostas\": [");
		for (int i = 0; i < respostas.size(); i++) {
			if (i > 0)
				b.append(", ");
			b.append("{\"texto\": \"").append(escapa(respostas.get(i)))
					.append("\", \"origem\": \"").append(ORIGEM)
					.append("\", \"humorMin\": 0, \"humorMax\": 10}");
		}
		b.append("], \"acoes\": []}");
		return b.toString();
	}

	private static String jsonSessao(Map<?, ?> s) {
		StringBuilder b = new StringBuilder();
		b.append("{\"nome\": ").append(jsonOuNull((String) s.get("nome")))
				.append(", \"topico\": ").append(jsonOuNull((String) s.get("topico")))
				.append(", \"turnos\": ").append(s.get("turnos"))
				.append(", \"historico\": ").append(s.get("historico"))
				.append(", \"fatos\": ").append(jsonMapa(s.get("fatos"))).append("}");
		return b.toString();
	}

	private static String jsonMapa(Object mapa) {
		if (!(mapa instanceof Map))
			return "{}";
		Map<?, ?> m = (Map<?, ?>) mapa;
		StringBuilder b = new StringBuilder("{");
		boolean primeiro = true;
		for (Map.Entry<?, ?> e : m.entrySet()) {
			if (!primeiro)
				b.append(", ");
			b.append("\"").append(escapa(String.valueOf(e.getKey()))).append("\": \"")
					.append(escapa(String.valueOf(e.getValue()))).append("\"");
			primeiro = false;
		}
		b.append("}");
		return b.toString();
	}

	private static String jsonOuNull(String valor) {
		return valor == null ? "null" : "\"" + escapa(valor) + "\"";
	}

	private static String escapa(String texto) {
		if (texto == null)
			return "";
		return texto.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\n", "\\n").replace("\r", "\\r");
	}
}
