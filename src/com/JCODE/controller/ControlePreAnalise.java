package com.JCODE.controller;

import com.JCODE.domain.FrameSlot;
import com.JCODE.util.Normalizador;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ControlePreAnalise {

    private static final String[][] FRAMES = {
            {"preferencia", "qual .* favorit"},
            {"gosto",        "gosta de"},
            {"opiniao",      "acha d"},
            {"cumprimento",  "bom dia|boa tarde|boa noite"},
            {"traducao",     "como se diz|traduz"},
            {"receita",      "como (se )?faz"},
            {"hora",         "que horas"},
            {"clima",        "que tempo faz|tempo hoje"},
            {"localizacao",  "onde fica"},
            {"definicao",    "o que e|que significa"},
            {"idade",        "quantos anos"}
    };

    private static final Set<String> RUBRICAS = Set.of(
            "qual", "seu", "sua", "seus", "suas", "favorito", "favorita",
            "gosta", "de", "acha", "do", "da", "o", "que", "voce", "vc",
            "bom", "boa", "dia", "tarde", "noite",
            "eu", "tu", "meu", "minha", "teu", "tua", "nosso", "nossa",
            "como", "se", "faz", "fazer", "diz", "traduz", "horas", "tempo",
            "hoje", "onde", "fica", "significa", "quantos", "anos");

    public static FrameSlot preAnalise(String frase) {
        String texto = Normalizador.texto(frase);

        String frame = null;
        for (String[] f : FRAMES) {
            if (Pattern.compile(f[1]).matcher(texto).find()) {
                frame = f[0];
                break;
            }
        }

        String slot = Arrays.stream(texto.split(" "))
                .filter(p -> !p.isEmpty())
                .filter(p -> !RUBRICAS.contains(p))
                .filter(p -> ControleResposta.stopwords == null
                        || !ControleResposta.stopwords.contains(Normalizador.stem(p)))
                .collect(Collectors.joining(" "));

        return new FrameSlot(frame, slot);
    }
}