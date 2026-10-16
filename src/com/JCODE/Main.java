package com.JCODE;

import com.JCODE.controller.ControleCache;
import com.JCODE.controller.ControleSessao;
import com.JCODE.domain.KAIRA;
import com.JCODE.view.Face;

public class Main {

    public static void main(String[] args) {
        try {

            KAIRA kaira = ControleCache.carregaCacheGeral();
            ControleSessao.carregaSessao();

            Face face = new Face(kaira.getNome());
            face.setVisible(true);

            System.out.println("Inicializações Finalizadas");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
