package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * #24 - Resposta ao pedido de nome.
 *
 * Quando a KAIRA pergunta "Como posso te chamar?" e o usuario responde:
 *
 * - nome "pelado" ("joao" sozinho) dentro da janela da pergunta ->
 *   salva e devolve o agradecimento SEM passar pelo classificador;
 * - frase so de nome ("me chamo Ana") -> salva e agradece;
 * - nome + assunto ("meu nome e joao e quero falar de filmes") ->
 *   salva "joao" (captura para no conector), NAO agradece e a fala
 *   vai pro classificador sem o trecho do nome;
 * - respostas de dialogo comum ("oi", "tudo bem", "nao") ficam fora
 *   da lista de captura - continuam no fluxo normal.
 *
 * Contratos novos: ControleSessao.respostaDeNome(fala) e
 * ControleSessao.tiraNome(fala).
 */
public class ControleSessaoNomeTest {

	@Before
	public void zera() {
		ControleSessao.reset();
	}

	@Test
	public void nomePeladoNaJanelaDaPergunta() {
		assertEquals("Como posso te chamar?", ControleSessao.perguntaDeNome());

		ControleSessao.processa("joão");
		assertEquals("nome pelado devia salvar", "joão",
				ControleSessao.get().getNomeUsuario());
		assertEquals("Prazer em conhecer voce, joão!",
				ControleSessao.respostaDeNome("joão"));
	}

	@Test
	public void nomePeladoForaDaJanelaNaoSalva() {
		ControleSessao.processa("joão");
		assertNull("sem a pergunta pendente nao salva",
				ControleSessao.get().getNomeUsuario());
		assertNull(ControleSessao.respostaDeNome("joão"));
		assertEquals("fala fica intacta pro classificador", "joão",
				ControleSessao.tiraNome("joão"));
	}

	@Test
	public void listaDeBloqueioNaoViraNome() {
		assertEquals("Como posso te chamar?", ControleSessao.perguntaDeNome());
		ControleSessao.processa("oi");
		assertNull("\"oi\" nao e nome", ControleSessao.get().getNomeUsuario());
		assertNull(ControleSessao.respostaDeNome("oi"));
		assertEquals("oi", ControleSessao.tiraNome("oi"));

		ControleSessao.reset();
		ControleSessao.perguntaDeNome();
		ControleSessao.processa("tudo bem");
		assertNull("\"tudo bem\" nao e nome",
				ControleSessao.get().getNomeUsuario());
		assertNull(ControleSessao.respostaDeNome("tudo bem"));

		ControleSessao.reset();
		ControleSessao.perguntaDeNome();
		ControleSessao.processa("não");
		assertNull("\"nao\" nao e nome", ControleSessao.get().getNomeUsuario());
	}

	@Test
	public void fraseSoDeNomeGeraAgradecimento() {
		ControleSessao.processa("me chamo Ana");
		assertEquals("Ana", ControleSessao.get().getNomeUsuario());
		assertEquals("Prazer em conhecer voce, Ana!",
				ControleSessao.respostaDeNome("me chamo Ana"));
	}

	/** ACEITE: nome + assunto separa - nome certo e resto intacto. */
	@Test
	public void nomeComAssuntoSeparado() {
		String fala = "meu nome é João e quero falar de filmes";
		ControleSessao.processa(fala);
		assertEquals("captura para no conector e", "João",
				ControleSessao.get().getNomeUsuario());
		assertNull("com assunto junto nao agradece",
				ControleSessao.respostaDeNome(fala));

		String restante = ControleSessao.tiraNome(fala);
		assertTrue("resto do assunto chega ao classificador: " + restante,
				restante.contains("quero falar de filmes"));
		assertFalse("nome sai do texto: " + restante,
				restante.contains("João"));
		assertFalse("o trecho do nome sai por completo: " + restante,
				restante.contains("meu nome"));
	}

	@Test
	public void turnoSemNomePassaIntacto() {
		String fala = "bom dia tudo bem por aqui";
		ControleSessao.processa(fala);
		assertNull(ControleSessao.respostaDeNome(fala));
		assertEquals("turno sem nome nao sofre strip", fala,
				ControleSessao.tiraNome(fala));
	}
}
