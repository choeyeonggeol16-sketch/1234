#!/usr/bin/env python3
"""Merge 11 independent 5-stage Java21/parity results (phases 1..55)."""
import json,pathlib,sys,collections
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/"reports/broadcast_nonsc_phase65_20261116"
part=OUT/"compile-stages"
files=sorted(part.glob("shard*.json"))
assert len(files)==11,("missing matrix shards",len(files),[x.name for x in files])
rows=[];shards=[]
for path in files:
    record=json.loads(path.read_text(encoding="utf8"))
    shards.append(record["shard"])
    rows.extend(record["results"])
assert sorted(shards)==list(range(11))
assert sorted(r["phase"] for r in rows)==list(range(1,56))
rows.sort(key=lambda x:x["phase"])
bad_compiles=[{"phase":r["phase"],"tail":r["compiler_tail"][-1800:]} for r in rows if not r["compile_ok"]]
tests=[{"phase":r["phase"],**t} for r in rows for t in r["embedded_tests"]]
bad_tests=[{"phase":t["phase"],"class":t["class"],"compile_log":t.get("compile_log","")[-1000:],
            "runtime_tail":t.get("runtime_tail","")[-1000:]} for t in tests if not t.get("passed",False)]
missing_tests=[r["phase"] for r in rows if not r["embedded_tests"]]
summary={
    "phases_checked":55,
    "java21_full_sources_compiled":55-len(bad_compiles),
    "java21_full_compilation_failures":bad_compiles,
    "embedded_parity_executed":sum(t.get("compile_ok",False) for t in tests),
    "embedded_parity_passed":sum(t.get("passed",False) for t in tests),
    "embedded_parity_failures":bad_tests,
    "phases_without_embedded_runnable_test":missing_tests,
    "test_scope":"JDK21 full-source compilation plus each phase's embedded Java parity tests, where present. No automated real in-game multiplayer scene is asserted.",
    "results_per_stage":rows
}
(OUT/"PHASE1_55_COMPILE_PARITY.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
print("PHASE65_HISTORY_COMPILE_AND_PARITY",json.dumps({
    k:v for k,v in summary.items() if k!="results_per_stage"
},ensure_ascii=False))
if bad_compiles or bad_tests:sys.exit(1)
