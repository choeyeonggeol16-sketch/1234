#!/usr/bin/env python3
"""Re-run original source migrations for 7 historical stages without an
embedded runnable Java parity test. Independent committed original source ZIPs
serve as inputs; output is written only to isolated scratch, and compared to
the published per-stage full source byte-for-byte for every member.

This establishes reproducibility and original migration assertions; it is not
a substitute for a real gameplay/recipient test.
"""
import argparse,contextlib,hashlib,io,json,pathlib,runpy,shutil,sys,time,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/"reports/broadcast_nonsc_phase65_20261116"
SCRIPTS={
 4:"patches/broadcast_nonsc_phase4_20261115/migrate_gm.py",
 5:"patches/broadcast_nonsc_phase5_20261115/migrate_team.py",
 15:"patches/broadcast_nonsc_phase15_20261115/prune_dead_broadcast.py",
 16:"patches/broadcast_nonsc_phase16_20261115/remove_generic_broadcast.py",
 17:"patches/broadcast_nonsc_phase17_20261115/remove_last_wrappers.py",
 18:"patches/broadcast_nonsc_phase18_20261115/remove_dead_broadcaster.py",
 41:"patches/broadcast_nonsc_phase41_20261115/migrate_magic_pc_effects.py"
}
def expected_archive(n):
    return ROOT/f"downloads/RKern_Broadcast_NonSC_Phase{n}_FULL_SRC_20261115.zip"
def members(path):
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None
        return {x.filename:z.read(x) for x in z.infolist() if not x.is_dir()}
def main():
    p=argparse.ArgumentParser()
    p.add_argument("--scratch",type=pathlib.Path,required=True)
    opts=p.parse_args()
    opts.scratch.mkdir(parents=True,exist_ok=True)
    results=[]
    for stage,rel in SCRIPTS.items():
        start=time.monotonic()
        script=ROOT/rel
        assert script.is_file(),script
        destination=opts.scratch/f"stage{stage}"
        destination.mkdir(parents=True,exist_ok=True)
        produced=destination/"generated.zip"
        history=expected_archive(stage)
        assert history.is_file(),history
        sys_args=sys.argv
        with contextlib.redirect_stdout(io.StringIO()) as capture:
            env=runpy.run_path(str(script))
            mainfunc=env["main"]
            # runpy globals on actual function are mutable, unlike env's
            # shallow wrapper copy. No checked-in output archive is modified.
            g=mainfunc.__globals__
            for key in ("OUTPUT","OUT"):
                if key in g:
                    g[key]=produced
            try:
                sys.argv=[str(script),"--work",str(destination/"work")]
                mainfunc()
            finally:
                sys.argv=sys_args
        assert produced.is_file(),("no output generated",stage)
        expected=members(history)
        regenerated=members(produced)
        assert expected.keys()==regenerated.keys(),("stage entryset mismatch",stage,
            sorted(expected.keys()-regenerated.keys()),sorted(regenerated.keys()-expected.keys()))
        differences=[n for n in expected if expected[n]!=regenerated[n]]
        assert not differences,("stage migration output differs from its committed ZIP",stage,differences)
        test={
          "stage":stage,
          "script":rel,
          "expected_zip":history.name,
          "source_entries":len(expected),
          "java_sources":sum(k.endswith(".java") for k in expected),
          "all_published_entries_reproduced":True,
          "complete_zip_sha256_identical":hashlib.sha256(history.read_bytes()).digest()==hashlib.sha256(produced.read_bytes()).digest(),
          "native_dll_identical":expected["CTransport.dll"]==regenerated["CTransport.dll"],
          "elapsed_seconds":round(time.monotonic()-start,3),
          "original_script_assertions_executed":True,
          "test_scope":"deterministic source transform, ZIP-bytes, original guards, not gameplay execution"
        }
        results.append(test)
        print("PHASE65_GAP_REPLAY_PASS",stage,"entries",len(expected),
              "zip byte hash",test["complete_zip_sha256_identical"],
              "elapsed",test["elapsed_seconds"],flush=True)
        shutil.rmtree(destination)
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"PHASE1_55_SEVEN_GAP_REPLAY.json").write_text(
        json.dumps({"verified_stages":sorted(SCRIPTS),"all_passed":len(results)==7,
                    "records":results},ensure_ascii=False,indent=2)+"\n",encoding="utf8")
    print("PHASE65_SEVEN_GAPS_REPLAY_VERIFIED",len(results))
if __name__=="__main__":main()
