from pathlib import Path
import platform
import subprocess
import sys
import tempfile
from run_bench import HERE, Process

native = "WinIocpCore" if platform.system() == "Windows" else "NcFfmCore"
programs = sys.argv[1:] or ["SelectorCore",native]
java = ["java","--enable-preview","--enable-native-access=ALL-UNNAMED","-cp",str(HERE/"classes")]
for name in programs:
    with tempfile.TemporaryDirectory() as tmp:
        server=Process([*java,name,"258","32","2"], Path(tmp)/"server.log")
        try:
            ready=server.line(10).split()
            assert ready[0]=="READY", ready
            subprocess.run([*java,"CoreFrameCheck",ready[1]],check=True,timeout=20)
            server.proc.stdin.write("STOP\n")
            server.proc.stdin.flush()
            result=server.line(15)
            assert "accepted=3 closed=3 frames=44" in result, result
            assert server.proc.wait(timeout=5)==0
            print(name,result,flush=True)
        finally:
            server.close()
            print((Path(tmp)/"server.log").read_text(),file=sys.stderr)
