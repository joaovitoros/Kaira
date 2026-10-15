package com.JCODE.controller;

import com.JCODE.domain.Sessao;
import com.JCODE.domain.Turno;
import com.JCODE.util.Registro;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ControleSessao {

    private static Sessao sessao = new Sessao();
    private static boolean perguntouNome = false;

    private static final Pattern[] PADROES_NOME = {
            Pattern.compile("(?i)meu nome (?:é|eh|e)\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){0,2})"),
            Pattern.compile("(?i)me chamo\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){0,2})"),
            Pattern.compile("(?i)pode me chamar de\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){0,2})"),
            Pattern.compile("(?i)^(?:eu\\s+)?sou\\s+(?:o\\s+|a\\s+)?([\\p{L}]+(?:\\s+[\\p{L}]+){0,2})")
    };

    private static final String[][] PADROES_FATO = {
            {"gosta de",     "(?i).*\\bgosto de\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"mora em",      "(?i).*\\bmor[oa]\\s+em\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"trabalha com", "(?i).*\\btrabalh[oa]\\s+com\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"nasceu em",    "(?i).*\\bnasci\\s+em\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"},
            {"meu X favorito e Y",
             "(?i).*\\bmeu\\s+([\\p{L}\\s]+?)\\s+favorit[oa]\\s+(?:é|eh|e)\\s+([\\p{L}\\s]+?)(?:[.!?]|$)"}
    };

    public static Sessao get() {
        return sessao;
    }

    public static void reset() {
        sessao = new Sessao();
        perguntouNome = false;
    }

    public static void processa(String falaUsuario) {
        if (falaUsuario == null || falaUsuario.trim().isEmpty())
            return;

        detectaNome(falaUsuario);
        detectaFato(falaUsuario);

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
        Registro.linha("SESSAO    : perguntou o nome");
        return "Como posso te chamar?";
    }

    public static void anotaResposta(String texto) {
        if (texto == null)
            return;
        sessao.anota(new Turno("kaira", texto));
    }

    private static void detectaNome(String fala) {
        for (Pattern p : PADROES_NOME) {
            Matcher m = p.matcher(fala.trim());
            if (m.find()) {
                sessao.setNomeUsuario(m.group(1).trim());
                Registro.linha("SESSAO    : nome=" + sessao.getNomeUsuario());
                return;
            }
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
}