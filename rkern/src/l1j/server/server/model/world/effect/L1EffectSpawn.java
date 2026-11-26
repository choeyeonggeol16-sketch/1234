package l1j.server.server.model.world.effect;

import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.templates.L1Npc;

public class L1EffectSpawn {

    private static L1EffectSpawn _instance;

    private L1EffectSpawn() {
    }

    public static L1EffectSpawn getInstance() {
        if (_instance == null) {
            _instance = new L1EffectSpawn();
        }
        return _instance;
    }

    public L1EffectInstance spawnEffect(int npcid, int time, int locX, int locY, short mapId) {
        L1Npc template = CObjectDB.GetNPC(npcid);
        if (template == null) {
            return null;
        }
        L1EffectInstance effect = null;
        try {
            effect = new L1EffectInstance(template);
            effect.setId(IdFactory.getInstance().nextId());
            effect.setCurrentSprite(template.get_gfxid());
            effect.setX(locX);
            effect.setY(locY);
            effect.setHomeX(locX);
            effect.setHomeY(locY);
            effect.setHeading(0);
            effect.setMap(mapId);

            L1World.getInstance().storeAndEnterWorld(effect);

            for (L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(effect)) {
                if (pc != null && !effect.isDestroyed()) {
                    effect.onPerceive(pc);
                }
            }

            effect.startDamageTimer();

            if (template.get_gfxid() == 17235) {
                NpcEffectPlayback.action(effect, ActionCodes.ACTION_Walk, time / 2000, 2000L);
                NpcEffectPlayback.sound(effect, 7454, time / 1000, 1000L);
            }

            effect.startTimedDelete(time);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return effect;
    }

    public void doSpawnFireWall(L1Character cha, int targetX, int targetY) {
        L1Npc firewall = CObjectDB.GetNPC(81157);
        int duration = SkillsTable.getInstance().getTemplate(
                L1SkillId.FIRE_WALL).getBuffDuration();

        if (firewall == null) {
            throw new NullPointerException("FireWall data not found:npcid=81157");
        }

        L1Character base = cha;
        for (int i = 0; i < 8; i++) {
            int a = base.targetDirection(targetX, targetY);
            int x = base.getX();
            int y = base.getY();
            switch (a) {
            case 1: x++; y--; break;
            case 2: x++; break;
            case 3: x++; y++; break;
            case 4: y++; break;
            case 5: x--; y++; break;
            case 6: x--; break;
            case 7: x--; y--; break;
            case 0: y--; break;
            default: break;
            }
            if (!base.isAttackPosition(x, y, 1)) {
                x = base.getX();
                y = base.getY();
            }
            L1Map map = L1WorldMap.getInstance().getMap(cha.getMapId());
            if (map == null || !map.isInMap(x, y) || !map.isArrowPassable(x, y, cha.getHeading())) {
                break;
            }

            L1EffectInstance effect = spawnEffect(
                    81157, duration * 1000, x, y, cha.getMapId());
            if (effect == null) {
                break;
            }

            for (L1Object obj : L1World.getInstance().getClusterGrid().queryNearby(effect, 0)) {
                if (obj instanceof L1EffectInstance && obj != effect) {
                    L1EffectInstance npc = (L1EffectInstance) obj;
                    if (npc.getNpcTemplate().get_npcId() == 81157
                            && npc.getMapId() == effect.getMapId()
                            && npc.getX() == x
                            && npc.getY() == y) {
                        npc.deleteMe();
                    }
                }
            }
            if (targetX == x && targetY == y) {
                break;
            }
            base = effect;
        }
    }
}
