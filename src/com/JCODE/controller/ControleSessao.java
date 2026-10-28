package com.JCODE.controller;

import com.JCODE.domain.Intencao;
import com.JCODE.domain.Sessao;
import com.JCODE.domain.Turno;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Registro;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ControleSessao {

    private static Sessao sessao = new Sessao();
    private static boolean perguntouNome = false;
    private static boolean nomeCapturado = false;

    // #41 - pergunta pendente dela: a fala seguinte e' a resposta do followup
    private static String familiaPergunta = null;
    private static String perguntaPendente = null;

    private static final String ARQUIVO_SESSAO = "sessao.json";

    private static int turnosDesdePergunta = 0;
    // #28 - turno em que o usuario pediu continuacao: o pool generico
    // do §7.2 fica de fora (o followup do topico passa)
    private static boolean turnoDeContinuacao = false;

    // #54 - o turno pediu profundidade ("explica melhor"): o dicionario fala o texto
    private static boolean turnoDeExplicacao = false;
    private static String ultimoInteresse = null;

    private static final int TURNOS_SEM_PERGUNTA = 3;

    private static final Pattern[] PADROES_NOME = {
            Pattern.compile("(?i)meu nome (?:é|eh|e)\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){0,2}?)(?=\\s*(?:$|[.!?])|\\s*,|\\s+(?:e|que|com|pra|para|vou|quero)\\b)"),
            Pattern.compile("(?i)me chamo\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){0,2}?)(?=\\s*(?:$|[.!?])|\\s*,|\\s+(?:e|que|com|pra|para|vou|quero)\\b)"),
            Pattern.compile("(?i)pode me chamar de\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){0,2}?)(?=\\s*(?:$|[.!?])|\\s*,|\\s+(?:e|que|com|pra|para|vou|quero)\\b)"),
            Pattern.compile("(?i)^(?:eu\\s+)?sou\\s+(?:o\\s+|a\\s+)?([\\p{L}]+(?:\\s+[\\p{L}]+){0,2}?)(?=\\s*(?:$|[.!?])|\\s*,|\\s+(?:e|que|com|pra|para|vou|quero)\\b)")
    };

    /** #63 - profissao nao e' nome ("sou enfermeira" nao vira "Prazer, enfermeira!"). */
    private static final String PROFISSOES =
            "(?i)(?:enfermeir[ao]|motorista|caminhoneir[ao]|professor[a]?|medic[oa]"
                    + "|estudante|programador[a]?|designer|corinthian[oa]|apaixonad[oa]"
                    + "|dev|pessoa)";

    private static final String[][] PADROES_FATO = {
            {"gosta de",     "(?i).*\\bgosto de\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"mora em",      "(?i).*\\bmor[oa]\\s+em\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"trabalha com", "(?i).*\\btrabalh[oa]\\s+com\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"nasceu em",    "(?i).*\\bnasci\\s+em\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"meu X favorito e Y",
             "(?i).*\\bmeu\\s+([\\p{L}\\s]+?)\\s+favorit[oa]\\s+(?:é|eh|e)\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            // #52 - o espelho feminino do padrao generico
            {"minha X favorita e Y",
                    "(?i).*\\bminha\\s+([\\p{L}\\s]+?)\\s+favorit[oa]\\s+(?:é|eh|e)\\s+([\\p{L}]+?)(?:[.!?]|$)"},
            // #32 - P7.4: fatos novos; artigo cortado no padrao (PADROES_NOME)
            {"time",            "(?i).*\\bmeu time (?:favorit[oa]\\s+)?(?:é|eh|e)\\s+(?:o\\s+|a\\s+)?([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"time",            "(?i).*\\btor[çc]o\\s+(?:pro|pra|para\\s+o|para\\s+a)\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"musica favorita", "(?i).*\\bminha\\s+m[úu]sica favorita (?:é|eh|e)\\s+(?:a\\s+|o\\s+)?([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"pet",             "(?i).*\\bmeu (?:pet|cachorro|gato|cão|cadela|gata|bicho) (?:se\\s+)?chama(?:-se)?\\s+(?:o\\s+|a\\s+)?([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"sonho",           "(?i).*\\bmeu sonho (?:é|eh|e)\\s+(?:o\\s+|a\\s+)?([\\p{L}\\s]+?)(?:[.!?]|$)"}
    };

    // §7.1 - modificadores de humor na fala crua (com ou sem acento)
    private static final String[][] PADROES_HUMOR = {
            {"+1", "(?i).*(?:voce|você|vc)\\s+(?:e|é|eh)\\s+(?:legal|show|incrivel|incrível|otimo|ótimo|otima|ótim[ao]|linda|maravilhosa).*"},
            {"+1", "(?i).*(?:te amo|amo (?:voce|você)|gosto de (?:voce|você)|adorei).*"},
            {"-1", "(?i).*(?:voce|você|vc)\\s+(?:e|é|eh)\\s+(?:chata|otario|otaria|otária|burro|burra|idiota|lixo|insuportavel|insuportável|irritante).*"},
            {"-1", "(?i).*(?:odeio (?:voce|você)|seu lixo|seu idiota|me arrependo de te conhecer).*"}
    };

    private static final String NOME_PURO =
            "(?i)^(?!(?:oi|ola|eai|lol|kkk|nao|não|n|sei|bem|obrigad[oa]|la|executar"
                    + "|tudo|esquece|bom|boa|dia|tarde|noite|haha|rsrs|risos|show|legal"
                    + "|beleza|tranquilo|nada)\\b"
                    + "|(tudo bem|td bem|nao sei|não sei|quem eu sou|de nada)\\s*$)"
                    + "[\\p{L}]{2,}(?:\\s+[\\p{L}]{2,}){0,2}$";

    // §7.2 - perguntas de interesse genericas; o topico atual com
    // followups no banco tem prioridade
    private static final List<String> PERGUNTAS_INTERESSE = List.of(
            "E voce, o que andou fazendo hoje?",
            "Assiste algo bom ultimamente?",
            "Tem algo que te fez sorrir hoje?",
            "O que tem te ocupado nos ultimos dias?",
            "Conta uma novidade pra mim, {nome}?",
            "Tem algo que voce queria ter feito e nao fez ainda?");

    // #51 - pergunta disfarcada de declaracao: nao vira fato
    private static final Pattern PERGUNTA_DISFARCADA = Pattern.compile(
            "(?i).*(?:\\?|\\b(?:qual|quais|quem|quando|onde|como|quanto|quantos|mesmo)\\b).*");

    // #58 - negativos de gosto (o negativo NAO pode cair no positivo)
    private static final Pattern NEGATIVA_FATO = Pattern.compile(
            "(?i).*(?:\\b(?:nao|nunca)\\s+(?:gosto|curto)\\b|\\b(?:odeio|detesto)\\b"
                    + "|favorit[oa]\\s+(?:nao|nunca)\\s+(?:é|eh|e)\\b).*");

    private static final String[][] PADROES_NAO_GOSTA = {
            {"gosta de", "(?i).*\\b(?:nao|nunca)\\s+gosto de\\s+([\\p{L}][\\p{L}\\s]*?)(?:[.!?]|$)"},
            {"gosta de", "(?i).*\\b(?:nao|nunca)\\s+curto\\s+(?:o\\s+|a\\s+)?([\\p{L}][\\p{L}\\s]*?)(?:[.!?]|$)"},
            {"gosta de", "(?i).*\\b(?:odeio|detesto)\\s+(?:o\\s+|a\\s+)?([\\p{L}][\\p{L}\\s]*?)(?:[.!?]|$)"},
            {"meu X favorito NAO e Y",
                    "(?i).*\\bmeu\\s+([\\p{L}\\s]+?)\\s+favorit[oa]\\s+"
                            + "(?:nao|nunca)\\s+(?:é|eh|e)\\s+([\\p{L}][\\p{L}\\s]*?)(?:[.!?]|$)"},
            {"minha X favorita NAO e Y",
                    "(?i).*\\bminha\\s+([\\p{L}\\s]+?)\\s+favorit[oa]\\s+"
                            + "(?:nao|nunca)\\s+(?:é|eh|e)\\s+([\\p{L}][\\p{L}\\s]*?)(?:[.!?]|$)"},
    };

    // #57 - fluxo de opcoes: a chave esperando a lista
    private static String chaveOpcoes = null;
    private static int falhasOpcoes = 0;

    public static void armaOpcoes(String chave) {
        chaveOpcoes = chave;
        falhasOpcoes = 0;
    }

    public static String getChaveOpcoes() {
        return chaveOpcoes;
    }

    public static void limpaOpcoes() {
        chaveOpcoes = null;
        falhasOpcoes = 0;
    }

    /** #57 - conta mais uma falha de lista; a 2a cancela o fluxo. */
    public static int contaFalhaOpcoes() {
        return ++falhasOpcoes;
    }

    public static Sessao get() {
        return sessao;
    }


    /** #41 - arma a pergunta que ela acabou de fazer (familia + texto). */
    public static String armaPergunta(String familia, String texto) {
        familiaPergunta = familia;
        perguntaPendente = texto;
        Registro.linha("PENDENTE : familia=" + familia);
        return texto;
    }

    public static String getFamiliaPergunta() {
        return familiaPergunta;
    }

    public static String getPerguntaPendente() {
        return perguntaPendente;
    }

    public static void limpaPerguntaPendente() {
        familiaPergunta = null;
        perguntaPendente = null;
    }

    public static void reset() {
        sessao = new Sessao();
        perguntouNome = false;
        nomeCapturado = false;
        turnosDesdePergunta = 0;
        turnoDeContinuacao = false;
        turnoDeExplicacao = false;
        ultimoInteresse = null;
        apostaPendente = null;
        limpaPerguntaPendente();
        limpaOpcoes();
    }

    public static void processa(String falaUsuario) {
        if (falaUsuario == null || falaUsuario.trim().isEmpty())
            return;

        nomeCapturado = false;
        // #28 - a flag do pedido vive so o turno atual
        turnoDeContinuacao = false;
        turnoDeExplicacao = false;   // #54 - a explicacao tambem vive so o turno
        turnosDesdePergunta++;
        detectaNome(falaUsuario);
        detectaFato(falaUsuario);
        detectaHumor(falaUsuario);

        sessao.anota(new Turno("usuario", falaUsuario.trim()));
        sessao.setTurnos(sessao.getTurnos() + 1);
    }

    public static void topico(String intencao) {
        if (intencao != null && !intencao.isBlank()) {
            sessao.setTopicoAtual(intencao);
            Registro.linha("SESSAO    : topico=" + intencao);
        }
    }

    /** #28 - o usuario pediu pra ela falar; perguntaInteresse segura o pool. */
    public static void pedeContinuacao() {
        turnoDeContinuacao = true;
    }

    /** #54 - o turno pediu profundidade; o dicionario responde o texto. */
    public static void pedeExplicacao() {
        turnoDeExplicacao = true;
    }

    public static boolean explicacaoPedida() {
        return turnoDeExplicacao;
    }

    // #39 - aposta pendente: o candidato que a aposta propôs fica guardado no
    // turno seguinte - confirmacao entrega, continuacao segue, match novo descarta
    private static String apostaPendente = null;

    public static void apostaPendente(String intento) {
        apostaPendente = intento;
    }

    public static String getApostaPendente() {
        return apostaPendente;
    }

    /** #39 - pega e limpa: consumiu a pendencia no turno. */
    public static String consomeApostaPendente() {
        String pendente = apostaPendente;
        apostaPendente = null;
        return pendente;
    }

    // #39 - confirmacao explicita: "isso", "e isso", "confirma"... frases curtas
    // que so confirmam; qualquer outra deixa a pendencia quieta
    private static final java.util.List<String> CONFIRMACOES =
            java.util.List.of("isso", "e isso", "isso mesmo", "confirma", "confirma?",
                    "com certeza", "certo", "isso ai", "exato", "eh isso", "isso aí");

    /** #39 - a frase normalizada e uma confirmacao da aposta pendente? */
    public static boolean ehConfirmacao(String falaNormalizada) {
        if (falaNormalizada == null)
            return false;
        String limpa = falaNormalizada.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[!?.,]", "").trim();
        return CONFIRMACOES.contains(limpa);
    }

    /** #39 - confirmou: a pendencia vira topico de verdade e sai da fila. */
    public static String confirmaApostaPendente() {
        String pendente = consomeApostaPendente();
        if (pendente != null)
            topico(pendente);
        return pendente;
    }

    public static String perguntaDeNome() {
        if (sessao.getNomeUsuario() != null || perguntouNome)
            return null;
        perguntouNome = true;
        turnosDesdePergunta = 0;
        Registro.linha("SESSAO    : perguntou o nome");
        return "Como posso te chamar?";
    }

    public static void anotaResposta(String texto) {
        if (texto == null)
            return;
        sessao.anota(new Turno("kaira", texto));
    }

    public static void gravaSessao() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            Path arquivo = Caminhos.base().resolve(ARQUIVO_SESSAO);
            mapper.writerWithDefaultPrettyPrinter().writeValue(arquivo.toFile(), sessao);
            Registro.linha("SESSAO    : gravada em " + arquivo.getFileName());
        } catch (Exception e) {
            Registro.linha("SESSAO    : falha ao gravar sessao.json (" + e.getMessage() + ")");
        }
    }

    public static boolean carregaSessao() {
        Path arquivo = Caminhos.base().resolve(ARQUIVO_SESSAO);
        if (!Files.exists(arquivo))
            return false;
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

            // #58 - migracao no load: o formato plano vira relacoes em memoria
            JsonNode raiz = mapper.readTree(arquivo.toFile());
            ObjectNode fatos =
                    (ObjectNode) raiz.get("fatos");
            if (fatos != null) {
                java.util.List<String> planas = new java.util.ArrayList<>();
                fatos.fieldNames().forEachRemaining(n -> {
                    if (fatos.get(n).isTextual())
                        planas.add(n);
                });
                for (String n : planas) {
                    ObjectNode f = mapper.createObjectNode();
                    f.putArray("gosta").add(fatos.get(n).asText());
                    fatos.set(n, f);
                }
            }

            Sessao restaurada = mapper.treeToValue(raiz, Sessao.class);
            if (restaurada == null)
                return false;
            sessao = restaurada;
            while (sessao.getHistorico().size() > Sessao.MAX_HISTORICO)
                sessao.getHistorico().remove(0);
            Registro.linha("SESSAO    : carregada de sessao.json (nome=" + sessao.getNomeUsuario() + ")");
            return true;
        } catch (Exception e) {
            reset();
            Registro.linha("SESSAO    : sessao.json corrompido, sessao nova");
            return false;
        }
    }

    public static String boasVindas() {
        if (sessao.getNomeUsuario() == null)
            return null;
        String mensagem = "Bem-vindo de volta, " + sessao.getNomeUsuario() + "!";
        if (sessao.getTopicoAtual() != null && !sessao.getTopicoAtual().isBlank())
            mensagem += " A gente tava falando sobre " + sessao.getTopicoAtual() + ".";
        return mensagem;
    }

    private static void detectaNome(String fala) {
        String t = fala.trim();
        for (Pattern p : PADROES_NOME) {
            Matcher m = p.matcher(t);
            if (m.find()) {
                String candidato = m.group(1).trim();
                // #63 - profissao nao e' nome
                if (candidato.matches(PROFISSOES)) {
                    Registro.linha("SESSAO    : nome ignorado (profissao: " + candidato + ")");
                    return;
                }
                sessao.setNomeUsuario(candidato);
                nomeCapturado = true;
                Registro.linha("SESSAO    : nome=" + sessao.getNomeUsuario());
                return;
            }
        }
        // #24 - nome pelado: so dentro da janela da pergunta, fora da lista
        if (perguntouNome && sessao.getNomeUsuario() == null && t.matches(NOME_PURO)) {
            sessao.setNomeUsuario(t);
            nomeCapturado = true;
            Registro.linha("SESSAO    : nome=" + sessao.getNomeUsuario() + " (pelado)");
        }
    }

    private static void detectaFato(String fala) {
        // #51 - integridade: pergunta vestida de declaracao nao grava nada
        if (PERGUNTA_DISFARCADA.matcher(fala.trim()).matches())
            return;
        // #58 - negativo tem caminho proprio (nao cai no positivo)
        if (NEGATIVA_FATO.matcher(fala.trim()).matches()) {
            for (String[] regra : PADROES_NAO_GOSTA) {
                Matcher m = Pattern.compile(regra[1]).matcher(fala.trim());
                if (!m.find())
                    continue;
                if (m.groupCount() >= 2) {
                    String sufixo = regra[0].startsWith("minha") ? " favorita" : " favorito";
                    guardaFato(m.group(1).trim() + sufixo, m.group(2).trim(), false);
                } else {
                    guardaFato(regra[0], m.group(1).trim(), false);
                }
            }
            return;
        }
        for (String[] regra : PADROES_FATO) {
            Matcher m = Pattern.compile(regra[1]).matcher(fala.trim());
            if (!m.find())
                continue;
            if (m.groupCount() >= 2) {
                // "meu jogo favorito e minecraft" -> "jogo favorito"
                // #52: "minha comida favorita e lasanha" -> "comida favorita"
                String sufixo = regra[0].startsWith("minha") ? " favorita" : " favorito";
                guardaFato(m.group(1).trim() + sufixo, m.group(2).trim(), true);
            } else {
                guardaFato(regra[0], m.group(1).trim(), true);
            }
        }
    }

    /** #51 - escrita central do fato: valor-pergunta nao grava e fato
     *  existente nao e sobrescrito por valor diferente (log de preservacao).
     *  Mesmo valor (caixa ignorada) = no-op com o canonico preservado. */
    /** #58 - escrita central: relacao, segredo e nao-contradicao. */
    private static void guardaFato(String chave, String valor, boolean gosta) {
        if (chave == null || chave.isBlank() || valor == null || valor.isBlank())
            return;
        if (PERGUNTA_DISFARCADA.matcher(valor).matches())
            return;
        if (ControleKaira.ehSegredo(chave + " " + valor)) {
            Registro.linha("SESSAO    : fato " + chave + " ignorado (segredo)");
            return;
        }
        com.JCODE.domain.FatoSessao fato = sessao.getFatosV2().get(chave);
        if (fato == null) {
            fato = new com.JCODE.domain.FatoSessao();
            sessao.getFatosV2().put(chave, fato);
        }
        java.util.List<String> alvo = gosta ? fato.getGosta() : fato.getNaoGosta();
        java.util.List<String> oposta = gosta ? fato.getNaoGosta() : fato.getGosta();
        String norm = compara(valor);
        for (String v : oposta)
            if (compara(v).equals(norm)) {
                Registro.linha("SESSAO    : fato " + chave + " recusado (contradicao)");
                return;
            }
        if (!alvo.isEmpty()) {
            for (String v : alvo)
                if (compara(v).equals(norm))
                    return;
            Registro.linha("SESSAO    : fato " + chave + " preservado (ja tem "
                    + alvo.get(0) + "; ignorado=" + valor + ")");
            return;
        }
        alvo.add(valor);
        Registro.linha("SESSAO    : fato " + chave + (gosta ? "=" : " nao_gosta=") + valor);
    }

    private static String compara(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** §7.1 - modificadores: elogio sobe, grosseria baixa (clamp no setter). */
    private static void detectaHumor(String fala) {
        for (String[] regra : PADROES_HUMOR) {
            if (!Pattern.compile(regra[1]).matcher(fala.trim()).find())
                continue;
            int delta = Integer.parseInt(regra[0]);
            int antes = sessao.getHumor();
            sessao.setHumor(antes + delta);
            Registro.linha("SESSAO    : humor " + antes + "->"
                    + sessao.getHumor() + " (" + regra[0] + ")");
            return;
        }
    }

    public static String tiraNome(String fala) {
        if (!nomeCapturado)
            return fala;
        String t = fala.trim();
        for (Pattern p : PADROES_NOME) {
            Matcher m = p.matcher(t);
            if (m.find()) {
                String sem = t.substring(0, m.start()) + " " + t.substring(m.end());
                return limpaConectivos(sem.trim());
            }
        }
        return "";
    }

    public static String respostaDeNome(String fala) {
        if (!nomeCapturado)
            return null;
        if (!tiraNome(fala).trim().isEmpty())
            return null;
        return "Prazer em conhecer voce, " + sessao.getNomeUsuario() + "!";
    }

    private static String limpaConectivos(String s) {
        s = s.replaceAll("^(?:e\\b\\s*|que\\s+|[,;.])\\s*", "");
        s = s.replaceAll("\\s+(?:e|que)\\b\\s*$", "");
        s = s.replaceAll("[,;]\\s*$", "");
        return s.trim();
    }

    /** §7.2 - iniciativa propria: pergunta de interesse a cada N turnos. */
    public static String perguntaInteresse() {
        if (turnosDesdePergunta < TURNOS_SEM_PERGUNTA)
            return null;
        turnosDesdePergunta = 0;

        // fonte 1 - followups da intencao atual
        String topico = sessao.getTopicoAtual();
        if (topico != null && !topico.isBlank()) {
            for (Intencao i : ControleResposta.getIntencoes()) {
                if (topico.equals(i.getIntento()) && i.getFollowups() != null
                        && !i.getFollowups().isEmpty()) {
                    Registro.linha("SESSAO    : followup de " + topico);
                    return armaPergunta(topico, sorteia(i.getFollowups()));
                }
            }
        }

        // #28 - no turno de continuacao a fonte 2 (pool) fica de fora:
        // ela fica com o turno - a fonte 1 do topico ja passou la em cima
        if (turnoDeContinuacao) {
            Registro.linha("SESSAO    : interesse segurado (turno de continuacao)");
            return null;
        }

        // fonte 2 - pool generico
        Registro.linha("SESSAO    : pergunta de interesse");
        return armaPergunta(null, sorteia(PERGUNTAS_INTERESSE));
    }

    /** Anti-repeticao no estilo do ultimoFallback (issue #17). */
    private static String sorteia(List<String> opcoes) {
        List<String> livres = new ArrayList<>(opcoes);
        if (ultimoInteresse != null)
            livres.remove(ultimoInteresse);
        if (livres.isEmpty())
            livres = new ArrayList<>(opcoes);
        String escolhida = livres.get((int) (Math.random() * livres.size()));
        ultimoInteresse = escolhida;
        return escolhida;
    }

}
