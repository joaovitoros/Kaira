package com.JCODE.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.JCODE.controller.ControlePalavra;
import com.JCODE.controller.ControleResposta;
import com.JCODE.controller.ControleSessao;
import com.JCODE.controller.ControleSinonimo;
import com.JCODE.controller.FormulaResposta;
import com.JCODE.util.Caminhos;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Benchmark de aceitacao: re-roda as falas das baterias de teste (out/2026)
 * contra a base ATUAL e compara, turno a turno, com o resultado OBSERVADO na
 * epoca. O corpus pode ter varias RODADAS no mesmo arquivo (campo "rodada"):
 * ao trocar de rodada a copia isolada da base e RESTAURADA, senao o
 * aprendizado de uma contaminaria a medida da seguinte.
 *
 * Ferramenta, NAO e teste (nao termina em Test - o harness compila e nao
 * roda). Somente LEITURA da base real: o replay roda numa COPIA isolada
 * (kaira.home em %TEMP%), entao o aprendizado organico do caminho nao
 * toca intencoes.json/palavras.json do projeto.
 *
 * Cenario: o replay chama o MESMO FormulaResposta.decifrar da janela
 * (cascata direto -> corrigida-palavra -> corrigida-frase -> aposta),
 * sessao zerada a cada bateria e ensino (buffer P1/P2) limpo entre elas.
 * O degrau de cada turno e lido do log redirecionado.
 *
 * Uso (raiz do projeto, libs no classpath):
 *   java -cp "out\build-test;externallibs\*" com.JCODE.tools.ReplayBaterias
 *   java ... ReplayBaterias caminho\para\baterias.json
 *
 * Saidas: resumo no console + logs/baterias/baterias-replay.json.
 */
public class ReplayBaterias {

	private static final List<String> RECURSOS = List.of(
			"intencoes.json", "stopwords.txt", "palavras.json",
			"grupos_sinonimos.json", "kaira.json", "conhecimentos.json");

	private static final Pattern SEPARADOR = Pattern.compile(
			"(?m)^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\s+-{5,}\\s*$");
	private static final Pattern RE_ENTRADA = Pattern.compile("(?m)ENTRADA\\s+:\\s?(.*)$");
	private static final Pattern RE_DEGRAU = Pattern.compile("(?m)DEGRAU\\s+:\\s*(\\S+)");
	private static final Pattern RE_MATCH = Pattern.compile(
			"(?m)MATCH\\s+:\\s*intento=(\\S+?)\\s+score=([\\d.,]+)");

	private static final List<String> ORDEM_DEGRAU = List.of(
			"direto", "aposta", "aprender", "resposta-nome",
			"corrigida-frase", "corrigida-palavra", "comando", "confirma-aposta");

