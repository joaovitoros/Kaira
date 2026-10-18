package com.JCODE.controller;

import com.JCODE.util.Calculos;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Normalizador;
import com.fasterxml.jackson.databind.JsonNode;
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
            int distancia = Calculos.damerau(compara, palavra);
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
                return geraPalavrasDasFontes();
        }

        return null;
    }

    public static void carregaCache() {

        if (objectMapper == null)
            objectMapper = new ObjectMapper();

        palavrasCache = buscaArquivoPalavra();
    }

    /**
     * Regera o dicionario quando o palavras.json nao existe (issue #47): le a
     * base de intents + os sinonimos, normaliza igual o pipeline normaliza,
     * GRAVA o arquivo e devolve a lista. Nunca devolve null - null derrubava
     * a correcao inteira (NPE no existsPalavras).
     *
     * Ordem: BASE PRIMEIRO, sinonimo-so-depois. E o que garante que o
     * desempate da correcao (issue #46) continue preferindo a palavra da base
     * quando as duas empatam, em QUALQUER regeneracao - medido no A/B do #47:
     * com a ordem certa, acrescentar os 18660 sinonimos nao muda o acerto nas
     * palavras da base (94,1% = 94,1%) e leva a palavra so-sinonimo de 0% pra
     * 94,7%. Commitado tem que ser igual ao regenerado.
     */
    private static List<String> geraPalavrasDasFontes() {

        LinkedHashSet<String> base = new LinkedHashSet<>();
        LinkedHashSet<String> sinonimos = new LinkedHashSet<>();

        try {
            JsonNode intents = objectMapper.readValue(
                    Caminhos.recurso("intencoes.json").toFile(), JsonNode.class);

            for (JsonNode intento : intents.get("intencoes")) {
                JsonNode exemplos = intento.get("exemplos");
                if (exemplos == null)
                    continue;
                for (JsonNode exemplo : exemplos)
                    for (String palavra : Normalizador.texto(exemplo.get("texto").asText()).split(" "))
                        if (!palavra.isEmpty())
                            base.add(palavra);
            }
        } catch (IOException e) {
            e.printStackTrace();
            // sem base nao da pra regerar - lista vazia, nunca null
            return new ArrayList<>();
        }

        try {
            JsonNode grupos = objectMapper.readValue(
                    Caminhos.recurso("grupos_sinonimos.json").toFile(), JsonNode.class);

            for (JsonNode grupo : grupos.get("grupos"))
                for (JsonNode membro : grupo)
                    for (String palavra : Normalizador.texto(membro.asText()).split(" "))
                        if (!palavra.isEmpty() && !base.contains(palavra))
                            sinonimos.add(palavra);
        } catch (IOException e) {
            // grupos e enriquecimento: sem ele, regenera so da base
            e.printStackTrace();
        }

        List<String> palavras = new ArrayList<>(base);
        palavras.addAll(sinonimos);

        try (FileWriter fileWriter = new FileWriter(
                Caminhos.recurso("palavras.json").toFile(), StandardCharsets.UTF_8)) {
            fileWriter.write(objectMapper.writeValueAsString(palavras));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return palavras;
    }


}
