package com.JCODE.controller;

import com.JCODE.util.Normalizador;
import com.JCODE.util.Registro;

public class FormulaResposta {

    static String respostaVariavel;

    static String bufferFrases;

    // #49 - aprendizado organico: P1 = buffer armado esperando a resposta;
    // P2 = resposta chegou e ficou RETIDA esperando o "sim" explicito
    static String respostaEnsino;

    // #49 - piso do porteiro (medido - ProbePiso: lixo 0.000, topical 0.192+)
    private static final double PISO = 0.10;

    // #49 - textos curados do ensino (autor aprovou)
    private static final java.util.List<String> FRASES_ABSORVIDO =
            java.util.List.of(
                    "Anotado: \"{fala}\" - obrigada, isso me ajuda a aprender!",
                    "Anotado: \"{fala}\" - guardei! Valeu por me ensinar.",
                    "Anotado: \"{fala}\" - aprendi mais uma, obrigada!");

    private static final String TEXTO_CONFIRMACAO =
            "To com dúvida nessa... me confirma: aprendo \"{fala}\"?";

    private static final String TEXTO_RECUSA = "Sem problema, descartei.";

    private static final String TEXTO_CANCELADO = "(deixa pra lá o que te perguntei antes!)";

    // #49 - vocabulario do ensino: sim/nao explicitos (o "sim" nao esta no
    // ehConfirmacao do #39 - medido: "sim" cai no degrau aprender)
    private static final java.util.List<String> CONFIRMACOES_ENSINO =
            java.util.List.of("sim", "claro", "pode ser");

    private static final java.util.List<String> NEGACOES_ENSINO =
            java.util.List.of("nao", "isso nao", "nao e isso", "nao eh isso",
                    "nah", "deixa isso");

    // #53 - recusa curada do ensino quando o par tem cara de segredo
    private static final String TEXTO_SEGREDO =
            "Essa eu prefiro nao anotar - segredo e segredo, viu? Fica so com voce.";

    // #49 - anti-repeticao do ack, no padrao ultimoFallback
    private static String ultimoAbsorvido = null;

    // #41 - confianca do #33 (0.6): acima disso a resposta ao followup vai
    // pro intento do match; abaixo (>= piso), pra familia que perguntou
    private static final double LIMIAR_CONFIANCA = 0.6;

    // #49 - limpa o estado do ensino (P1/P2) e desarma o brilho do botao
    private static void limpaEnsino() {
        bufferFrases = null;
        respostaEnsino = null;
        ControleFace.mudarNaoVerificar();
    }

    private static boolean ensinoPendente() {
        return bufferFrases != null;
    }

    private static boolean ehConfirmacaoEnsino(String falaNormalizada) {
        String limpa = falaNormalizada.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[!?.,]", "").trim();
        return ControleSessao.ehConfirmacao(falaNormalizada)
                || CONFIRMACOES_ENSINO.contains(limpa);
    }

    private static boolean ehNegacaoEnsino(String falaNormalizada) {
        String limpa = falaNormalizada.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[!?.,]", "").trim();
        return NEGACOES_ENSINO.contains(limpa);
    }

    // #49 - absorve o par (pergunta, resposta) e fecha o ensino com ack
    // #53 - par com cara de segredo nao e absorvido (P1 direto e P2 confirmado)
    private static void absorve(String resposta) {
        String pergunta = bufferFrases;
        limpaEnsino();
        if (ControleKaira.ehSegredo(pergunta) || ControleKaira.ehSegredo(resposta)) {
            Registro.linha("ENSINO    : par descartado (segredo)");
            bolha(TEXTO_SEGREDO);
            return;
        }
        ControleResposta.chamadaFrase(pergunta, resposta);
        Registro.linha("ENSINO    : absorvido");
        bolha(ackAbsorvido(resposta));
    }

    private static String ackAbsorvido(String fala) {
        java.util.List<String> opcoes = new java.util.ArrayList<>(FRASES_ABSORVIDO);
        if (ultimoAbsorvido != null)
            opcoes.remove(ultimoAbsorvido);
        String modelo = opcoes.get(new java.util.Random().nextInt(opcoes.size()));
        ultimoAbsorvido = modelo;
        return modelo.replace("{fala}", fala);
    }

