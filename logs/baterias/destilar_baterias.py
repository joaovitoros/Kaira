#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Destila as 20 baterias de teste (logs/baterias/conversas-N.log) num unico
arquivo reutilizavel: logs/baterias/baterias.json.

Uso:
    python logs/baterias/destilar_baterias.py

O JSON guarda o corpus de aceitacao (as falas reais das personas) junto com o
resultado OBSERVADO na epoca (degrau, intento, score, match). Serventia:
re-rodar as entradas depois de mudancas no classificador e comparar a
distribuicao de degraus (direto/aposta/aprender) com a linha de base gravada.

Os .log originais podem ser apagados depois: tudo o que importa esta aqui,
menos a prosa das respostas (que e regenerada a cada execucao).
"""

import glob
import json
import os
import re
import sys

BASE = os.path.dirname(os.path.abspath(__file__))
SEPARADOR = re.compile(r"^\d{4}-\d\d-\d\d [\d:]{8}\s+-{5,}\s*$", re.M)

RE_ENTRADA = re.compile(r"ENTRADA\s+:\s?(.*)$", re.M)
RE_DEGRAU = re.compile(r"DEGRAU\s+:\s*(\S+)", re.M)
RE_MATCH = re.compile(r"MATCH\s+:\s*intento=(\S+?)\s+score=([\d.,]+)(.*)$", re.M)
RE_FRASE = re.compile(r'frase="([^"]*)"')
RE_TOKENS = re.compile(r"tokens=\[([^\]]*)\]")
RE_TOPICO = re.compile(r"SESSAO\s+:\s*topico=(\S+)", re.M)
RE_KAIRA = re.compile(r"^\d{4}-\d\d-\d\d [\d:]{8}\s+KAIRA\s+:\s?(.*)$")


def resposta_do_bloco(bloco: str) -> str:
    """Junta as linhas KAIRA (inclusive as continuacoes sem timestamp)."""
    linhas = []
    dentro = False
    for linha in bloco.splitlines():
        m = RE_KAIRA.match(linha)
        if m:
            dentro = True
            linhas.append(m.group(1))
        elif dentro:
            linhas.append(linha)
    return " ".join(" ".join(linhas).split())


def turnos_da_bateria(caminho: str):
    texto = open(caminho, encoding="utf-8", errors="replace").read()
    blocos = SEPARADOR.split(texto)[1:]
    numero = int(re.search(r"conversas-(\d+)", caminho).group(1))
    for i, bloco in enumerate(blocos, start=1):
        entrada = RE_ENTRADA.search(bloco)
        if not entrada:
            continue
        degrau = RE_DEGRAU.search(bloco)
        match = RE_MATCH.search(bloco)
        topico = RE_TOPICO.search(bloco)
        frase = RE_FRASE.search(match.group(3)) if match else None
        tokens = RE_TOKENS.search(match.group(3)) if match else None
        yield {
            "bateria": numero,
            "bloco": "jar" if numero <= 10 else "fonte",
            "turno": i,
            "entrada": entrada.group(1).strip(),
            "degrau": degrau.group(1) if degrau else None,
            "intento": match.group(1) if match else None,
            "score": float(match.group(2).replace(",", ".")) if match else None,
            "topico": topico.group(1) if topico else None,
            "match_frase": frase.group(1) if frase else None,
            "match_tokens": [t.strip() for t in tokens.group(1).split(",") if t.strip()] if tokens else None,
            "resposta": resposta_do_bloco(bloco) or None,
        }


def main():
    arquivos = sorted(
        glob.glob(os.path.join(BASE, "conversas-*.log")),
        key=lambda p: int(re.search(r"conversas-(\d+)", p).group(1)),
    )
    if not arquivos:
        sys.exit("Nenhum conversas-N.log encontrado em " + BASE)

    turnos = [t for caminho in arquivos for t in turnos_da_bateria(caminho)]

    degraus = {}
    for t in turnos:
        degraus[t["degrau"] or "(sem degrau)"] = degraus.get(t["degrau"] or "(sem degrau)", 0) + 1

    saida = {
        "_doc": (
            "Corpus de aceitacao destilado das 20 baterias de teste as cegas (out/2026). "
            "Use as 'entrada' para re-rodar o classificador e comparar a distribuicao de "
            "degrau com _linha_de_base. O resultado gravado aqui e o OBSERVADO na epoca "
            "(inclusive os erros), nao o esperado. Baterias 1-10 rodaram pelo .jar (base "
            "antiga); 11-20 pelo codigo-fonte (refino)."
        ),
        "_fonte": "logs/baterias/conversas-1..20.log",
        "_linha_de_base": {
            "turnos": len(turnos),
            "degraus": dict(sorted(degraus.items(), key=lambda kv: -kv[1])),
            "aposta_pct": round(100.0 * degraus.get("aposta", 0) / len(turnos), 1),
            "direto_pct": round(100.0 * degraus.get("direto", 0) / len(turnos), 1),
            "aprender_pct": round(100.0 * degraus.get("aprender", 0) / len(turnos), 1),
            "nota_media": 3.2,
        },
        "turnos": turnos,
    }

    destino = os.path.join(BASE, "baterias.json")
    with open(destino, "w", encoding="utf-8", newline="\n") as f:
        json.dump(saida, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("Gravado:", destino)
    print("Turnos :", len(turnos))
    print("Degraus:", saida["_linha_de_base"]["degraus"])
    print("Tamanho: %.1f KB" % (os.path.getsize(destino) / 1024.0))


if __name__ == "__main__":
    main()
