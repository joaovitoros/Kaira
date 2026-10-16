package com.JCODE.controller;

import com.JCODE.domain.Sessao;
import com.JCODE.domain.Turno;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Registro;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ControleSessao {

    private static Sessao sessao = new Sessao();
    private static boolean perguntouNome = false;

    private static final String ARQUIVO_SESSAO = "sessao.json";

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