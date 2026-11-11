from pathlib import Path
import argparse,subprocess,tempfile,zipfile,urllib.request,concurrent.futures,hashlib,json
R=Path(__file__).resolve().parents[1]
DEPS=['net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar','it/unimi/dsi/fastutil/8.5.15/fastutil-8.5.15.jar','org/jctools/jctools-core/4.0.5/jctools-core-4.0.5.jar','com/github/ben-manes/caffeine/caffeine/3.2.2/caffeine-3.2.2.jar','com/zaxxer/HikariCP/6.3.0/HikariCP-6.3.0.jar','org/agrona/agrona/2.2.4/agrona-2.2.4.jar','org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar','javolution/javolution/5.5.1/javolution-5.5.1.jar','javax/xml/bind/jaxb-api/2.3.1/jaxb-api-2.3.1.jar','javax/activation/javax.activation-api/1.2.0/javax.activation-api-1.2.0.jar']
a=argparse.ArgumentParser();a.add_argument('--src',type=Path);a.add_argument('--libs',type=Path);args=a.parse_args()
(R/'logs').mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix='aden-magic-') as tmp:
 p=Path(tmp);src=args.src.resolve() if args.src else None
 if src is None:
  bundle=R.parents[1]/'downloads/RKern_Aden_IOCP_THREADPOOL_MAGIC_FULL_SRC_20261111.zip'
  with zipfile.ZipFile(bundle) as z:z.extractall(p/'source')
  src=p/'source/src'
 libs=args.libs.resolve() if args.libs else p/'libs';libs.mkdir(exist_ok=True)
 if args.libs is None:
  def fetch(rel):
   q=libs/rel.rsplit('/',1)[1]
   with urllib.request.urlopen('https://repo.maven.apache.org/maven2/'+rel,timeout=30) as response:q.write_bytes(response.read())
  with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:list(pool.map(fetch,DEPS))
 deps=[{'name':f.name,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in sorted(libs.glob('*.jar'))]
 (R/'logs/build-dependencies.json').write_text(json.dumps(deps,indent=2)+'\n')
 build=p/'classes';build.mkdir();cp=':'.join(str(f) for f in sorted(libs.glob('*.jar')))
 sources=sorted(src.rglob('*.java'))
 opts=['-source','21','-target','21','--enable-preview','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(build),*[str(f) for f in sources]]
 argfile=p/'compile.args';argfile.write_text('\n'.join('"'+x.replace('\\','\\\\').replace('"','\\"')+'"' for x in opts))
 with (R/'logs/full-build.log').open('w') as log:
  subprocess.run(['java','-Xmx1400m','-m','jdk.compiler/com.sun.tools.javac.Main','@'+str(argfile)],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=120)
  log.write(f'PASS full Java 21 type compilation: {len(sources)} production Java files; no game/DB/native startup.\n')
 testcp=str(build)+':'+cp
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--enable-preview','-source','21','-target','21','-cp',testcp,'-d',str(build),str(R/'tests/SpellFormulaTest.java'),str(R/'tests/MagicStackRuntimeTest.java')],check=True,timeout=30)
 for name,logname in [('SpellFormulaTest','formula-test.log'),('MagicStackRuntimeTest','stack-runtime-test.log')]:
  with (R/'logs'/logname).open('w') as log:subprocess.run(['java','--enable-preview','-cp',testcp,name],cwd=p,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=30)
 for name in ['formula-test.log','stack-runtime-test.log']:print((R/'logs'/name).read_text())
 print(f'PASS full type compilation of {len(sources)} production Java files (5 pre-existing removal warnings).')
