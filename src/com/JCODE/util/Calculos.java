package com.JCODE.util;

public class Calculos {

    public static Double calculoPorcentagemFrase(String fraseUsuario, String fraseArmazenada, Double porcentagemAtual){

        int acertos = 0;

        String[] palavrasUsuario = fraseUsuario.split(" ");

        for(String palavraArmazenada : fraseArmazenada.split(" ")){
            for(String palavraUsuario : palavrasUsuario){
                if(palavraUsuario.equals(palavraArmazenada)){
                    acertos++;
                    break;
                }
            }
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

    public static int levenshtein(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int custo = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                d[i][j] = Math.min(
                        Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + custo);
            }
        }
        return d[a.length()][b.length()];
    }

}
