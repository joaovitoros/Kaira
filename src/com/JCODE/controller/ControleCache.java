package com.JCODE.controller;

import com.JCODE.domain.KAIRA;

import javax.swing.*;
import java.io.FileNotFoundException;

public class ControleCache {

    public static KAIRA carregaCacheGeral(){

        KAIRA kaira = null;

        try {
            kaira = ControleKaira.carregaCacheKAIRA();
            System.out.println("Inicialização inicial --- OK");
        }catch (Exception e){
            if(e instanceof FileNotFoundException)
                geraDadosIniciais();
            else
                e.printStackTrace();

        }

        ControleResposta.carregaCache();
        System.out.println("Inicialização respostas --- OK");
        ControlePalavra.carregaCache();
        System.out.println("Inicialização palavras --- OK");
        ControleSinonimo.carregaCache();
        System.out.println("Inicialização sinonimos --- OK");

        return kaira;
    }

    public static KAIRA geraDadosIniciais() {

        JOptionPane.showMessageDialog(null, "Essa é a primeira inicialização. " +
                "\nPode levar alguns segundos para instalar todos os arquivos necessarios. " +
                "\nPor favor, aperte 'ok' e aguerde o término",null, JOptionPane.INFORMATION_MESSAGE);

        KAIRA kaira = ControleKaira.criarCacheKAIRA();

        JOptionPane.showMessageDialog(null, "Inicialização primaria concluida, o sistema deve ser reiniciado para que possa operar",null, JOptionPane.INFORMATION_MESSAGE);
        System.exit(0);

        return kaira;
    }

}
