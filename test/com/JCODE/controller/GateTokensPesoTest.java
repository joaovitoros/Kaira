package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.JCODE.domain.Intencao;
import com.JCODE.util.Normalizador;

/**
 * SPEC VERMELHA do GATE de tokens de peso (rodada 2 de baterias as cegas,
 * out/2026). Vermelha de proposito: ela falha HOJE e so verde quando a casa
 * implementar o gate no matcher (ControleResposta.melhorIntencaoDetalhado).
 * Commita spec + codigo juntos - o master fica verde.
 *
 * PROBLEMA medido: o matcher premia match parcial com token comum. A sonda
 * (logs/baterias + ProbeGate) mostrou "isso foi muito bom" -> agradecimento
 * 1.00 decidido por {foi, bom} ("foi" casando direto no exemplo "obrigado vc
 * foi muito gentil tchau"), "muito bem" -> confusao 1.00 via {bem}~{assim},
 * "eu trabalho com isso" -> felicidade_usuario 1.00 porque o grupo
 * trabalho~emprego ("consegui o emprego") empatou em 1.00 com o match direto
 * de trabalho_usuario e venceu no resto. No corpus de 811 turnos, ~29 matches
 * foram decididos por 1 token (eh, foi, tem, esta, amo) e 40% dos turnos
 * terminaram em aposta.
 *
 * RECEITA (as 4 decisoes de desenho - a implementacao e da casa, eu escrevo
 * so a spec e os dados):
 *
 *   1. tokens comuns sem peso: lista curada (dados: "foi", "bem", "mesmo",
 *      "bom", "coisa", "tipo", "mais", "ser", "ter", "estar"... candidatos;
 *      valide com suite + avaliador + replay e tire o que quebrar caso
 *      congelado). Trata como stopword NA ENTRADA DO MATCHER - filtro junto
 *      de tokensDe() na linha do matcher, NOS tokensDe() gerais (fraseSoGenerica
 *      e desempate compartilham o metodo; mudar la tem blast radius fora do
 *      matcher). Frase que vira' tokens vazios ja' devolve null hoje (linha
 *      do matcher: palavrasEntrada.isEmpty()).
 *
 *   2. match parcial exige >= 2 tokens de peso cobertos: o contador de
 *      cobertura (intersecao) ja' existe no loop; candidato com score < 1.0
 *      e menos de 2 tokens cobertos (direto OU por grupo) nao pode virar
 *      resposta nem aposta - cai no fallback honesto.
 *
 *   3. isencao de cobertura cheia e fraseExata: score == 1.0 (entrada toda
 *      coberta - "obrigado", "minha mae", "meu pai" por stem) e o caminho
 *      fraseExata passam direto. O gate so' pega match parcial.
 *
 *   4. match mediado por grupo conta como parcial (peso menor no numerador,
 *      nao 1.0). E tambem NAO vale isencao de resto-0 quando a cobertura e'
 *      so' de grupo - sem isso o "amo" sobrevive como match de 1 token de
 *      cobertura cheia via grupo ("sou mestre em adiar").
 *
 * VALIDACAO DE ACEITE (replay de 811 turnos em logs/baterias/baterias.json):
 *   - estes 8 casos verdes + os 14 guardas verdes;
 *   - Avaliador 44/44 (os fuzzy congelados sao todos cobertura cheia ou
 *     >= 2 tokens cobertos - conferido caso a caso: "eae kaira tudo certo"
 *     tem 2 diretos, "obrigada por ouvir" tem 1 direto + 1 de grupo);
 *   - replay sem regressao: direto nao cai (hoje 40,0%), GRAVE nao sobe
 *     (hoje 0), e medir quantas apostas viram fallback honesto.
 */
public class GateTokensPesoTest {

	/** Caso -> intento esperado (ou null = fallback honesto). */
	private static final Map<String, String> DESEJADO = new LinkedHashMap<>();
	static {
		// -- R: lixo decidido por token comum -> fallback (hoje: agradecimento
		// 1.00, confusao 1.00; todos tem "foi"/"bem"/"bom" como conteudo) --
		DESEJADO.put("isso foi muito bom", null);
		DESEJADO.put("foi muito bom", null);
		DESEJADO.put("foi sim, muito bom", null);
		DESEJADO.put("foi bem mesmo", null);
		DESEJADO.put("muito bem", null);
		// -- R: grupo nao e' cobertura cheia nem decide sozinho --
		// hoje: felicidade_usuario 1.00 (grupo trabalho~emprego no resto);
		// com grupo=parcial o match DIRETO de trabalho_usuario (1.00) vence.
		DESEJADO.put("eu trabalho com isso", "trabalho_usuario");
		// -- R: um token coberto nao vira resposta; dado junto resolve --
		// hoje: aposta de felicidade_usuario 0.43; precisa tambem do passo de
		// dados da issue (exemplo "procuro emprego" em trabalho_usuario, ja
		// que "estou procurando emprego" nao empata por stem).
		DESEJADO.put("procuro emprego", "trabalho_usuario");
	}

