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

    // Damerau-Levenshtein restrito (OSA): alem do levenshtein, a troca de duas
    // letras VIZINHAS custa 1 em vez de 2. Foi o que tirou o "fimle" (log de
    // 17/10) da dependencia da ordem do arquivo: com levenshtein puro o swap
    // m/l custava 2 e empatava com fale/fim/fique/fome - CorrecaoOrdemArquivoTest.
    // NAO e anagrama livre: lista/sltia (Ap. B.2) continua 4 e fora do limiar
    // (CalculosDamerauTest) - anagrama inteiro casaria palavra de letras iguais
    // como "identica" e criaria correcao falsa.
    public static int damerau(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int custo = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                d[i][j] = Math.min(
                        Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + custo);
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

}
