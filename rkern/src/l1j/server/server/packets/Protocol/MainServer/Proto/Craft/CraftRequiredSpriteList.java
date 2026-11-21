package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;
import java.util.ArrayList;

import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/**
 * 제작 필요 스프라이트 조건
 * 1 and_or(bool) / 2 count / 3 sprites(repeated msg: sprite_id / count)
 *
 * ⚠ CraftRequiredSprite 서브메시지 필드번호는 server 원본 CraftRequiredSpriteList의
 *    내부 클래스 writeTo 기준으로 아래 SPRITE_FIELD_* 상수 확인 필요 (미확정 표시)
 */
public class CraftRequiredSpriteList {

    // ⚠ server 원본 내부 CraftRequiredSprite의 writeTo에서 번호 확인 (아마 1=sprite_id, 2=count)
    private static final int SPRITE_FIELD_ID	= 1;
    private static final int SPRITE_FIELD_COUNT	= 2;

    private boolean _andOr;
    private int _count;
    private ArrayList<int[]> _sprites = new ArrayList<int[]>();	// [0]=spriteId, [1]=count

    // getter/setter
    public boolean get_andOr(){ return _andOr; }
    public void set_andOr(boolean v){ _andOr = v; }
    public int get_count(){ return _count; }
    public void set_count(int v){ _count = v; }
    public ArrayList<int[]> get_sprites(){ return _sprites; }
    public void add_sprite(int spriteId, int spriteCount){
        _sprites.add(new int[]{ spriteId, spriteCount });
    }

    // ── 역직렬화 ──
    public static CraftRequiredSpriteList parse(ProtoInputStream in) throws IOException {
        CraftRequiredSpriteList s = new CraftRequiredSpriteList();
        while(!in.isAtEnd()){
            int tag = in.readTag();
            switch(tag >>> 3){
            case 1: s._andOr = in.readBool(); break;
            case 2: s._count = in.readInt32(); break;
            case 3: parseSprite(in, s); break;
            default: in.skipField(tag); break;
            }
        }
        return s;
    }

    private static void parseSprite(ProtoInputStream in, CraftRequiredSpriteList target) throws IOException {
        byte[] b = in.readBytes();
        ProtoInputStream sub = ProtoInputStream.newInstance(b == null ? new byte[0] : b);
        int spriteId = 0, spriteCount = 0;
        while(!sub.isAtEnd()){
            int tag = sub.readTag();
            switch(tag >>> 3){
            case SPRITE_FIELD_ID: spriteId = sub.readInt32(); break;
            case SPRITE_FIELD_COUNT: spriteCount = sub.readInt32(); break;
            default: sub.skipField(tag); break;
            }
        }
        target.add_sprite(spriteId, spriteCount);
    }

    // ── 직렬화 ──
    public void writeTo(ProtoWriter w){
        w.bool(1, _andOr);
        w.int32(2, _count);
        for(int[] spr : _sprites){
            w.msg(3);
            w.int32(SPRITE_FIELD_ID, spr[0]);
            w.int32(SPRITE_FIELD_COUNT, spr[1]);
            w.end();
        }
    }
}