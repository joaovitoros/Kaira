package com.JCODE.controller;

import com.JCODE.domain.KAIRA;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ControleKaira {

    static ObjectMapper objectMapper;

    static KAIRA kairaCache;

    public static KAIRA carregaCacheKAIRA() throws IOException {

        if(objectMapper == null)
            objectMapper = new ObjectMapper();

        FileInputStream fileInputStream = new FileInputStream("KAIRA/resources/kaira.json");

        InputStreamReader reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);

        KAIRA kaira = objectMapper.readValue(reader, KAIRA.class);

        kairaCache = kaira;

        return kaira;
    }

    public static KAIRA criarCacheKAIRA(){

        criarCaminho();

        if(objectMapper==null)
            objectMapper = new ObjectMapper();

        KAIRA kaira = new KAIRA();

        kaira.setNome("K.A.I.R.A");
        kaira.setVersao("0.0.1");

        try (FileWriter fileWriter = new FileWriter("KAIRA/resources/kaira.json", StandardCharsets.UTF_8)) {

            String jsonInicial = objectMapper.writeValueAsString(kaira);

            fileWriter.write(jsonInicial);
            System.out.println("Arquivo JSON escrito com sucesso! " +jsonInicial);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static KAIRA buscaDadosGerais(){
        if(kairaCache ==null) {
            try {
                kairaCache = carregaCacheKAIRA();
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        return kairaCache;
    }

    public static void criarCaminho(){
        File diretorio = new File("KAIRA");
        diretorio.mkdir();
        File dir = new File("KAIRA/resources");
        dir.mkdir();
    }

}
