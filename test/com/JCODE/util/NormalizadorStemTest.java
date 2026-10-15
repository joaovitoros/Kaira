package com.JCODE.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Testa o stemmer PT-BR caseiro (Tarefa #12, issue #12, Secao 5.3).
 *
 * Normalizador.stem recebe token no formato do Normalizador.texto()
 * (lowercase, sem acento) e devolve o radical pelas regras do plano, EM
 * ORDEM, com a PRIMEIRA que casar vendo. As regras escritas com acento no
 * plano ("coes->cao", "-cao", "-anca") valem na forma sem acento que o
 * pipeline ja produz.
 *
 * Guarda do plano: a regra so vale se o RESULTADO tiver >= 3 letras -
 * senao a palavra volta inteira. Por isso "as" nao vira "a" e "acao" nao
 * vira "a" (mas "acoes" vira "acao" pela regra 1 - as duas unificam).
 *
 * Limite conhecido, documentado na issue: as regras do plano nao alcancam
 * "gosto" nem "gostosa" (nenhuma sufixa ali). O teste amarra so o que as
 * regras alcancam - estender fica a criterio de quem implementa.
 *
 * Puro, sem disco: roda com qualquer CWD.
 */
public class NormalizadorStemTest {

	/** O plano: gostar/gostei/gostando contam como a mesma palavra. */
	@Test
	public void familiaGostarUnifica() {
		assertEquals("gost", Normalizador.stem("gostar"));
		assertEquals("gost", Normalizador.stem("gostei"));
		assertEquals("gost", Normalizador.stem("gostando"));
	}

	/** Plural e o par coes/cao: acoes e acao caem no mesmo radical. */
	@Test
	public void pluralEDecaoUnificam() {
		assertEquals("casa", Normalizador.stem("casas"));
		assertEquals("casa", Normalizador.stem("casa")); // sem regra que case, fica
		assertEquals("acao", Normalizador.stem("acoes")); // regra 1: coes -> cao (4 letras >= 3, vale)
		assertEquals("acao", Normalizador.stem("acao")); // regra 8 casaria mas daria "a" (1 < 3) -> guarda devolve a palavra
	}

	/** Guarda de 3 letras do plano: nao corta pra menos que isso. */
	@Test
	public void guardaDeTresLetrasNaoCortaAbaixo() {
		assertEquals("as", Normalizador.stem("as")); // -s daria "a"
		assertEquals("os", Normalizador.stem("os")); // -s daria "o"
	}

	/** Outras regras do plano, casando na ordem em que aparecem. */
	@Test
	public void outrasRegrasDoPlano() {
		assertEquals("rapida", Normalizador.stem("rapidamente")); // regra 2: -mente
		assertEquals("fal", Normalizador.stem("falou")); // regra 5: -ou
		assertEquals("fal", Normalizador.stem("falamos")); // regra 6: -amos
		assertEquals("terror", Normalizador.stem("terroristas")); // regra 7: -istas
		assertEquals("esper", Normalizador.stem("esperanca")); // regra 10: -anca
		assertEquals("fal", Normalizador.stem("falar")); // regra 11: -ar (radical de 3, vale)
	}

	/** Nulo devolve vazio, mesmo contrato do Normalizador.texto. */
	@Test
	public void nuloDevolveVazio() {
		assertEquals("", Normalizador.stem(null));
	}
}
