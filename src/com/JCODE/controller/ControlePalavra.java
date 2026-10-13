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
        double porcentagem = 0;
        String palavra;

        Map<String, Double> bufferCalc = new HashMap<>();

        for (int i = 0; i < palavrasCache.size(); i++) {
            palavra = palavrasCache.get(i);
            porcentagem = Calculos.calculoPorcentagemPalavra(compara, palavra);
            if (porcentagem==100) {
                System.out.println(porcentagem+"/"+ palavra);
                return palavra;
            }else if(porcentagem>=70){
                System.out.println(porcentagem+"/"+ palavra);

                bufferCalc.put(palavra, porcentagem);

            }
        }

        List<Map.Entry<String, Double>> entryList = new ArrayList<>(bufferCalc.entrySet());

        Collections.sort(entryList, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> entry1, Map.Entry<String, Double> entry2) {
                return Double.compare(entry2.getValue(), entry1.getValue());
            }
        });

        if(!entryList.isEmpty()) {
            String melhorPalavra = entryList.get(0).getKey();

            Double melhorPorcentagem = entryList.get(0).getValue();

            if (melhorPorcentagem >= 75)
                return melhorPalavra;
        }

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
