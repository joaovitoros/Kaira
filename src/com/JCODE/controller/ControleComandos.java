package com.JCODE.controller;

import javax.swing.*;

public class ControleComandos {

    public static boolean validaComandos(String frase) {

        if (false) {
            //TODO comandos ainda precisam ser validados
            return true;
        } else {

            String fraseComando[] = frase.split(" ");
            if (fraseComando[0].equalsIgnoreCase("Executar")) {
                try {
                    Runtime.getRuntime().exec(fraseComando[1]);
                    FormulaResposta.enviarResposta("Executando o programa solicitado...", "");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null,
                            "ERRO durante a abertura do pragrama!");
                    e.printStackTrace();
                }
                return true;
            }

            return false;

        }
    }
}
