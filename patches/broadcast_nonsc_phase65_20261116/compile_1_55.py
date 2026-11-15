#!/usr/bin/env python3
"""Independent Java 21 compile + embedded parity test of every phase 1..55.

Run as CI matrix (11 shards x 5 stages) with one file per shard. Nothing in the
archived production source is rewritten; no baseline-only stubs are used.
"""
from __future__ import annotations
import argparse,ast,hashlib,json,os,pathlib,re,shutil,subprocess,sys,time,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
REPORTS=ROOT/"reports/broadcast_nonsc_phase65_20261116"/"compile-stages"
def archive(n):
    if n==1:return ROOT/"downloads/RKern_Broadcast_FullAudit_Central2_FULL_SRC_20261115.zip"
    return ROOT/f"downloads/RKern_Broadcast_NonSC_Phase{n}_FULL_SRC_202611{'16' if n>=50 else '15'}.zip"
def migration_scripts(n):
    if n==1:return sorted((ROOT/"patches/broadcast_full_audit_20261115").glob("migrate*.py"))
    dates=["20261115","20261116"]
    result=[]
    for day in dates:
        d=ROOT/f"patches/broadcast_nonsc_phase{n}_{day}"
        if d.is_dir():result.extend(d.glob("migrate*.py"))
    return sorted(set(result))
def embedded_tests(n):
    result=[]
    for script in migration_scripts(n):
        tree=ast.parse(script.read_text(encoding="utf8"),filename=str(script))
        for node in tree.body:
            if isinstance(node,(ast.Assign,ast.AnnAssign)):
                names=[t.id for t in node.targets if isinstance(t,ast.Name)] if isinstance(node,ast.Assign) else [node.target.id] if isinstance(node.target,ast.Name) else []
                # Earlier stages name their embedded parity classes REGRESSION or PARITY,
                # not necessarily TEST. Discover by runnable Java main method.
                try:source=ast.literal_eval(node.value)
                except (ValueError,TypeError,KeyError):continue
                if not isinstance(source,str):continue
                if "static void main(String" not in source and "static void main(String[]" not in source:continue
                m=re.search(r"\bpublic\s+(?:final\s+)?class\s+(\w+)\b",source)
                if m is None:continue
                result.append((m.group(1),source,str(script.relative_to(ROOT))))
    return result
def run(cmd,timeout,where,env=None):
    started=time.monotonic()
    try:
        p=subprocess.run(cmd,cwd=where,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                         encoding="utf-8",errors="replace",timeout=timeout,env=env)
        return {"exit":p.returncode,"secs":round(time.monotonic()-started,3),"log":p.stdout[-15000:]}
    except subprocess.TimeoutExpired as e:
        value=(e.stdout or b"")
        if isinstance(value,bytes):value=value.decode("utf8",errors="replace")
        return {"exit":124,"secs":round(time.monotonic()-started,3),"log":"TIMEOUT\n"+value[-9000:]}
