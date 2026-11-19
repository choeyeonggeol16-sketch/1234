package l1j.server.server.utils;

/**
 * 특수 저항/관통 종류 —
 */
public enum eKind {
    ABILITY(1),       // 기술: 군주, 기사
    SPIRIT(2),        // 정령: 요정, 다크엘프
    DRAGON_SPELL(3),  // 용언: 용기사, 환술사
    FEAR(4),          // 공포: 전사
    ALL(5);           // 전체

    private final int _value;
    eKind(int v) { _value = v; }
    public int value() { return _value; }
}