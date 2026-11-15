#!/usr/bin/env python3
"""Recover correct one-to-one historical phase artifact results from an existing
fully completed 11-matrix CI run. Never merge overlapping shard*.json blindly.
"""
import argparse,json,pathlib,shutil
ROOT=pathlib.Path(__file__).resolve().parents[2]
DEST=ROOT/"reports/broadcast_nonsc_phase65_20261116/compile-stages"
p=argparse.ArgumentParser()
p.add_argument("--artifact-root",type=pathlib.Path,required=True)
args=p.parse_args()
assert args.artifact_root.is_dir(),str(args.artifact_root)
DEST.mkdir(exist_ok=True,parents=True)
for shard in range(11):
    original=(args.artifact_root/f"broadcast-historical-shard-{shard}"/f"shard{shard:02d}.json")
    assert original.is_file(),("missing matrix artifact source",shard,str(original))
    data=json.loads(original.read_text(encoding="utf8"))
    assert data["shard"]==shard,(shard,data["shard"])
    assert data["phases"]==list(range(shard*5+1,shard*5+6)),(shard,data["phases"])
    assert len(data["results"])==5
    target=DEST/f"shard{shard:02d}.json"
    shutil.copyfile(original,target)
    print("PHASE65_ARTIFACT_SOURCE_RESTORED",shard,"stages",data["phases"],
          "compiled",data["all_compiled"],
          "tests",[(r["phase"],[(t["class"],t.get("passed",False)) for t in r["embedded_tests"]])
                   for r in data["results"]],flush=True)
print("PHASE65_UNAMBIGUOUS_11_ARTIFACTS_RESTORED")
