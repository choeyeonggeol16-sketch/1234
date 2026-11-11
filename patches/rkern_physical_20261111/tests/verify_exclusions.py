from pathlib import Path
import argparse,re,zipfile
R=Path(__file__).resolve().parents[1]
a=argparse.ArgumentParser();a.add_argument('--src',type=Path,required=True);args=a.parse_args()
prefix='l1j/server/server/model/character/Attack/Helper/'
def methods(s,name):
 out=[]
 for m in re.finditer(r'(?:public|private)\s+static\s+[^({;]+?\s+'+name+r'\s*\([^)]*\)\s*\{',s):
  depth=1;i=m.end()
  while depth:depth+=(s[i]=='{')-(s[i]=='}');i+=1
  out.append(s[m.start():i])
 return out
bundle=R.parents[1]/'downloads/RKern_Aden_IOCP_THREADPOOL_MAGIC_FULL_SRC_20261111.zip'
with zipfile.ZipFile(bundle) as z:
 old=z.read('src/'+prefix+'L1SkillProcHelper.java').decode();new=(args.src/prefix/'L1SkillProcHelper.java').read_text();checks=[]
 for name in ['isProcDoubleBreak','rollWeaponProcMode','rollCrashFuryFlags']:
  before,after=methods(old,name),methods(new,name);assert before and before==after,name
  checks.append('PASS unchanged production method(s): '+name+' ('+str(len(before))+')')
 rel=prefix+'AttackSkillChanceConfig.java';assert z.read('src/'+rel)==(args.src/rel).read_bytes()
 checks.append('PASS entire AttackSkillChanceConfig source unchanged; no production probability/config edits.')
(R/'logs/exclusions-test.log').write_text('\n'.join(checks)+'\n');print('\n'.join(checks))
