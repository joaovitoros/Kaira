package com.JCODE.util;

import java.text.Normalizer;
import java.util.Set;

public class Normalizador {

    public static String texto(String texto){

        if(texto==null)
            return "";

        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    public static String stem(String palavra){

        String original = texto(palavra);
        String radical = original;

        if(radical.endsWith("coes"))                                            // 1: troca (coes -> cao)
            radical = radical.substring(0, radical.length() - 4) + "cao";
        else if(radical.endsWith("mente"))                                      // 2
            radical = radical.substring(0, radical.length() - 5);
        else if(radical.endsWith("ando") || radical.endsWith("endo") || radical.endsWith("indo"))   // 3: gerundio
            radical = radical.substring(0, radical.length() - 4);
        else if(radical.endsWith("ei"))                                         // 4
            radical = radical.substring(0, radical.length() - 2);
        else if(radical.endsWith("ou"))                                         // 5
            radical = radical.substring(0, radical.length() - 2);
        else if(radical.endsWith("amos") || radical.endsWith("emos") || radical.endsWith("imos"))   // 6
            radical = radical.substring(0, radical.length() - 4);
        else if(radical.endsWith("istas"))                                      // 7 (plural antes do singular)
            radical = radical.substring(0, radical.length() - 5);
        else if(radical.endsWith("ista"))                                       // 7
            radical = radical.substring(0, radical.length() - 4);
        else if(radical.endsWith("cao"))                                        // 8
            radical = radical.substring(0, radical.length() - 3);
        else if(radical.endsWith("eza"))                                        // 9
            radical = radical.substring(0, radical.length() - 3);
        else if(radical.endsWith("anca"))                                       // 10
            radical = radical.substring(0, radical.length() - 4);
        else if(radical.endsWith("ar") || radical.endsWith("er") || radical.endsWith("ir"))         // 11: infinitivo
            radical = radical.substring(0, radical.length() - 2);
        else if(radical.endsWith("s"))                                          // 12: plural, sempre por ultimo
            radical = radical.substring(0, radical.length() - 1);

        return radical.length() < 3 ? original : radical;
    }

    // #39/#40 - radical de palavra de conteudo que sobreviveu ao corte, mas que
    // em isolation nao resolve match sozinho (ex.: "quem" de "quem e voce" quando
    // voce/e sao stopwords). Raizes PROIBIDOS: nao-vazio, nao-stopword, >= 3 chars.
    // Um radical desta lista NUNCA vira fallback - sinal de que sobrou conteudo real.
    private static final Set<String> RAIZES_GENERICAS_PROIBIDAS = Set.of(
            "voce", "coisa", "assunto", "preferido", "favorito",
            "sobre", "gost", "quer", "fal", "diz", "cont", "memsm", "outr");

    // #39/#40 - radical de conteudo que pode virar o PISO quando a frase inteira
    // e so stopword. "voce" e barrado pela RAIZES_GENERICAS_PROIBIDAS; "quem"
    // sobrevive de proposito (e a unica palavra de "quem e voce" que casa o
    // identidade - score 1.00 nos 2 exemplos 100% stopword da base).
    public static boolean ehRadicalDeConteudo(String radical) {
        if (radical == null || radical.length() < 3)
            return false;
        return !RAIZES_GENERICAS_PROIBIDAS.contains(radical);
    }

}
