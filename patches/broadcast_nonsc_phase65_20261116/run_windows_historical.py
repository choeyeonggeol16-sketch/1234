#!/usr/bin/env python3
"""Bound the real IOCP/TCP regression runtime; always dump threads on timeout."""
import pathlib,os,subprocess,sys,time
root=pathlib.Path(sys.argv[1]).resolve()
repo=pathlib.Path.cwd()
cp=os.pathsep.join([str(root/"testclasses"),str(root/"classes"),str(repo/".compile-deps"/"*")])
cmd=["java","--enable-preview","--enable-native-access=ALL-UNNAMED",
     "-Djava.library.path="+str(root),
     "-cp",cp,"l1j.server.server.model.network.core.connection.CSharedBroadcastWindowsTest"]
print("PHASE65_NATIVE_LAUNCH",sys.platform,flush=True)
started=time.monotonic()
child=subprocess.Popen(cmd,cwd=root,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,errors="replace")
try:
    out,_=child.communicate(timeout=100)
except subprocess.TimeoutExpired:
    print("PHASE65_REAL_WINDOWS_IOCP_TIMEOUT",round(time.monotonic()-started,2),flush=True)
    try:
        dump=subprocess.run(["jcmd",str(child.pid),"Thread.print"],capture_output=True,text=True,errors="replace",timeout=20)
        print("PHASE65_JAVA_THREAD_DUMP\n"+dump.stdout[-32000:]+dump.stderr[-3000:],flush=True)
    except BaseException as failure:
        print("PHASE65_DUMP_FAILURE",repr(failure),flush=True)
    child.kill()
    out,_=child.communicate(timeout=15)
    print("PHASE65_CAPTURED_STDOUT\n"+out[-32000:],flush=True)
    raise SystemExit("PHASE65_WINDOWS_NATIVE_WATCHDOG_FAILURE")
print("PHASE65_REAL_WINDOWS_OUTPUT\n"+out[-32000:],flush=True)
print("PHASE65_NATIVE_SECONDS",round(time.monotonic()-started,2),flush=True)
if child.returncode!=0:
    raise SystemExit("PHASE65_WINDOWS_NATIVE_FAILED code="+str(child.returncode))
print("PHASE65_REAL_WINDOWS_IOCP_PASS",flush=True)
