package l1j.server.server.templates;

public class L1MobSkill implements Cloneable {

    public static final int TYPE_NONE = 0;
    public static final int TYPE_PHYSICAL_ATTACK = 1;
    public static final int TYPE_MAGIC_ATTACK = 2;
    public static final int TYPE_SUMMON = 3;
    public static final int TYPE_POLY = 4;

    public static final int CHANGE_TARGET_NO = 0;
    public static final int CHANGE_TARGET_COMPANION = 1;
    public static final int CHANGE_TARGET_ME = 2;
    public static final int CHANGE_TARGET_RANDOM = 3;

    private int mobid;
    private String mobName;

    /**
     * 서버 부팅 시 MobSkillTable이 mobId별로 한 번만 만든 공용 정의.
     * NPC 인스턴스에서는 이 배열을 복제하지 않고 그대로 참조한다.
     */
    private final SkillEntry[] _skills;

    public static class SkillEntry implements Cloneable {
        public int type;
        public int triRnd;
        public int triHp;
        public int triCompanionHp;
        public int triRange;
        public int triCount;
        public int changeTarget;
        public int range;
        public int areaWidth;
        public int areaHeight;
        public int leverage;
        public int skillId;
        public int gfxid;
        public int actid;
        public int summon;
        public int summonMin;
        public int summonMax;
        public int polyId;

        @Override
        public SkillEntry clone() {
            try {
                return (SkillEntry) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new InternalError(e.getMessage());
            }
        }
    }

    public L1MobSkill(int sSize) {
        _skills = new SkillEntry[sSize];
        for (int i = 0; i < sSize; i++) {
            _skills[i] = new SkillEntry();
        }
    }

    public int getSkillSize() {
        return _skills.length;
    }

    /**
     * 공용 스킬 정의 배열을 그대로 반환한다.
     *
     * hot path에서 getter 18개를 다시 호출하거나 SkillEntry를 복제하지 않기 위한
     * 저수준 read-only view다. MobSkillTable load 완료 후에는 runtime에서 수정하지 않는다.
     */
    public SkillEntry[] getSkills() {
        return _skills;
    }

    @Override
    public L1MobSkill clone() {
        try {
            L1MobSkill clone = (L1MobSkill) super.clone();

            clone.cloneSkills(_skills);
            return clone;
        } catch (Exception e) {
            e.printStackTrace();
            throw new InternalError(e.getMessage());
        }
    }

    private void cloneSkills(SkillEntry[] source) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] != null) {
                _skills[i] = source[i].clone();
            }
        }
    }

    public int get_mobid() {
        return mobid;
    }

    public void set_mobid(int i) {
        mobid = i;
    }

    public String getMobName() {
        return mobName;
    }

    public void setMobName(String s) {
        mobName = s;
    }

    private SkillEntry getEntry(int idx) {
        if (idx < 0 || idx >= _skills.length) {
            return null;
        }
        return _skills[idx];
    }

    public int getType(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.type : 0;
    }

    public void setType(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.type = i;
        }
    }

    public int getTriggerRandom(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.triRnd : 0;
    }

    public void setTriggerRandom(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.triRnd = i;
        }
    }

    public int getTriggerHp(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.triHp : 0;
    }

    public void setTriggerHp(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.triHp = i;
        }
    }

    public int getTriggerCompanionHp(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.triCompanionHp : 0;
    }

    public void setTriggerCompanionHp(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.triCompanionHp = i;
        }
    }

    public int getTriggerRange(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.triRange : 0;
    }

    public void setTriggerRange(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.triRange = i;
        }
    }

    public boolean isTriggerDistance(int idx, int distance) {
        int triggerRange = getTriggerRange(idx);

        if ((triggerRange < 0 && distance <= Math.abs(triggerRange))
                || (triggerRange > 0 && distance >= triggerRange)) {
            return true;
        }
        return false;
    }

    public int getTriggerCount(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.triCount : 0;
    }

    public void setTriggerCount(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.triCount = i;
        }
    }

    public int getChangeTarget(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.changeTarget : 0;
    }

    public void setChangeTarget(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.changeTarget = i;
        }
    }

    public int getRange(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.range : 0;
    }

    public void setRange(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.range = i;
        }
    }

    public int getAreaWidth(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.areaWidth : 0;
    }

    public void setAreaWidth(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.areaWidth = i;
        }
    }

    public int getAreaHeight(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.areaHeight : 0;
    }

    public void setAreaHeight(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.areaHeight = i;
        }
    }

    public int getLeverage(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.leverage : 0;
    }

    public void setLeverage(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.leverage = i;
        }
    }

    public int getSkillId(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.skillId : 0;
    }

    public void setSkillId(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.skillId = i;
        }
    }

    public int getGfxid(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.gfxid : 0;
    }

    public void setGfxid(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.gfxid = i;
        }
    }

    public int getActid(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.actid : 0;
    }

    public void setActid(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.actid = i;
        }
    }

    public int getSummon(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.summon : 0;
    }

    public void setSummon(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.summon = i;
        }
    }

    public int getSummonMin(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.summonMin : 0;
    }

    public void setSummonMin(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.summonMin = i;
        }
    }

    public int getSummonMax(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.summonMax : 0;
    }

    public void setSummonMax(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.summonMax = i;
        }
    }

    public int getPolyId(int idx) {
        SkillEntry e = getEntry(idx);
        return e != null ? e.polyId : 0;
    }

    public void setPolyId(int idx, int i) {
        SkillEntry e = getEntry(idx);
        if (e != null) {
            e.polyId = i;
        }
    }
}
