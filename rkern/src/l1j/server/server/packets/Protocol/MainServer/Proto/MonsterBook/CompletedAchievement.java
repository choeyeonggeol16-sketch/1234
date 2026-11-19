package l1j.server.server.packets.Protocol.MainServer.Proto.MonsterBook;


public class CompletedAchievement {
    public static CompletedAchievement newInstance(int achievement_id){
        CompletedAchievement achievement = new CompletedAchievement();
        achievement.set_achievement_id(achievement_id);
        achievement.set_completed_time(System.currentTimeMillis() / 1000);
        achievement.set_get_reward(false);
        return achievement;
    }

    public static CompletedAchievement newInstance(){
        return new CompletedAchievement();
    }

    private int _achievement_id;
    private long _completed_time;
    private boolean _get_reward;
    private boolean _has; 

    private CompletedAchievement(){}

    public int get_achievement_id(){
        return _achievement_id;
    }
    public void set_achievement_id(int val){
        _has = true;
        _achievement_id = val;
    }
    public boolean has_achievement_id(){
        return _has;
    }
    public long get_completed_time(){
        return _completed_time;
    }
    public void set_completed_time(long val){
        _has = true;
        _completed_time = val;
    }
    public boolean has_completed_time(){
        return _has;
    }
    public boolean get_get_reward(){
        return _get_reward;
    }
    public void set_get_reward(boolean val){
        _has = true;
        _get_reward = val;
    }
    public boolean has_get_reward(){
        return _has;
    }

    public void dispose(){
        _has = false;
    }
}