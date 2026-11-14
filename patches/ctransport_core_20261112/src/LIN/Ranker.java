package LIN;

import java.sql.ResultSet;
import java.sql.SQLException;
import l1j.server.MJTemplate.MJClassesType.EClassesType;
import l1j.server.server.packets.Protocol.ProtoWriter;



public class Ranker {
    public static LIN.Ranker newInstance(String name, EClassesType type){
        LIN.Ranker rnk = newInstance();
        rnk.set_name(name);
        rnk.set_class_(type.toInt());
        return rnk;
    }
    
    public static LIN.Ranker newInstance(ResultSet rs) throws SQLException{
        return newInstance(rs.getString("char_name"), EClassesType.fromGfx(rs.getInt("class")));
    }
    
    public static LIN.Ranker newInstance(){
        return new LIN.Ranker();
    }
    
    private int _rating;
    private int _rank;
    private int _previous_rank;
    private int _class;
    private String _name;
    private int _uid;
    private int _score;
    private int _class_rating;
    private int _bit;
    private Ranker(){
    }
    
    public LIN.Ranker deepCopy(){
        LIN.Ranker rnk = newInstance();
        rnk.set_rating(_rating);
        rnk.set_rank(_rank);
        rnk.set_previous_rank(_previous_rank);
        rnk.set_class_(_class);
        rnk.set_name(_name);
        //rnk.set_class_rating(_class_rating);
        return rnk;
    }
    
    public int rating(){
        return _rating;
    }
    public void set_rating(int val){
        _bit |= 0x00000001;
        _rating = val;
    }
    public boolean has_rating(){
        return (_bit & 0x00000001) == 0x00000001;
    }
    public int rank(){
        return _rank;
    }
    public void set_rank(int val){
        set_previous_rank(_rank);
        _bit |= 0x00000002;
        _rank = val;
    }
    public boolean has_rank(){
        return (_bit & 0x00000002) == 0x00000002;
    }
    public int previous_rank(){
        return _previous_rank;
    }
    public void set_previous_rank(int val){
        _bit |= 0x00000004;
        _previous_rank = val;
    }
    public boolean has_previous_rank(){
        return (_bit & 0x00000004) == 0x00000004;
    }
    public int class_(){
        return _class;
    }
    public void set_class_(int val){
        _bit |= 0x00000008;
        _class = val;
    }
    public boolean has_class_(){
        return (_bit & 0x00000008) == 0x00000008;
    }
    public String name(){
        return _name;
    }
    public void set_name(String val){
        _bit |= 0x00000010;
        _name = val;
    }
    public boolean has_name(){
        return (_bit & 0x00000010) == 0x00000010;
    }
    public int uid(){
        return _uid;
    }
    public void set_uid(int val){
        _bit |= 0x00000020;
        _uid = val;
    }
    public boolean has_uid(){
        return (_bit & 0x00000020) == 0x00000020;
    }
    public int score(){
        return _score;
    }
    public void set_score(int val){
        _bit |= 0x00000040;
        _score = val;
    }
    public boolean has_score(){
        return (_bit & 0x00000040) == 0x00000040;
    }
    public int class_rating(){
        return _class_rating;
    }
    public void set_class_rating(int val){
        _bit |= 0x00000080;
        _class_rating = val;
    }
    public boolean has_class_rating(){
        return (_bit & 0x00000080) == 0x00000080;
    }
    
    /** has-bit 세팅된 필드만 방출 */
    public void writeTo(ProtoWriter w) {
        if (has_rating())        w.int32(1, _rating);
        if (has_rank())          w.int32(2, _rank);
        if (has_previous_rank()) w.int32(3, _previous_rank);
        if (has_class_())         w.int32(4, _class);
        if (has_name())          w.string(5, _name);
        if (has_uid())           w.int32(6, _uid);
        if (has_score())         w.int32(7, _score);
        if (has_class_rating())  w.int32(8, _class_rating);
    }
    
    /** dispose() 호출부 호환용 no-op — 기존 코드에서 호출하던 곳 있으면 그대로 동작 */
    public void dispose() {}
}