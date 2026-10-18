#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Junta os corpora de varias rodadas num UNICO baterias.json, marcando onde
cada rodada comeca e termina.

Uso:
    python combinar_rodadas.py --saida baterias.json rodada-1/baterias.json rodada-2/baterias.json

Cada turno ganha o campo "rodada" (1, 2, ...). O cabecalho ganha "_rodadas",
com o numero de turnos, o indice do primeiro turno e a distribuicao de degraus
de cada rodada - e o indice global do corte ("primeiro_turno"/"ultimo_turno").

Os arquivos de origem NAO sao tocados.
"""

import argparse
import json
import os


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--saida", required=True, help="arquivo unificado a gravar")
    ap.add_argument("--base", help="corpus unificado existente; a(s) rodada(s) nova(s) e anexada(s) no fim")
    ap.add_argument("--rotulo", help="rotulo da rodada nova (default: nome da pasta de origem)")
    ap.add_argument("corpora", nargs="+", help="baterias.json de cada rodada, em ordem")
    args = ap.parse_args()

    turnos = []
    rodadas = {}
    fontes = []
    proxima = 1
    if args.base:
        base = json.load(open(args.base, encoding="utf-8"))
        turnos = list(base["turnos"])
        rodadas = dict(base.get("_rodadas", {}))
        fontes = list(base.get("_fonte", []))
        proxima = max((int(k) for k in rodadas), default=0) + 1

    for indice, caminho in enumerate(args.corpora, start=proxima):
        dados = json.load(open(caminho, encoding="utf-8"))
        fontes.append(dados.get("_fonte", caminho))
        inicio = len(turnos)
        degraus = {}
        for t in dados["turnos"]:
            novoTurno = {"rodada": indice, "bateria": t["bateria"]}
            for chave, valor in t.items():
                if chave in ("rodada", "bateria"):
                    continue
                novoTurno[chave] = valor
            # o rotulo "bloco" era do desenho da rodada 1 (jar 1-10 / fonte 11-20);
            # nas rodadas seguintes as 10 primeiras sao personas e as 10 ultimas sondagens
            if indice > 1 and novoTurno.get("bloco") in ("jar", "fonte"):
                novoTurno["bloco"] = "personas" if t["bateria"] <= 10 else "sondagens"
            turnos.append(novoTurno)
            d = t.get("degrau") or "(sem degrau)"
            degraus[d] = degraus.get(d, 0) + 1
        base_corpus = dados.get("_linha_de_base", {})
        rodadas[str(indice)] = {
            "rotulo": args.rotulo if args.rotulo else (
                base_corpus.get("rotulo") or os.path.basename(os.path.dirname(caminho)) or caminho),
            "turnos": len(turnos) - inicio,
            "primeiro_turno": inicio,
            "ultimo_turno": len(turnos) - 1,
            "degraus": dict(sorted(degraus.items(), key=lambda kv: -kv[1])),
            "aposta_pct": round(100.0 * degraus.get("aposta", 0) / max(1, len(turnos) - inicio), 1),
            "direto_pct": round(100.0 * degraus.get("direto", 0) / max(1, len(turnos) - inicio), 1),
            "aprender_pct": round(100.0 * degraus.get("aprender", 0) / max(1, len(turnos) - inicio), 1),
        }

    degraus = {}
    for t in turnos:
        d = t.get("degrau") or "(sem degrau)"
        degraus[d] = degraus.get(d, 0) + 1

    saida = {
        "_doc": (
            "Corpus de aceitacao UNIFICADO das rodadas de bateria (out/2026). Cada turno "
            "tem 'rodada'; '_rodadas' diz quantos turnos tem cada uma e onde ela comeca/"
            "termina no array. O resultado gravado e o OBSERVADO na epoca (inclusive os "
            "erros), nao o esperado. Re-rodar: com.JCODE.tools.ReplayBaterias (a base "
            "isolada e RESTAURADA a cada troca de rodada, senao o aprendizado de uma "
            "contaminaria a medida da seguinte)."
        ),
        "_fonte": fontes,
        "_rodadas": rodadas,
        "_linha_de_base": {
            "turnos": len(turnos),
            "degraus": dict(sorted(degraus.items(), key=lambda kv: -kv[1])),
            "aposta_pct": round(100.0 * degraus.get("aposta", 0) / max(1, len(turnos)), 1),
            "direto_pct": round(100.0 * degraus.get("direto", 0) / max(1, len(turnos)), 1),
            "aprender_pct": round(100.0 * degraus.get("aprender", 0) / max(1, len(turnos)), 1),
        },
        "turnos": turnos,
    }

    with open(args.saida, "w", encoding="utf-8", newline="\n") as f:
        json.dump(saida, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("Gravado:", args.saida)
    for rotulo, info in rodadas.items():
        print("  rodada %s: %d turnos (indices %d..%d) | %s"
              % (rotulo, info["turnos"], info["primeiro_turno"], info["ultimo_turno"], info["degraus"]))
    print("  TOTAL   : %d turnos | %.1f KB" % (len(turnos), os.path.getsize(args.saida) / 1024.0))


if __name__ == "__main__":
    main()