	public static void main(String[] args) throws Exception {
		Path baseReal = Caminhos.base();
		Path json = args.length > 0
				? Path.of(args[0])
				: baseReal.resolve("logs").resolve("baterias").resolve("baterias.json");
		if (!Files.exists(json)) {
			System.err.println("Nao achei " + json);
			System.err.println("Rode antes: python logs/baterias/destilar_baterias.py");
			System.exit(2);
		}
		// opcional: base de origem (dir que contem KAIRA/resources) - ex.: uma
		// copia do ultimo commit, pra isolar o efeito dos DADOS do efeito do
		// CODIGO entre a rodada original e hoje.
		Path raizOrigem = args.length > 1 ? Path.of(args[1]) : baseReal;
		Path saida = args.length > 2
				? Path.of(args[2])
				: baseReal.resolve("logs").resolve("baterias").resolve("baterias-replay.json");

		// ---- copia isolada da base (o replay aprende, mas no temp) ----
		Path home = Files.createTempDirectory("kaira-replay-");
		Path recursos = home.resolve("KAIRA").resolve("resources");
		Files.createDirectories(recursos);
		Path origemRecursos = raizOrigem.resolve("KAIRA").resolve("resources");
		if (!Files.exists(origemRecursos.resolve("intencoes.json"))) {
			System.err.println("Base de origem sem intencoes.json: " + origemRecursos);
			System.exit(2);
		}
		for (String nome : RECURSOS) {
			Path origem = origemRecursos.resolve(nome);
			if (Files.exists(origem))
				Files.copy(origem, recursos.resolve(nome));
		}
		System.setProperty("kaira.home", home.toString());
		System.setProperty("kaira.log.destino", home.resolve("replay.log").toString());
		Caminhos.resetar();
		carregaCaches();
		ControleSessao.reset();
		limpaEnsino();

		ObjectNode cabecalhoBase = retratoDaBase();

		// ---- le o corpus ----
		ObjectMapper om = new ObjectMapper();
		JsonNode raiz = om.readTree(Files.readAllBytes(json));
		List<JsonNode> turnos = new ArrayList<>();
		raiz.get("turnos").forEach(turnos::add);

		// ---- replay (silencioso: o app imprime muito no console) ----
		PrintStream console = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		int bateriaAtual = -1;
		int rodadaAtual = -1;
		try {
			for (JsonNode t : turnos) {
				int rodada = rodadaDe(t);
				int bateria = t.get("bateria").asInt();
				if (rodada != rodadaAtual) {
					// troca de rodada: a base isolada VOLTA ao estado original - o
					// aprendizado de uma rodada nao pode contaminar a medida da seguinte
					if (rodadaAtual != -1) {
						restauraBase(origemRecursos, recursos);
						carregaCaches();
					}
					ControleSessao.reset();
					limpaEnsino();
					rodadaAtual = rodada;
					bateriaAtual = -1;
				}
				if (bateria != bateriaAtual) {
					ControleSessao.reset();
					limpaEnsino();
					bateriaAtual = bateria;
				}
				FormulaResposta.decifrar(t.get("entrada").asText());
			}
		} finally {
			System.setOut(console);
		}

		// ---- le o degrau/intento de cada turno no log ----
		String log = Files.readString(home.resolve("replay.log"), StandardCharsets.UTF_8);
		String[] blocos = SEPARADOR.split(log);
		int n = Math.min(turnos.size(), Math.max(0, blocos.length - 1));
		if (n != turnos.size())
			System.out.printf("AVISO: %d turnos no corpus, %d no log%n", turnos.size(), blocos.length - 1);

		List<Resultado> resultados = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			JsonNode antes = turnos.get(i);
			String bloco = blocos[i + 1];
			Resultado r = new Resultado();
			r.rodada = rodadaDe(antes);
			r.bateria = antes.get("bateria").asInt();
			r.turno = antes.get("turno").asInt();
			r.entrada = antes.get("entrada").asText();
			r.degrauAntes = texto(antes, "degrau");
			r.intentoAntes = texto(antes, "intento");
			r.degrauAgora = busca(RE_DEGRAU, bloco);
			Matcher m = RE_MATCH.matcher(bloco);
			if (m.find()) {
				r.intentoAgora = m.group(1);
				r.scoreAgora = Double.parseDouble(m.group(2).replace(",", "."));
			}
			resultados.add(r);
		}

