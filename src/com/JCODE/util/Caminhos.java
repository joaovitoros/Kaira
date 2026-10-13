package com.JCODE.util;

import java.nio.file.*;
import java.net.URISyntaxException;

public final class Caminhos {
    private static Path base;

    /** Base = raiz do projeto (dir que contem KAIRA/). Prioridade:
     *  sysprop kaira.home > env KAIRA_HOME > sobe a partir do code source > CWD. */
    public static synchronized Path base() {
        if (base == null) base = resolver();
        return base;
    }

    /** Caminho absoluto de um arquivo de dados, ex.: recurso("respostas.json") */
    public static Path recurso(String nome) {
        return base().resolve("KAIRA").resolve("resources").resolve(nome);
    }

    /** So pra teste limpar o cache estatico. */
    public static synchronized void resetar() { base = null; }

    /** Sobe de `inicio` ate achar o dir que contem KAIRA/. Testavel isolado. */
    static Path enraizar(Path inicio) {
        Path p = inicio.toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("KAIRA"))) return p;
            p = p.getParent();
        }
        return null;
    }

    private static Path resolver() {
        String prop = System.getProperty("kaira.home");
        if (prop != null) return Paths.get(prop);
        String env = System.getenv("KAIRA_HOME");
        if (env != null) return Paths.get(env);
        try {
            Path codigo = Paths.get(Caminhos.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path raiz = enraizar(codigo);
            if (raiz != null) return raiz;
        } catch (URISyntaxException ignored) { }
        return Paths.get("").toAbsolutePath();  // fallback: CWD
    }

    private Caminhos() {}
}