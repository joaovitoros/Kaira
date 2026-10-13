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

}
