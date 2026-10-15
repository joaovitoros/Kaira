package com.JCODE.util;

import java.text.Normalizer;

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

}
