package com.JCODE.controller;

import com.JCODE.domain.Intencao;
import com.JCODE.domain.Sessao;
import com.JCODE.domain.Turno;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Registro;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final String ARQUIVO_SESSAO = "sessao.json";

    private static int turnosDesdePergunta = 0;
    private static String ultimoInteresse = null;

    private static final int TURNOS_SEM_PERGUNTA = 3;

    private static final Pattern[] PADROES_NOME = {
            Pattern.compile("(?i)meu nome (?:é|eh|e)\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){0,2}?)(?=\\s*(?:$|[.!?])|\\s*,|\\s+(?:e|que|com|pra|para|vou|quero)\\b)"),
            Pattern.compile("(?i)me chamo\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){0,2}?)(?=\\s*(?:$|[.!?])|\\s*,|\\s+(?:e|que|com|pra|para|vou|quero)\\b)"),
            Pattern.compile("(?i)pode me chamar de\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){0,2}?)(?=\\s*(?:$|[.!?])|\\s*,|\\s+(?:e|que|com|pra|para|vou|quero)\\b)"),
            Pattern.compile("(?i)^(?:eu\\s+)?sou\\s+(?:o\\s+|a\\s+)?([\\p{L}]+(?:\\s+[\\p{L}]+){0,2}?)(?=\\s*(?:$|[.!?])|\\s*,|\\s+(?:e|que|com|pra|para|vou|quero)\\b)")
    };

    private static final String[][] PADROES_FATO = {
            {"gosta de",     "(?i).*\\bgosto de\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"mora em",      "(?i).*\\bmor[oa]\\s+em\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"trabalha com", "(?i).*\\btrabalh[oa]\\s+com\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"nasceu em",    "(?i).*\\bnasci\\s+em\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"meu X favorito e Y",
             "(?i).*\\bmeu\\s+([\\p{L}\\s]+?)\\s+favorit[oa]\\s+(?:é|eh|e)\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"}
    };

    // §7.1 - modificadores de humor na fala crua (com ou sem acento)
    private static final String[][] PADROES_HUMOR = {
            {"+1", "(?i).*(?:voce|você|vc)\\s+(?:e|é|eh)\\s+(?:legal|show|incrivel|incrível|otimo|ótimo|otima|ótim[ao]|linda|maravilhosa).*"},
            {"+1", "(?i).*(?:te amo|amo (?:voce|você)|gosto de (?:voce|você)|adorei).*"},
            {"-1", "(?i).*(?:voce|você|vc)\\s+(?:e|é|eh)\\s+(?:chata|otario|otaria|otária|burro|burra|idiota|lixo|insuportavel|insuportável|irritante).*"},
            {"-1", "(?i).*(?:odeio (?:voce|você)|seu lixo|seu idiota|me arrependo de te conhecer).*"}
    };

    private static final String NOME_PURO =
            "(?i)^(?!(?:oi|ola|eai|lol|kkk|nao|não|n|sei|bem|obrigad[oa]|la|executar)\\b"
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

    public static Sessao get() {
        return sessao;
    }

    public static void reset() {
        sessao = new Sessao();
        perguntouNome = false;
        nomeCapturado = false;
        turnosDesdePergunta = 0;
        ultimoInteresse = null;
    }

    public static void processa(String falaUsuario) {
        if (falaUsuario == null || falaUsuario.trim().isEmpty())
            return;

        nomeCapturado = false;
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
            Sessao restaurada = mapper.readValue(arquivo.toFile(), Sessao.class);
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
                sessao.setNomeUsuario(m.group(1).trim());
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
        for (String[] regra : PADROES_FATO) {
            Matcher m = Pattern.compile(regra[1]).matcher(fala.trim());
            if (!m.find())
                continue;
            if (m.groupCount() >= 2) {
                // "meu jogo favorito e minecraft" -> chave "jogo favorito"
                sessao.getFatos().put(m.group(1).trim() + " favorito", m.group(2).trim());
                Registro.linha("SESSAO    : fato " + m.group(1).trim() + " favorito=" + m.group(2).trim());
            } else {
                sessao.getFatos().put(regra[0], m.group(1).trim());
                Registro.linha("SESSAO    : fato " + regra[0] + "=" + m.group(1).trim());
            }
        }
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
                    return sorteia(i.getFollowups());
                }
            }
        }

        // fonte 2 - pool generico
        Registro.linha("SESSAO    : pergunta de interesse");
        return sorteia(PERGUNTAS_INTERESSE);
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
