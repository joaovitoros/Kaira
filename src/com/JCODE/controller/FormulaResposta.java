package com.JCODE.controller;

import com.JCODE.util.Normalizador;

public class FormulaResposta {

    static String respostaVariavel;

    static String bufferFrases;

    public static void decifrar(String falaUsuario) {

        falaUsuario = Normalizador.texto(falaUsuario);

        System.out.println("texto recebido pós replaces "+falaUsuario);

        if(!ControleComandos.validaComandos(falaUsuario)){
            respostaVariavel = ControleResposta.lerResposta(falaUsuario);

            if(respostaVariavel==null){
                respostaVariavel = ControleCorrecao.corrigirPalavras(falaUsuario);
            }

            if(respostaVariavel==null){
                respostaVariavel = ControleCorrecao.corrigirFrases(falaUsuario);
            }

            if(respostaVariavel==null){
                respostaVariavel = ControleResposta.respostaAposta(falaUsuario);
            }

            criacaoLeituraRespostas(falaUsuario);
        }

    }

    public static void criacaoLeituraRespostas(String falaUsuario){
        int decisao = (int) (Math.random()* (4))+1;
        System.out.println("Fator geração resposta nova "+ decisao);
        if(decisao>=2){
            ControleResposta.CriarResposta(respostaVariavel);
        }

        enviarResposta(respostaVariavel, falaUsuario);


    }

    public static void enviarResposta(String resposta, String falaUsuario) {

        if(resposta==null) {
            ativarAprendizado();
            bufferFrases=falaUsuario;
            ControleFace.recebeResp("Não sei como responder a isso");
        }else{
            ControleFace.recebeResp(resposta);
        }
    }

    public static void ativarAprendizado(){
        ControleFace.mudarVerificar();
    }

    public static void manterResposta(String resposta){
        ControleResposta.chamadaFrase(bufferFrases, resposta);
        ControleFace.recebeResp(resposta);
    }


}
