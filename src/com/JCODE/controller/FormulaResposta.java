package com.JCODE.controller;

import com.JCODE.util.Normalizador;
import com.JCODE.util.Registro;

public class FormulaResposta {

    static String respostaVariavel;

    static String bufferFrases;

    public static void decifrar(String falaUsuario) {

        Registro.linha("ENTRADA   : " + falaUsuario);

        ControleSessao.processa(falaUsuario);

        // #24: a resposta ao pedido de nome nao passa pelo classificador
        String agradecimento = ControleSessao.respostaDeNome(falaUsuario);
        if (agradecimento != null) {
            Registro.linha("DEGRAU    : resposta-nome");
            enviarResposta(agradecimento, falaUsuario);
            return;
        }
        falaUsuario = ControleSessao.tiraNome(falaUsuario);
        falaUsuario = Normalizador.texto(falaUsuario);

        System.out.println("texto recebido pós replaces "+falaUsuario);

        if(!ControleComandos.validaComandos(falaUsuario)){
            String degrau = "aprender";
            respostaVariavel = ControleResposta.lerResposta(falaUsuario);

            if(respostaVariavel!=null)
                degrau = "direto";

            if(respostaVariavel==null){
                respostaVariavel = ControleCorrecao.corrigirPalavras(falaUsuario);
                if(respostaVariavel!=null)
                    degrau = "corrigida-palavra";
            }

            if(respostaVariavel==null){
                respostaVariavel = ControleCorrecao.corrigirFrases(falaUsuario);
                if(respostaVariavel!=null)
                    degrau = "corrigida-frase";
            }

            if(respostaVariavel==null){
                respostaVariavel = ControleResposta.respostaAposta(falaUsuario);
                if(respostaVariavel!=null)
                    degrau = "aposta";
            }

            Registro.linha("DEGRAU    : " + degrau);
            criacaoLeituraRespostas(falaUsuario);
        }else{
            Registro.linha("DEGRAU    : comando");
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

        String texto;
        if(resposta==null) {
            ativarAprendizado();
            bufferFrases=falaUsuario;
            String slot = ControlePreAnalise.preAnalise(falaUsuario).getSlot();
            texto = ControleResposta.respostaFallback(slot);
            Registro.linha("APRENDENDO: " + falaUsuario + " (slot=" + slot + ")");
        }else{
            texto = resposta;
            // §7.2 - iniciativa propria: a cada N turnos, uma pergunta de interesse
            if (resposta != null) {
                String interesse = ControleSessao.perguntaInteresse();
                if (interesse != null)
                    texto = texto + " " + interesse;
            }
        }

        texto = ControleResposta.preencheSlots(texto);

        String chamada = ControleSessao.perguntaDeNome();
        if (chamada != null)
            texto = texto + " " + chamada;

        ControleSessao.anotaResposta(texto);
        Registro.linha("KAIRA     : " + texto);
        ControleFace.recebeResp(texto);
    }

    public static void ativarAprendizado(){
        ControleFace.mudarVerificar();
    }

    public static void manterResposta(String resposta){
        ControleResposta.chamadaFrase(bufferFrases, resposta);
        ControleFace.recebeResp(resposta);
    }


}