		imprimeResumo(resultados, origemRecursos);
		gravaDetalhe(saida, cabecalhoBase, resultados);
		apaga(home);
	}

	// ------------------------------------------------------------------

	private static void imprimeResumo(List<Resultado> rs, Path origemRecursos) {
		int total = rs.size();
		System.out.println();
		System.out.println("=== REPLAY DAS BATERIAS (out/2026) CONTRA A BASE ATUAL ===");
		System.out.printf("turnos: %d  |  base real intocada (replay em copia isolada)%n", total);
		System.out.println("base usada: " + origemRecursos);
		System.out.println();

		Map<String, Integer> antes = conta(rs, true);
		Map<String, Integer> agora = conta(rs, false);

		System.out.println("DEGRAU                 ANTES        AGORA       DELTA");
		for (String d : ordem(antes, agora)) {
			int a = antes.getOrDefault(d, 0);
			int b = agora.getOrDefault(d, 0);
			System.out.printf("%-18s %5d %5.1f%%  %5d %5.1f%%  %+5d%n",
					d, a, 100.0 * a / total, b, 100.0 * b / total, b - a);
		}
		System.out.println();

		System.out.println("== por rodada");
		for (int rodada : rs.stream().map(r -> r.rodada).distinct().sorted().toList()) {
			List<Resultado> bloco = rs.stream().filter(r -> r.rodada == rodada).toList();
			Map<String, Integer> a = conta(bloco, true);
			Map<String, Integer> b = conta(bloco, false);
			System.out.printf("  rodada %d (%d turnos): aposta %d->%d | direto %d->%d | aprender %d->%d%n",
					rodada, bloco.size(),
					a.getOrDefault("aposta", 0), b.getOrDefault("aposta", 0),
					a.getOrDefault("direto", 0), b.getOrDefault("direto", 0),
					a.getOrDefault("aprender", 0), b.getOrDefault("aprender", 0));
		}
		System.out.println();

		Map<String, Integer> trocas = new TreeMap<>();
		Map<String, Integer> transicoes = new TreeMap<>();
		for (Resultado r : rs) {
			if (r.intentoAntes != null && r.intentoAgora != null
					&& !r.intentoAntes.equals(r.intentoAgora))
				trocas.merge(r.intentoAntes + " -> " + r.intentoAgora, 1, Integer::sum);
			String da = r.degrauAntes == null ? "(sem degrau)" : r.degrauAntes;
			String db = r.degrauAgora == null ? "(sem degrau)" : r.degrauAgora;
			transicoes.merge(da + " -> " + db, 1, Integer::sum);
		}
		int iguais = (int) transicoes.entrySet().stream()
				.filter(e -> e.getKey().split(" -> ")[0].equals(e.getKey().split(" -> ")[1]))
				.mapToInt(Map.Entry::getValue).sum();
		System.out.printf("== degrau: %d/%d turnos mantiveram o mesmo degrau%n", iguais, total);
		transicoes.entrySet().stream()
				.filter(e -> !e.getKey().split(" -> ")[0].equals(e.getKey().split(" -> ")[1]))
				.sorted((x, y) -> y.getValue() - x.getValue())
				.forEach(e -> System.out.printf("   %-42s %3d%n", e.getKey(), e.getValue()));
		System.out.println();

		System.out.printf("== intento: %d turnos com intento ANTES e AGORA, dos quais %d mudaram%n",
				rs.stream().filter(r -> r.intentoAntes != null && r.intentoAgora != null).count(),
				trocas.values().stream().mapToInt(Integer::intValue).sum());
		trocas.entrySet().stream()
				.sorted((x, y) -> y.getValue() - x.getValue())
				.limit(20)
				.forEach(e -> System.out.printf("   %-44s %3d%n", e.getKey(), e.getValue()));
		System.out.println();

		System.out.println("== conferencias pontuais");
		for (Resultado r : rs) {
			String f = r.entrada.toLowerCase(Locale.ROOT);
			if (f.contains("kaira") || f.contains("obrigad") || f.contains("faleceu")
					|| f.contains("pacient") || f.contains("desistir") || f.contains("luto"))
				System.out.printf("   %-44s antes: %-14s %-18s agora: %-14s %s%n",
						curto(r.entrada), nulo(r.degrauAntes), nulo(r.intentoAntes),
						nulo(r.degrauAgora), nulo(r.intentoAgora));
		}
	}

	private static void gravaDetalhe(Path destino, ObjectNode cabecalho, List<Resultado> rs) throws Exception {
		ObjectMapper om = new ObjectMapper();
		ObjectNode saida = om.createObjectNode();
		saida.put("gerado_por", "com.JCODE.tools.ReplayBaterias");
		saida.put("quando", LocalDateTime.now().toString());
		saida.put("fonte", "logs/baterias/baterias.json (resultado observado out/2026)");
		saida.put("replay", "FormulaResposta.decifrar, sessao zerada por bateria, base isolada em %TEMP%");
		saida.set("base_antes_do_replay", cabecalho);

		ObjectNode resumo = om.createObjectNode();
		Map<String, Integer> antes = conta(rs, true);
		Map<String, Integer> agora = conta(rs, false);
		for (String d : ordem(antes, agora)) {
			ObjectNode par = om.createObjectNode();
			par.put("antes", antes.getOrDefault(d, 0));
			par.put("agora", agora.getOrDefault(d, 0));
			resumo.set(d, par);
		}
		saida.set("resumo_degrau", resumo);

		ArrayNode arr = om.createArrayNode();
		for (Resultado r : rs) {
			ObjectNode t = om.createObjectNode();
			t.put("rodada", r.rodada);
			t.put("bateria", r.bateria);
			t.put("turno", r.turno);
			t.put("entrada", r.entrada);
			t.put("degrau_antes", r.degrauAntes);
			t.put("degrau_agora", r.degrauAgora);
			t.put("intento_antes", r.intentoAntes);
			t.put("intento_agora", r.intentoAgora);
			if (r.scoreAgora != null)
				t.put("score_agora", r.scoreAgora);
			arr.add(t);
		}
		saida.set("turnos", arr);

		Path alvo = destino;
		Files.createDirectories(alvo.getParent());
		Files.writeString(alvo, om.writerWithDefaultPrettyPrinter().writeValueAsString(saida) + "\n",
				StandardCharsets.UTF_8);
		System.out.println();
		System.out.println("detalhe turno a turno: " + alvo);
	}

	private static ObjectNode retratoDaBase() {
		int exemplos = 0, respostas = 0;
		for (var i : ControleResposta.getIntencoes()) {
			exemplos += i.getExemplos().size();
			respostas += i.getRespostas().size();
		}
		ObjectMapper om = new ObjectMapper();
		ObjectNode n = om.createObjectNode();
		n.put("intencoes", ControleResposta.getIntencoes().size());
		n.put("exemplos", exemplos);
		n.put("respostas", respostas);
		return n;
	}

	private static void carregaCaches() {
		ControleResposta.carregaCache();
		ControleResposta.invalidaCacheIdf();
		ControlePalavra.carregaCache();
		ControleSinonimo.carregaCache();
	}

	/** buffer P1/P2 do ensino e package-private: zera por reflexao. */
	private static void limpaEnsino() {
		try {
			for (String nome : List.of("bufferFrases", "respostaEnsino")) {
				Field f = FormulaResposta.class.getDeclaredField(nome);
				f.setAccessible(true);
				f.set(null, null);
			}
		} catch (Exception e) {
			throw new IllegalStateException("nao consegui limpar o ensino", e);
		}
	}

	/** rodada do turno (corpus antigo, sem o campo, e rodada 1). */
	private static int rodadaDe(JsonNode t) {
		JsonNode r = t.get("rodada");
		return r == null || r.isNull() ? 1 : r.asInt();
	}

	/** devolve a base isolada ao estado da origem (troca de rodada). */
	private static void restauraBase(Path origem, Path destino) throws Exception {
		if (Files.exists(destino)) {
			try (var caminhos = Files.walk(destino)) {
				caminhos.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
					try {
						Files.delete(p);
					} catch (Exception ignorada) {
					}
				});
			}
		}
		Files.createDirectories(destino);
		for (String nome : RECURSOS) {
			Path de = origem.resolve(nome);
			if (Files.exists(de))
				Files.copy(de, destino.resolve(nome));
		}
	}

	/** apaga a copia isolada (best effort). */
	private static void apaga(Path home) {
		try (var caminhos = Files.walk(home)) {
			caminhos.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (Exception ignorada) {
				}
			});
		} catch (Exception ignorada) {
		}
	}

	private static Map<String, Integer> conta(List<Resultado> rs, boolean antes) {
		Map<String, Integer> mapa = new LinkedHashMap<>();
		for (Resultado r : rs) {
			String d = antes ? r.degrauAntes : r.degrauAgora;
			mapa.merge(d == null ? "(sem degrau)" : d, 1, Integer::sum);
		}
		return mapa;
	}

	private static List<String> ordem(Map<String, Integer> a, Map<String, Integer> b) {
		List<String> chaves = new ArrayList<>(ORDEM_DEGRAU);
		for (String k : a.keySet())
			if (!chaves.contains(k) && !k.equals("(sem degrau)"))
				chaves.add(k);
		for (String k : b.keySet())
			if (!chaves.contains(k) && !k.equals("(sem degrau)"))
				chaves.add(k);
		if (a.containsKey("(sem degrau)") || b.containsKey("(sem degrau)"))
			chaves.add("(sem degrau)");
		return chaves;
	}

	private static String texto(JsonNode n, String campo) {
		JsonNode v = n.get(campo);
		return v == null || v.isNull() ? null : v.asText();
	}

	private static String busca(Pattern p, String bloco) {
		Matcher m = p.matcher(bloco);
		return m.find() ? m.group(1) : null;
	}

	private static String nulo(String s) {
		return s == null ? "null" : s;
	}

	private static String curto(String s) {
		return s.length() <= 42 ? s : s.substring(0, 39) + "...";
	}

	private static final class Resultado {
		int rodada;
		int bateria;
		int turno;
		String entrada;
		String degrauAntes;
		String degrauAgora;
		String intentoAntes;
		String intentoAgora;
		Double scoreAgora;
	}
}