    // #49 - entrega direta na bolha: o protocolo do ensino (ack e
    // confirmacao) nao passa pelo pipeline - ele NAO mexe no estado
    private static void bolha(String texto) {
        ControleSessao.anotaResposta(texto);
        Registro.linha("KAIRA     : " + texto);
        ControleFace.recebeResp(texto);
    }

    public static void decifrar(String falaUsuario) {

        Registro.linha("---------------------------");
        Registro.linha("ENTRADA   : " + falaUsuario);

        ControleSessao.processa(falaUsuario);

        // #24: a resposta ao pedido de nome nao passa pelo classificador
        // #49: mas com ensino pendente o degrau come a resposta do ensino
        // (medido: "zork" virava nome na 1a sessao) - entao fica fora do turno
        if (!ensinoPendente()) {
            String agradecimento = ControleSessao.respostaDeNome(falaUsuario);
            if (agradecimento != null) {
                Registro.linha("DEGRAU    : resposta-nome");
                enviarResposta(agradecimento, falaUsuario);
                return;
            }
        }
        falaUsuario = ControleSessao.tiraNome(falaUsuario);
        falaUsuario = Normalizador.texto(falaUsuario);

        System.out.println("texto recebido pós replaces "+falaUsuario);

        // #50 - ensino dela (posicao do P2, ANTES da cascata): com o P1 armado,
        // frase na forma "sua CHAVE ... e VALOR" grava na memoria pessoal - o
        // score NAO decide. Invariante da #48: sem P1 armado, NADA grava (a
        // frase solta no ar segue o pipeline de sempre).
        if (ensinoPendente() && respostaEnsino == null) {
            String[] fato = ControleKaira.extraiFatoDela(falaUsuario);
            if (fato != null) {
                if (ControleKaira.ehSegredo(falaUsuario)
                        || !ControleKaira.gravaFato(fato[0], fato[1])) {
                    Registro.linha("TRIAGEM   : fato dela recusado (segredo)");
                    limpaEnsino();
                    bolha(TEXTO_SEGREDO);
                } else {
                    Registro.linha("TRIAGEM   : fato dela gravado chave=" + fato[0]);
                    limpaEnsino();
                    bolha(ackAbsorvido(falaUsuario));   // o mesmo curado do #48 ("Anotado...")
                }
                return;
            }
        }

        // #49 - P2 (resposta retida pelo porteiro): so um SIM explicito grava
        // o par; um NAO explicito descarta. Roda ANTES do classificador
        // porque "nao" casa como resposta pronta (medido: DEGRAU direto).
        if (bufferFrases != null && respostaEnsino != null) {
            if (ehConfirmacaoEnsino(falaUsuario)) {
                Registro.linha("ENSINO    : P2 confirmado");
                absorve(respostaEnsino);
                return;
            }
            if (ehNegacaoEnsino(falaUsuario)) {
                Registro.linha("ENSINO    : P2 descartado");
                limpaEnsino();
                bolha(TEXTO_RECUSA);
                return;
            }
            Registro.linha("ENSINO    : P2 aguardando confirmacao");
        }

        // #39 - aposta pendente: "isso" confirma e entrega o candidato na hora,
        // sem passar pelo classificador
        String confirmada = ControleResposta.entregaApostaPendente(falaUsuario);
        if (confirmada != null) {
            Registro.linha("DEGRAU    : confirma-aposta");
            enviarResposta(confirmada, falaUsuario);
            return;
        }

        // #41 - pergunta pendente dela: a fala seguinte e' a resposta do
        // followup. Fala que o pipeline ENTENDE (ou pergunta de outro
        // assunto) cancela e flui (o ack sai no enviarResposta). A maquina
        // absorve so' o que o pipeline nao sabe responder (aposta e abaixo).
        if (ControleSessao.getPerguntaPendente() != null
                && !ControleComandos.validaComandos(falaUsuario)) {
            double score = ControleResposta.scoreDo(falaUsuario);
            boolean entendida = ControleResposta.ehPergunta(falaUsuario)
                    || score >= LIMIAR_CONFIANCA;
            if (entendida) {
                Registro.linha("PENDENTE : cancelamento (fala entendida)");
            } else {
                String pergunta = ControleSessao.getPerguntaPendente();
                String familia = ControleSessao.getFamiliaPergunta();
                ControleSessao.limpaPerguntaPendente();
                String fmt = String.format(java.util.Locale.ROOT, "%.3f", score);
                if (score >= PISO) {
                    ControleResposta.absorveNoIntento(
                            familia != null ? familia : "auto_aprendizado",
                            falaUsuario, pergunta);
                    Registro.linha("USUARIO   : resposta ao followup familia=" + familia
                            + " score=" + fmt);
                    bolha(ackAbsorvido(falaUsuario));
                } else {
                    // P2 do #48: retem o par - nada grava sem "sim"
                    bufferFrases = falaUsuario;
                    respostaEnsino = pergunta;
                    Registro.linha("PENDENTE : retido (score=" + fmt + ")");
                    bolha(TEXTO_CONFIRMACAO.replace("{fala}", falaUsuario));
                }
                return;
            }
        }

        if(!ControleComandos.validaComandos(falaUsuario)){

            String degrau = "aprender";

            // #50(v2) - o campo respostaVariavel e' STATIC: sem reset por
            // turno ele vira maleta - a primeira resposta vence pra sempre
            // (matcher, correcoes e aposta nunca mais correm; medido no
            // replay: "aprender" 94%, GERAL 0.1)
            respostaVariavel = null;

            // #50 - triagem: porta DELA (memoria pessoal) + porta USUARIO
            // (fatos do contexto); null = miss, o pipeline segue inteiro.
            String triada = ControleResposta.respostaPorTriagem(falaUsuario);
            if (triada != null) {
                respostaVariavel = triada;
                degrau = "triagem";
            }
            if(respostaVariavel==null){
                respostaVariavel = ControleResposta.lerResposta(falaUsuario);
                if(respostaVariavel!=null)
                    degrau = "direto";
            }

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
            // #29 - detalhe da classificacao: intento, score, frase-exemplo, tokens
            String match = ControleResposta.getUltimoMatchLog();
            if (match != null)
                Registro.linha("MATCH     : " + match);
            // #49 - aprendizado organico: sem resposta no fim da cascata e com
            // ensino PENDENTE, quem julga o turno e o porteiro (a regua e o
            // score do #33 sobre o BUFFER - nao sobre a resposta)
            if (respostaVariavel == null && ensinoPendente() && respostaEnsino == null) {
                double score = ControleResposta.scoreDo(bufferFrases);
                Registro.linha("ENSINO    : porteiro score="
                        + String.format(java.util.Locale.ROOT, "%.3f", score)
                        + " piso=" + PISO);
                if (score >= PISO) {
                    absorve(falaUsuario);
                } else {
                    // reten em P2: nada gravado, pede confirmacao citando o buffer
                    String pendente = bufferFrases;
                    respostaEnsino = falaUsuario;
                    Registro.linha("ENSINO    : P2 retido, aguardando confirmacao");
                    bolha(TEXTO_CONFIRMACAO.replace("{fala}", pendente));
                }
                return;
            }

            criacaoLeituraRespostas(falaUsuario);
        }else{
            Registro.linha("DEGRAU    : comando");
            // #49 - comando entendido tambem fecha o ensino pendente
            if (ensinoPendente()) {
                Registro.linha("ENSINO    : cancelado (comando)");
                limpaEnsino();
            }
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
            // #49 - auto-sanificacao: um P2 sem confirmacao nao sobrevive -
            // a conversa segue e a nova frase vira o P1
            respostaEnsino = null;
            Registro.linha("ENSINO    : P1 armado (aguardando resposta)");
            String slot = ControlePreAnalise.preAnalise(falaUsuario).getSlot();
            texto = ControleResposta.respostaFallback(slot);
            Registro.linha("APRENDENDO: " + falaUsuario + " (slot=" + slot + ")");
        }else{
            texto = resposta;
            // #49 - regra 1: qualquer resposta normal fecha o ensino pendente,
            // e o cancel nao mora calado (ack transparente)
            if (ensinoPendente()) {
                texto = texto + " " + TEXTO_CANCELADO;
                Registro.linha("ENSINO    : cancelado (fala entendida)");
            }
            limpaEnsino();

            // #41 - a pergunta pendente dela: fala entendida cancela com ack
            if (ControleSessao.getPerguntaPendente() != null) {
                texto = texto + " " + TEXTO_CANCELADO;
                Registro.linha("PENDENTE : cancelada (fala entendida)");
                ControleSessao.limpaPerguntaPendente();
            }

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
