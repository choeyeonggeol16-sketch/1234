"""Regression: common creature intrinsic stat readers preserve original L1 formula inputs."""
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2] / "src"
BASE = ROOT / "l1j/server/server"
character = (BASE / "model/L1Character.java").read_text(encoding="utf-8")
calc = (BASE / "utils/CalcStat.java").read_text(encoding="utf-8")
names = ("GetStatPAttack", "GetStatMCriticalProb", "GetStatCarryWeight",
         "GetStatPDefend", "GetStatMDefend", "GetStatHPRegen", "GetStatMPRegen")
methods = []
for name in names:
    match = re.search(r"public final int " + name + r"\([^)]*\)\s*\{[^}]+\}", character)
    assert match, name
    methods.append(match.group())
attack = (BASE / "model/character/action/CAttackAction.java").read_text(encoding="utf-8")
magic = (BASE / "model/L1Magic.java").read_text(encoding="utf-8")
assert "CalcStat." not in attack and "CalcStat." not in magic
assert "_pc.GetStatPAttack(_weaponType == 20 || _weaponType == 62)" in attack
assert "_npc.GetStatPAttack(rangedNpc)" in attack
assert "_pc.GetStatMCriticalProb()" in attack and "_pc.GetStatMCriticalProb()" in magic
harness = """
import l1j.server.server.utils.CalcStat;
public class IntrinsicStatTest {
    int str, dex, intel, con, wis, pureCon, pureWis, level;
    int GetTotalStatStr(){return str;}
    int GetTotalStatDex(){return dex;}
    int GetTotalStatInt(){return intel;}
    int GetTotalStatCon(){return con;}
    int GetTotalStatWis(){return wis;}
    int GetStatCon(){return pureCon;}
    int GetStatWis(){return pureWis;}
    int getLevel(){return level;}
""" + "\n".join(methods) + """
    static int checks;
    static void eq(int expected,int actual) {
        if(expected!=actual) throw new AssertionError(expected+" != "+actual);
        checks++;
    }
    public static void main(String[] args) {
        IntrinsicStatTest c = new IntrinsicStatTest();
        java.util.Random rng = new java.util.Random(11025);
        for(int i=0;i<100000;i++){
            c.str=rng.nextInt(256)-128; c.dex=rng.nextInt(256)-128;
            c.intel=rng.nextInt(256)-128; c.con=rng.nextInt(256)-128;
            c.wis=rng.nextInt(256)-128; c.pureCon=rng.nextInt(128);
            c.pureWis=rng.nextInt(128); c.level=rng.nextInt(256);
            int type=rng.nextInt(16)-4;
            eq(CalcStat.근거리대미지(c.str),c.GetStatPAttack(false));
            eq(CalcStat.원거리대미지(c.dex),c.GetStatPAttack(true));
            eq(CalcStat.마법치명타(c.intel),c.GetStatMCriticalProb());
            eq(CalcStat.최대소지무게(c.str,c.con),c.GetStatCarryWeight());
            eq(CalcStat.물리방어력(c.dex),c.GetStatPDefend());
            eq(CalcStat.마법방어(type,c.wis),c.GetStatMDefend(type));
            eq(CalcStat.피회복틱(c.pureCon),c.GetStatHPRegen());
            eq(CalcStat.naturalMpRegen(c.level,c.wis,c.pureWis),c.GetStatMPRegen());
        }
        System.out.println("PASS "+checks+" intrinsic-stat comparisons");
    }
}
"""
with tempfile.TemporaryDirectory() as tmp:
    temp = Path(tmp)
    (temp / "CalcStat.java").write_text(calc, encoding="utf-8")
    (temp / "IntrinsicStatTest.java").write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp,
                    str(temp / "CalcStat.java"), str(temp / "IntrinsicStatTest.java")], check=True)
    subprocess.run(["java", "-cp", tmp, "IntrinsicStatTest"], check=True)
