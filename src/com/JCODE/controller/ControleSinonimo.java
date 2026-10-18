package com.JCODE.controller;

import com.JCODE.domain.GruposSinonimos;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Normalizador;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ControleSinonimo {

    static ObjectMapper objectMapper;

    private static Map<String, Set<String>> indice;
    private static Map<String, Set<String>> indicePalavras;

    private static GruposSinonimos buscaArquivoSinonimo(){

        try {
            FileInputStream fileInputStream = new FileInputStream(Caminhos.recurso("grupos_sinonimos.json").toFile());

            InputStreamReader reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);

            return objectMapper.readValue(reader, GruposSinonimos.class);
        }catch (Exception e){
            if(e instanceof FileNotFoundException)

                return null;
        }

        return null;
    }

    public static void carregaCache() {

        if (objectMapper == null)
            objectMapper = new ObjectMapper();

        indice = new HashMap<>();
        indicePalavras = new HashMap<>();

        for(List<String> sinonimos: Objects.requireNonNull(buscaArquivoSinonimo()).getGrupos()){
            Set<String> brutos = new HashSet<>(sinonimos);              // cru do grupo
            Set<String> todos = new HashSet<>(sinonimos);

            for (String m : sinonimos) todos.add(Normalizador.stem(m));

            for (String palavra : sinonimos) {
                String cru = Normalizador.texto(palavra);
                String radical = Normalizador.stem(palavra);
                indice.computeIfAbsent(cru, k -> new HashSet<>()).addAll(todos);       // chave cru
                indice.computeIfAbsent(radical, k -> new HashSet<>()).addAll(todos);   // chave stemmada
                indicePalavras.computeIfAbsent(cru, k -> new HashSet<>()).addAll(brutos); // so cru
            }
        }
    }

    public static Boolean mesmaPalavra(String a, String b){

        if(indice == null)
            return a.equalsIgnoreCase(b);

        Set<String> vizinhos = indice.getOrDefault(a.toLowerCase(), Set.of());

        return a.equalsIgnoreCase(b) || vizinhos.contains(b.toLowerCase());
    }

    // #49 - flexao de genero/numero (favorita~favorito, obrigada~obrigado):
    // mesma radical, o conteudo nao muda - no matcher vale PESO CHEIO.
    // Sinonimo de verdade (ouv~escut, tu~voce) carrega informacao nova e
    // fica com o 0.7 do gate. Renda medida: os grupos do JSON juntam os
    // dois tipos, entao aqui separa por prefixo em comum >= min(len)-1.
    // So' e' chamado depois de mesmaPalavra confirmar o vinculo.
    public static Boolean eFlexao(String a, String b){
        String x = a.toLowerCase(), y = b.toLowerCase();
        int n = Math.min(x.length(), y.length());
        if (n < 4) return false;                 // curto demais pra régua
        int comum = 0;
        while (comum < n && x.charAt(comum) == y.charAt(comum)) comum++;
        return comum >= n - 1;
    }

    public static Set<String> buscaGrupoSinonimo(String palavra){
        return indicePalavras.getOrDefault(palavra, Set.of());
    }

}
