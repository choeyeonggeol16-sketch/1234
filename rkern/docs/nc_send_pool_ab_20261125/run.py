import csv, importlib.util, json, os, shutil, statistics, subprocess, sys
from pathlib import Path
from types import SimpleNamespace
ROOT=Path.cwd()
HERE=ROOT/"rkern/docs/nc_send_pool_ab_20261125"
spec=importlib.util.spec_from_file_location("bench",ROOT/"rkern/docs/jproring_cio_integration_20261120/run_game_ab.py")
bench=importlib.util.module_from_spec(spec); spec.loader.exec_module(bench)
work=Path(os.environ["RUNNER_TEMP"])/"send-pool-ab";work.mkdir()
out=Path(os.environ["RUNNER_TEMP"])/"benchmark";out.mkdir()
base=work/"baseline";base.mkdir()
basecp=bench.classpath(base,work/"deps")
bench.compile_java(basecp,base,False)
candidate=work/"candidate";shutil.copytree(base,candidate)
candcp=bench.classpath(candidate,work/"deps")
subprocess.run(["javac","--release","21","--enable-preview","-encoding","UTF-8","-cp",candcp,"-d",str(candidate),str(HERE/"CIOSlotPool.java"),str(HERE/"CIOBufferPool.java")],check=True)
targets={"bitmap":basecp,"local":candcp}
name="l1j.server.server.model.network.core.connection.SendPoolAB"
for cp,classes in ((basecp,base),(candcp,candidate)):
    subprocess.run(["javac","--release","21","--enable-preview","-cp",cp,"-d",str(classes),str(HERE/"SendPoolAB.java")],check=True)
    subprocess.run(["java","--enable-preview","-Xmx256m","-XX:MaxDirectMemorySize=128m","-cp",cp,name],check=True)
    tests=ROOT/"rkern/docs/jproring_cio_integration_20261120"
    subprocess.run(["javac","--release","21","--enable-preview","-cp",cp,"-d",str(classes),str(tests/"SelectorCioIntegrationTest.java"),str(tests/"CIOBufferPoolAffinityTest.java")],check=True)
    runtime=work/("runtime-"+classes.name);runtime.mkdir()
    for test in ("SelectorCioIntegrationTest","l1j.server.server.model.network.core.connection.CIOBufferPoolAffinityTest"):
        subprocess.run(["java","--enable-preview","-XX:MaxDirectMemorySize=64m","-cp",cp,test],cwd=runtime,check=True)
if sys.argv[1]=="pool":
    results=[]
    for threads,cross in ((1,False),(2,False),(8,False),(2,True),(8,True),(20,True)):
        for trial in range(3):
            order=list(targets)
            if trial%2:order.reverse()
            for variant in order:
                log=subprocess.check_output(["java","--enable-preview","-Xms128m","-Xmx256m","-XX:MaxDirectMemorySize=128m","-cp",targets[variant],name,str(threads),str(cross).lower()],text=True)
                line=[x for x in log.splitlines() if x.startswith("POOL_RESULT ")][-1]
                _,n,c,rate,cpu,mem,count=line.split()
                row=dict(threads=int(n),cross=c,trial=trial+1,variant=variant,ops_per_s=float(rate),cpu_ns=float(cpu),direct_bytes=int(mem),operations=int(count))
                results.append(row);print("POOL_TRIAL",json.dumps(row),flush=True)
    (out/"pool.json").write_text(json.dumps(results,indent=2))
    for threads,cross in ((1,False),(2,False),(8,False),(2,True),(8,True),(20,True)):
        groups={v:[r for r in results if r["threads"]==threads and r["cross"]==str(cross).lower() and r["variant"]==v] for v in targets}
        print("POOL_MEDIAN",threads,cross,json.dumps({v:{k:statistics.median(r[k] for r in group) for k in ("ops_per_s","cpu_ns","direct_bytes")} for v,group in groups.items()}),flush=True)
else:
    window=int(sys.argv[1]);bench.WORK["load1024"]=(1024,window,2,256,"mixed")
    args=SimpleNamespace(warmup_ms=10000,measure_ms=15000,jfr=False,profile_writes=False,sample_resources=True)
    rows=[]
    with (out/"results.csv").open("w",newline="") as f:
        writer=csv.writer(f);writer.writerow(bench.FIELDS)
        for trial in range(4):
            order=list(targets)
            if trial%2:order.reverse()
            for variant in order:
                logs=out/"logs"/variant;logs.mkdir(parents=True,exist_ok=True)
                row=bench.measure(targets[variant],work,args,trial+1,"load1024","off",logs)
                writer.writerow([*row,variant]);f.flush()
                row=dict(zip(bench.FIELDS,[*row,variant]));rows.append(row)
                print("TCP_TRIAL",json.dumps(row),flush=True)
    print("TCP_MEDIAN",window,json.dumps({v:{k:statistics.median(float(r[k]) for r in rows if r["variant"]==v) for k in ("requests_per_s","p99_us","p999_us","server_cpu_ns_per_op","server_alloc_bytes_per_op","send_rejections")} for v in targets}),flush=True)
    if any(float(r["send_rejections"])!=0 for r in rows):raise SystemExit("SEND rejected")
