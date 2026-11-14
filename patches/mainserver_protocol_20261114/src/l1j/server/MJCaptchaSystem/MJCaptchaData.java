package l1j.server.MJCaptchaSystem;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.MJCaptchaSystem.Loader.MJCaptchaLoadManager;
import l1j.server.server.packets.Protocol.Packet.SC_NOTIFICATION_MESSAGE_NOT;

public class MJCaptchaData {
    public static MJCaptchaData newInstance(ResultSet rs) throws SQLException {
        return newInstance()
            .set_surf_id(rs.getInt("surfId"))
            .set_answer(rs.getInt("answer"));
    }

    public static MJCaptchaData newInstance() {
        return new MJCaptchaData();
    }

    private byte[] _cache;
    private int _surf_id;
    private int _answer;

    private MJCaptchaData() {}

    public MJCaptchaData set_surf_id(int surf_id) { _surf_id = surf_id; return this; }
    public int get_surf_id() { return _surf_id; }
    public MJCaptchaData set_answer(int answer) { _answer = answer; return this; }
    public int get_answer() { return _answer; }
    public boolean is_answer(int answer) { return _answer == answer; }

    /** 직렬화 결과 캐시 — surf_id/문구/색상/시간은 생성 시점에 고정이라 재사용 가능 */
    public byte[] get_bytes() {
        if (_cache == null) {
            _cache = SC_NOTIFICATION_MESSAGE_NOT.of(
                    _surf_id,
                    String.format("[오토방지]좌측 그림의 숫자를  입력해주세요. (%d초)", MJCaptchaLoadManager.CAPTCHA_INPUT_DURATION),
                    MJCaptchaLoadManager.CAPTCHA_INPUT_DURATION,
                    MJCaptchaLoadManager.CAPTCHA_MESSAGE_COLOR.get_bytes()) 
                .encode();
        }
        return _cache;
    }


    public void dispose() {
        _cache = null;
    }
}