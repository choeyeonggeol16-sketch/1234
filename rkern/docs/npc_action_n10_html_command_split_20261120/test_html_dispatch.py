"""Exercise the production earring selection and common HTML response tail."""
from pathlib import Path
import subprocess
import tempfile

SOURCE = Path(__file__).resolve().parents[2] / "src/l1j/server/server/model/network/core/cs/packet"


def block(file, start):
    text = (SOURCE / file).read_text(encoding="utf-8")
    offset = text.index(start)
    begin = text.index("{", offset)
    depth = 1
    end = begin + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[offset:end]


program = '''
import java.util.Arrays;

public class Check {
    static final int[] INPUTS = {21014, 21006, 21007};
    static final int[] OUTPUTS = {41176, 41177, 41178};

    static class L1Object {}
    static class L1NpcInstance extends L1Object {
        L1NpcInstance getNpcTemplate() { return this; }
        int get_npcId() { return 92; }
    }
    static class L1PcInstance {
        final int equipped, available;
        L1PcInstance(int equipped, int available) {
            this.equipped = equipped;
            this.available = available;
        }
        L1PcInstance getInventory() { return this; }
        boolean checkEquipped(int id) { return contains(equipped, id); }
        boolean checkItem(int id, int count) { return count == 1 && contains(available, id); }
        private boolean contains(int mask, int id) {
            for (int i = 0; i < INPUTS.length; i++)
                if (INPUTS[i] == id) return (mask & (1 << i)) != 0;
            throw new AssertionError(id);
        }
    }

    static String effect;
    static class NpcCraftCommands {
        %s
        static void execute(L1PcInstance p, L1Object o, int id, int[] m, int[] c,
                int[] items, int[] qty, String ok, String fail, String page, String[] data) {
            effect += "craft:" + page + ":" + data[0] + ":" + id;
        }
    }
    static class NpcGameEventActions {
        static String[] makeUbInfoStrings(int id) {
            effect += "ub:" + id + ":";
            return new String[]{"updated"};
        }
    }
    static class SC_NPCTalkReturn {
        static void send(L1PcInstance p, int id, String html, String[] data) {
            effect += "html:" + html + ":" + (data == null ? "null" : data[0]) + ":" + id;
        }
    }

    %s
    %s

    record Result(String page, int[] inputs, int[] counts, int[] outputs, int[] quantities) {}
    static Result select(L1PcInstance pc, String s) {
        String htmlid = "initial";
        int[] materials = null, counts = null, createitem = null, createcount = null;
        %s
        return new Result(htmlid, materials, counts, createitem, createcount);
    }

    static void check(boolean valid, String failure) {
        if (!valid) throw new AssertionError(failure);
    }

    public static void main(String[] args) {
        for (int equipped = 0; equipped < 8; equipped++) {
            for (int available = 0; available < 8; available++) {
                var player = new L1PcInstance(equipped, available);
                var result = select(player, "K");
                if (equipped == 0 && available != 0) {
                    int first = Integer.numberOfTrailingZeros(available);
                    check("initial".equals(result.page), "preserve initial HTML with recipe");
                    check(Arrays.equals(result.inputs, new int[]{INPUTS[first]}), "recipe input priority");
                    check(Arrays.equals(result.outputs, new int[]{OUTPUTS[first]}), "recipe output");
                    check(Arrays.equals(result.counts, new int[]{1}), "input count");
                    check(Arrays.equals(result.quantities, new int[]{1}), "output count");
                    var repeat = select(player, "K");
                    check(repeat.inputs == result.inputs && repeat.outputs == result.outputs, "reuse recipe arrays");
                } else {
                    check("perita36".equals(result.page) && result.inputs == null && result.outputs == null,
                          "equipped and missing item response");
                }
                check("initial".equals(select(player, "k").page), "preserve case sensitivity");
            }
        }

        var player = new L1PcInstance(0, 0);
        var npc = new L1NpcInstance();
        effect = "";
        finish(player, npc, 123, "colos2", null, null, null, null, null, null, new String[]{"old"});
        check(effect.equals("ub:92:html:colos2:updated:123"), "UB data resolved before reply");
        effect = "";
        finish(player, npc, 123, "colos2", null, null, new int[]{1}, null, null, null, new String[]{"old"});
        check(effect.equals("ub:92:craft:colos2:updated:123"), "craft consumes reply and preserves updated data");
        effect = "";
        finish(player, npc, 123, "", null, null, null, null, null, null, null);
        check(effect.equals("html::null:123"), "empty HTML still sends close");
        effect = "";
        finish(player, npc, 123, null, null, null, null, null, null, null, null);
        check(effect.isEmpty(), "null HTML has no output");
        System.out.println("PASS 64 earring inventory combinations and four HTML/craft finish paths");
    }
}
''' % (
    block("NpcCraftCommands.java", "static final class UnitCounts"),
    block("NpcEquipmentHtmlCommands.java", "private static final class EarringRecipes"),
    block("HtmlCmdHandler.java", "static void finish("),
    block("NpcEquipmentHtmlCommands.java", 'if (s.equals("K"))'),
)
with tempfile.TemporaryDirectory() as work:
    path = Path(work) / "Check.java"
    path.write_text(program, encoding="utf-8")
    subprocess.run(["javac", "--release", "21", "-encoding", "UTF-8", str(path)], check=True)
    subprocess.run(["java", "-cp", work, "Check"], check=True)