def main():
    p=argparse.ArgumentParser()
    p.add_argument("--shard",type=int,required=True)
    p.add_argument("--scratch",type=pathlib.Path,required=True)
    args=p.parse_args()
    assert 0<=args.shard<11
    args.scratch.mkdir(exist_ok=True,parents=True)
    REPORTS.mkdir(exist_ok=True,parents=True)
    deps=str(ROOT/".compile-deps"/"*")
    assert len(list((ROOT/".compile-deps").glob("*.jar")))>=9,"missing pinned JDK dependencies"
    phases=list(range(args.shard*5+1,args.shard*5+6))
    results=[]
    for i in phases:
        tmp=args.scratch/f"phase{i}"
        if tmp.exists():shutil.rmtree(tmp)
        tmp.mkdir()
        classes=tmp/"classes";classes.mkdir()
        java_src=tmp/"src";java_src.mkdir()
        source=archive(i)
        assert source.is_file(),source
        with zipfile.ZipFile(source) as z:
            names=[x.filename for x in z.infolist() if x.filename.endswith(".java") and x.filename.startswith("src/")]
            assert len(names) in (1710,1711),(i,len(names))
            for n in names:
                f=tmp/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(z.read(n))
        argfile=tmp/"sources.txt"
        argfile.write_text("".join(str(tmp/n)+"\n" for n in names),encoding="utf8")
        build=run(["javac","--enable-preview","--release","21","-encoding","UTF-8",
                   "-J-Xmx5g","-cp",deps,"-d",str(classes),"@"+str(argfile)],
                  timeout=150,where=ROOT)
        record={"phase":i,"source":source.name,"java":len(names),"compile_ok":build["exit"]==0,
                "compile_seconds":build["secs"],"compiler_tail":build["log"][-7000:],
                "migration_scripts":[str(x.relative_to(ROOT)) for x in migration_scripts(i)],
                "embedded_tests":[]}
        if build["exit"]==0:
            for classname,sourcecode,script in embedded_tests(i):
                if i==3 and classname=="SocialActionSharedParity":
                    # Phase3's original build dynamically rewrote its source-level
                    # placeholder imports to the canonical generated Proto package.
                    # Restore exactly that historical test compilation contract.
                    with zipfile.ZipFile(source) as zz:
                        social=[x for x in zz.namelist()
                                if x.endswith("/SC_SOCIAL_ACTION_NOTI.java") and x.startswith("src/")]
                        assert len(social)==1,social
                        jsource=zz.read(social[0]).decode("utf8")
                    package=re.search(r"(?m)^package\\s+([\\w.]+)\\s*;",jsource)
                    assert package is not None,"social Proto package declaration missing"
                    sourcecode=sourcecode.replace(
                        "l1j.server.server.packets.Protocol.MainServer.Proto.SC_SOCIAL_ACTION_NOTI",
                        package.group(1)+".SC_SOCIAL_ACTION_NOTI")
                    for proto_type in ("ProtoWriter","ProtoMsg"):
                        imported=re.search(r"(?m)^import\\s+([\\w.]+\\."+proto_type+r")\\s*;",jsource)
                        assert imported is not None,("stage3 proto import",proto_type)
                        sourcecode=sourcecode.replace(
                            "l1j.server.server.packets.Protocol."+proto_type,imported.group(1))
                target=tmp/"testsrc"/(classname+".java")
                target.parent.mkdir(exist_ok=True,parents=True)
                target.write_text(sourcecode,encoding="utf8")
                testclass=tmp/"testclasses";testclass.mkdir(exist_ok=True)
                b=run(["javac","--enable-preview","--release","21","-encoding","UTF-8",
                       "-cp",os.pathsep.join((str(classes),deps)),"-d",str(testclass),str(target)],
                       timeout=60,where=ROOT)
                testrecord={"class":classname,"source":script,"compile_ok":b["exit"]==0,
                            "compile_seconds":b["secs"]}
                if b["exit"]:
                    testrecord["compile_log"]=b["log"][-6500:]
                else:
                    rr=run(["java","--enable-preview","-cp",os.pathsep.join((str(testclass),str(classes),deps)),
                            classname],timeout=45,where=ROOT)
                    testrecord.update({"passed":rr["exit"]==0,"runtime_seconds":rr["secs"],
                                       "runtime_tail":rr["log"][-6500:]})
                record["embedded_tests"].append(testrecord)
        results.append(record)
        print("PHASE65_HISTORIC_STAGE",i,"compile",record["compile_ok"],
              "tests",[(t["class"],t.get("passed",False)) for t in record["embedded_tests"]],flush=True)
        shutil.rmtree(tmp)
    result={"shard":args.shard,"phases":phases,"results":results,
            "all_compiled":all(r["compile_ok"] for r in results),
            "embedded_test_success":all(t.get("passed",False) for r in results for t in r["embedded_tests"]),
            "missing_embedded_test_phases":[r["phase"] for r in results if not r["embedded_tests"]]}
    out=REPORTS/f"shard{args.shard:02d}.json"
    out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
    print("PHASE65_SHARD_FINAL",args.shard,result["all_compiled"],result["embedded_test_success"],
          "missing_embedded",result["missing_embedded_test_phases"],flush=True)
if __name__=="__main__":main()
