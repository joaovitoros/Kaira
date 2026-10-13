package com.JCODE.util;

public class Calculos {

    public static double calculoPorcentagemPalavra(String compara, String palavra){
        int acerto = 0;
        double porcentagem = 0;
        for (int i = 0; i < palavra.length(); i++) {
            try {
                if (compara.equalsIgnoreCase(palavra)) {
                    return 100;
                }else if (palavra.charAt(i)==compara.charAt(i)) {
                    acerto++;
                }
            } catch (Exception e) {
            }
            //calculo da porcentagem que determinara a substituição ou não da palavra digitada pela armazenada
            porcentagem = calculoPorcentagem(acerto, palavra.length());
        }
        return porcentagem;
    }

    public static Double calculoPorcentagemFrase(String fraseUsuario, String fraseArmazenada, Double porcentagemAtual){

        int acertos = 0;

        for(String fraseArmazenadaSeparada : fraseArmazenada.split(" ")){
            if(fraseUsuario.contains(fraseArmazenadaSeparada))
                acertos++;
        }

        Double percAux = Calculos.calculoPorcentagem(acertos, fraseArmazenada.split(" ").length);

        if(porcentagemAtual!=null)
            return (porcentagemAtual+percAux)/2;
        else
            return percAux;

    }

    public static double calculoPorcentagem(int acerto, int quantidade){
        return acerto*100/quantidade;
    }

}
