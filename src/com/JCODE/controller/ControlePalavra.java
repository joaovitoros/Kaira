package com.JCODE.controller;

import com.JCODE.util.Calculos;
import com.JCODE.util.Caminhos;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ControlePalavra {

    static ObjectMapper objectMapper;

    static List<String> palavrasCache;

    public static void gravaPalavra(String frase){

        if(objectMapper == null)
            objectMapper = new ObjectMapper();

        List<String> palavras = buscaArquivoPalavra();

        if(palavras==null){
            palavras = new ArrayList<>();
        }

        for(String palavraSeparada: frase.split(" ")){
            if(!palavras.contains(palavraSeparada))
                palavras.add(palavraSeparada);
        }

        try (FileWriter fileWriter = new FileWriter(Caminhos.recurso("palavras.json").toFile(), StandardCharsets.UTF_8)) {

            String jsonInicial = objectMapper.writeValueAsString(palavras);

            fileWriter.write(jsonInicial);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void gravaPalavraEmLista(List<String> frases){

        if(objectMapper == null)
            objectMapper = new ObjectMapper();

        List<String> palavras = buscaArquivoPalavra();

        if(palavras==null){
            palavras = new ArrayList<>();
        }

        for(String frase: frases) {
            for (String palavraSeparada : frase.split(" ")) {
                if (!palavras.contains(palavraSeparada))
                    palavras.add(palavraSeparada);
            }
        }

        try (FileWriter fileWriter = new FileWriter(
                Caminhos.recurso("palavras.json").toFile(), StandardCharsets.UTF_8)) {

            String jsonInicial = objectMapper.writeValueAsString(palavras);

            fileWriter.write(jsonInicial);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String correcaoPalavra(String compara){

        int limiar = (compara.length() <= 4) ? 1 : 2;

        String melhorPalavra = null;
        int melhorDistancia = Integer.MAX_VALUE;

        for(String palavra: palavrasCache){
            int distancia = Calculos.levenshtein(compara, palavra);
            if(distancia <= limiar && distancia < melhorDistancia){
                melhorDistancia = distancia;
                melhorPalavra = palavra;
            }
        }

        if(melhorPalavra != null)
            return melhorPalavra;

        return compara;
    }

    public static boolean existsPalavras(String palavra){
        return palavrasCache.contains(palavra);
    }

    private static List<String> buscaArquivoPalavra(){

        try {

            FileInputStream fileInputStream = new FileInputStream(Caminhos.recurso("palavras.json").toFile());

            InputStreamReader reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);

            return objectMapper.readValue(reader, List.class);
        }catch (Exception e){
            if(e instanceof FileNotFoundException)

                return null;
        }

        return null;
    }

    public static void carregaCache() {

        if (objectMapper == null)
            objectMapper = new ObjectMapper();

        palavrasCache = buscaArquivoPalavra();
    }

}
