package com.JCODE.controller;

import java.util.ArrayList;
import java.util.List;

public class ControleCorrecao {

    public static String corrigirPalavras(String frase){
        String[] fraseSeparada = frase.split(" ");
        List<String> fraseCorridaList = new ArrayList<>();
        String compara, frasefinal = "";
        for (int i = 0; i < fraseSeparada.length; i++) {
            compara = fraseSeparada[i];
            if(ControlePalavra.existsPalavras(compara)){
                fraseCorridaList.add(compara);
            }else{
                fraseCorridaList.add(ControlePalavra.correcaoPalavra(compara));
            }
        }

        for(var fraseCorrida: fraseCorridaList){
            frasefinal = frasefinal+" "+fraseCorrida;
        }

        System.out.println("Resultado detecção de erros: "+frasefinal);

        return ControleResposta.lerResposta(frasefinal.trim());
    }


    public static String corrigirFrases(String falaUsuario) {

        String frasecorrigida;
        frasecorrigida = ControleResposta.correcaoFrases(falaUsuario);
        System.out.println("Resultado comparação de frases: "+frasecorrigida);
        return ControleResposta.lerResposta(frasecorrigida.trim());

    }

}
