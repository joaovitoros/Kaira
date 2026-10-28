package com.JCODE.controller;

import java.util.Locale;

/** #62 - G6: o comando "Executar" foi neutralizado - nada roda no PC do
 *  usuario. O gatilho e' o INICIO da frase; a recusa e' curada e o turno
 *  e' consumido (nao cai no fallback/ensino). */
public class ControleComandos {

    public static boolean validaComandos(String frase) {
        if (frase == null || frase.isBlank())
            return false;
        if (frase.trim().toLowerCase(Locale.ROOT).startsWith("executar")) {
            FormulaResposta.enviarResposta(
                    "Essa eu nao faco, nao - executar programa no seu PC e' responsabilidade sua!",
                    frase);
            return true;
        }
        return false;
    }
}