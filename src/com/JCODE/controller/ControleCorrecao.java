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

    public static String procuraSinonimo(String frase) {
        String novafrase = "", compara;
        List<String> fraseCorridaList = new ArrayList<>();
        String[] fraseseparada = frase.split(" ");
        for (int i = 0; i < fraseseparada.length; i++) {
            compara = fraseseparada[i];
            fraseCorridaList.add(ControleSinonimo.procuraSinonimo(compara));
        }

        for(String palavra: fraseCorridaList){
            novafrase = novafrase+" "+palavra;
        }

        System.out.println("Resultado busca de sinonimos: "+novafrase);

        return ControleResposta.lerResposta(novafrase.trim());
    }
}
