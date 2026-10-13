package com.JCODE.controller;

import com.JCODE.domain.Sinonimo;
import com.JCODE.domain.Sinonimos;
import com.JCODE.util.Caminhos;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class ControleSinonimo {

    static ObjectMapper objectMapper;

    static List<Sinonimo> sinonimosCache;

    private static Sinonimos buscaArquivoSinonimo(){

        try {
            FileInputStream fileInputStream = new FileInputStream(Caminhos.recurso("sinonimos.json").toFile());

            InputStreamReader reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);

            return objectMapper.readValue(reader, Sinonimos.class);
        }catch (Exception e){
            if(e instanceof FileNotFoundException)

                return null;
        }

        return null;
    }

    public static void carregaCache() {

        if (objectMapper == null)
            objectMapper = new ObjectMapper();

        sinonimosCache = buscaArquivoSinonimo().getSinonimos();
    }

    public static String procuraSinonimo(String palavra){

        String[] retorno = new String[1];

        sinonimosCache.stream().filter(sinonimo -> sinonimo.getPalavra().equalsIgnoreCase(palavra))
                .findFirst().ifPresentOrElse(sinonimo -> {
            retorno[0] = escolheSinonimo(sinonimo.getSinonimos());
        }, () ->{ retorno[0] = palavra;
        });

        if(retorno[0].equalsIgnoreCase(palavra)) {
            String palavraCorrigida = ControlePalavra.correcaoPalavra(palavra);

            sinonimosCache.stream().filter(sinonimo -> sinonimo.getPalavra().equalsIgnoreCase(palavraCorrigida))
                    .findFirst().ifPresent(sinonimo ->
                retorno[0] = escolheSinonimo(sinonimo.getSinonimos()));
        }

        return retorno[0];

    }

    private static String escolheSinonimo(List<String> sinonimos){

        if (sinonimos.isEmpty())
            return null;

        int qtdSinonimos = sinonimos.size();

        int escolha = (int) (Math.random() * (qtdSinonimos));

        return sinonimos.get(escolha);
    }

}