	/** Guardas: o gate NAO pode derrubar nada disto (verde hoje, verde la'). */
	private static final Map<String, String> GUARDAS = new LinkedHashMap<>();
	static {
		// ancoras de 1 token / match exato
		GUARDAS.put("obrigado", "agradecimento");
		GUARDAS.put("valeu", "agradecimento");
		GUARDAS.put("bom dia", "cumprimentar");
		GUARDAS.put("tudo bem", "status_geral");
		// entrada inteira coberta (isencao de resto-0)
		GUARDAS.put("penso em desistir", "peso_demais");
		GUARDAS.put("nao vejo a hora de acabar", "peso_demais");
		GUARDAS.put("minha mae faleceu", "perda_apoio"); // fragmento solto "minha mae" virou familia_usuario na curadoria da casa (19:34) - guarda na frase de luto completa
		GUARDAS.put("meu marido faleceu ano passado", "perda_apoio");
		GUARDAS.put("meu pai", "familia_usuario");
		GUARDAS.put("meus pais", "familia_usuario");
		// ancoras curtas da curadoria da casa (19:34): fragmentos soltos saem do
		// perda_apoio de proposito ("meu marido" -> relacao_usuario, "minha mae"
		// -> familia_usuario). Se a lista de comuns tirar "meu"/"minha", elas
		// ficam com 1 token de peso e so' passam pela isencao de cobertura cheia
		// (1/1 = 1.0) - o gate tem que deixar isso de pe'.
		GUARDAS.put("meu marido", "relacao_usuario");
		GUARDAS.put("minha mae", "familia_usuario");
		GUARDAS.put("dia hoje", "felicidade_usuario");
		// fuzzy congelados do avaliador que andam de 2 tokens ou de grupo
		GUARDAS.put("como vc esta", "status_geral");
		GUARDAS.put("eae kaira, tudo certo?", "cumprimentar");
		GUARDAS.put("obrigada por ouvir", "agradecimento");
	}

	@Before
	public void carregaCaches() {
		ControleResposta.carregaCache();
		ControleSinonimo.carregaCache();
		ControlePalavra.carregaCache();
	}

	private static String intentoDe(String fala) {
		ControleSessao.reset();
		Intencao r = ControleResposta.buscaResposta(Normalizador.texto(fala));
		return r == null ? null : r.getIntento();
	}

	@Test
	public void lixoSoDeTokenComumCaiNoFallback() {
		String[] frases = { "isso foi muito bom", "foi muito bom", "foi sim, muito bom",
				"foi bem mesmo", "muito bem" };
		for (String fala : frases) {
			assertEquals("\"" + fala + "\" nao pode virar resposta decidida por token comum "
					+ "(foi/bem/bom/mesmo nao sao conteudo; no corpus 29 matches foram "
					+ "decididos por 1 token) - esperado fallback honesto",
					DESEJADO.get(fala), intentoDe(fala));
		}
	}

	@Test
	public void matchMediadoPorGrupoNaoVenceCoberturaDireta() {
		// "eu trabalho com isso": grupo trabalho~emprego empata em 1.00 hoje e
		// vence no resto; com grupo=parcial o exemplo DIRETO de trabalho_usuario
		// ("trabalho com vendas") fica com a entrada coberta e leva.
		assertEquals("match so de grupo nao pode se passar por cobertura cheia",
				"trabalho_usuario", intentoDe("eu trabalho com isso"));
	}

	@Test
	public void matchPrecisaDeDoisTokensOuCoberturaCheia() {
		// "procuro emprego": 1 token coberto ({empreg}) por IDFs altos vira
		// aposta de felicidade 0.43 hoje. Com o gate cai em fallback ate' o
		// passo de dados da issue (exemplo direto em trabalho_usuario).
		assertEquals("um token coberto nao sustenta resposta nem aposta",
				"trabalho_usuario", intentoDe("procuro emprego"));
	}

	@Test
	public void fragmentoNaoEGovernadoPorGrupoDistante() {
		// "amo" (fragmento de corpus): o pecado era o match SO' de grupo decidir
		// o fragmento (procrastinacao 1.00 via grupo amo~mestre). Com grupo
		// valendo parcial, casar direto com um exemplo de cobertura cheia
		// (namoro) e' aceitavel - o que nao pode voltar e' o grupo decidir.
		assertNotEquals("fragmento nao pode voltar a ser decidido por match "
				+ "so' de grupo (procrastinacao_usuario antes)", "procrastinacao_usuario",
				intentoDe("amo"));
	}

	@Test
	public void ancorasESubconjuntosSobrevivemAoGate() {
		for (Map.Entry<String, String> e : GUARDAS.entrySet()) {
			assertEquals("\"" + e.getKey() + "\" e' guarda: o gate so' pega match "
					+ "parcial, e isto e' cobertura cheia, match exato ou >= 2 tokens",
					e.getValue(), intentoDe(e.getKey()));
		}
	}
}
