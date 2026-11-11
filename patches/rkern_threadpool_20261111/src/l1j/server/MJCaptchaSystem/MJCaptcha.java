package l1j.server.MJCaptchaSystem;

import l1j.server.MJCaptchaSystem.Loader.MJCaptchaDataLoader;
import l1j.server.MJCaptchaSystem.Loader.MJCaptchaLoadManager;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class MJCaptcha {
    private static final int MESSAGE_IDX_SUCCESS = 0;
    private static final int MESSAGE_IDX_FAIL = 1;

    private static final String[] _messages = {
        "오토방지 코드 인증에 성공했습니다.",
        "오토방지 코드 인증에 실패했습니다."
    };

    public static MJCaptcha newInstance(int character_object_id){
        return newInstance()
                .set_character_object_id(character_object_id);
    }

    public static MJCaptcha newInstance(){
        return new MJCaptcha();
    }

    private int _character_object_id;
    private int _fail_count;
    private int _relay_count;
    private boolean _is_answer;
    private long _next_show_millis;
    private MJCaptchaData _c_data;
    private MJCaptcha(){
        _is_answer = false;
    }

    public MJCaptcha set_character_object_id(int character_object_id){
        _character_object_id = character_object_id;
        return this;
    }

    public int get_character_object_id(){
        return _character_object_id;
    }

    public MJCaptcha set_fail_count(int fail_count){
        _fail_count = fail_count;
        return this;
    }

    public int get_fail_count(){
        return _fail_count;
    }

    public int inc_fail_count(){
        return ++_fail_count;
    }

    public MJCaptcha set_relay_count(int relay_count){
        _relay_count = relay_count;
        return this;
    }

    public int get_relay_count(){
        return _relay_count;
    }

    public int inc_relay_count(){
        return ++_relay_count;
    }

    public MJCaptcha set_is_answer(boolean is_answer){
        _is_answer = is_answer;
        return this;
    }

    public boolean is_answer(){
        return _is_answer;
    }

    public boolean is_keep_captcha(){
        return _c_data != null;
    }

    public MJCaptcha set_c_data(MJCaptchaData c_data){
        _c_data = c_data;
        return this;
    }

    public MJCaptchaData get_c_data(){
        return _c_data;
    }

    public MJCaptcha set_next_show_millis(long next_show_millis){
        _next_show_millis = next_show_millis;
        return this;
    }

    public long get_next_show_millis(){
        return _next_show_millis;
    }

    public long update_next_show_millis(){
        return (_next_show_millis = System.currentTimeMillis() + MJCaptchaLoadManager.CAPTCHA_DELAY_MILLIS);
    }

    public boolean is_pass_captcha(){
        return _next_show_millis > System.currentTimeMillis();
    }

    public void auth_captcha(L1PcInstance pc, int answer){
        set_is_answer(true);
        set_relay_count(0);
        if(_c_data.is_answer(answer)){
            do_success(pc);
        }else{
            do_fail(pc);
        }
        set_c_data(null);
    }

    public void do_success(L1PcInstance pc){
        set_fail_count(0);
        SC_SystemMessage.send(pc, _messages[MESSAGE_IDX_SUCCESS]);
        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, _messages[MESSAGE_IDX_SUCCESS]);
    }

    public void do_fail(L1PcInstance pc){
        if(inc_fail_count() >= MJCaptchaLoadManager.CAPTCHA_FAIL_COUNT){
            set_relay_count(0);
            set_c_data(null);
            MJCaptchaLoadManager.getInstance().do_assert(pc);
        }
        SC_SystemMessage.send(pc, _messages[MESSAGE_IDX_FAIL]);
        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, _messages[MESSAGE_IDX_FAIL]);
    }

    public void drain_captcha(L1PcInstance pc){
        drain_captcha(pc, MJCaptchaDataLoader.getInstance().to_rand_captcha());
    }

    public void drain_captcha(L1PcInstance pc, MJCaptchaData c_data){
        update_next_show_millis();
        set_c_data(c_data);
        set_is_answer(false);
        set_relay_count(0);
        l1j.server.server.packets.Protocol.Proto.sendRaw(pc, c_data.get_bytes());
        ThreadPool.getInstance().scheduleRequired(new Runnable(){
            @Override
            public void run(){
                if(is_answer()){
                    set_is_answer(false);
                    if(get_fail_count() > 0 && !MJCaptchaLoadManager.getInstance().is_in_assert(pc)){
                        drain_captcha(pc);
                    }
                }else{
                    do_fail(pc);
                    if(!MJCaptchaLoadManager.getInstance().is_in_assert(pc))
                        drain_captcha(pc);
                }
            }
        }, MJCaptchaLoadManager.CAPTCHA_INPUT_DURATION * 1000L);
    }
}