package l1j.server.server.model.inventory;

/**
 * Item profiles for the equipment option interpreter.
 *
 * Header: @ item IDs (or *) followed by an accessory category and optional blessing state.
 * Row: inclusive enchant range followed by ordered STAT:base[:perEnchant] entries.
 * The three @ notification entries retain the position of their legacy client updates.
 * A star at either range endpoint means that endpoint is unbounded.
 */
final class EquipmentOptionData {
    private EquipmentOptionData() {}

    static final String[] WEAPON = {
        """
        @ 12
        1..*  ALL_PLERCE:0:1

        @ 59,617,11121
        *..*  ABILITY_PLERCE:5

        @ 61
        *..-1  ABILITY_PLERCE:8, MELEE_CRITICAL_RATE:1
        0..*  ABILITY_PLERCE:8:1, MELEE_CRITICAL_RATE:1:1

        @ 66
        *..*  MELEE_DAMAGE_UP:0:2

        @ 86
        *..-1  SPIRIT_PLERCE:5
        0..*  SPIRIT_PLERCE:5:1

        @ 118
        9..15  CHARISMA:1

        @ 124
        7..9  SP:-6:1, @SPMR
        10..15  SP:3, @SPMR

        @ 134
        *..-1  SP:7, MAGIC_HIT:2, @SPMR
        0..*  SP:7:1, MAGIC_HIT:2:1, @SPMR

        @ 147
        *..*  DMG_RATE:0:1

        @ 541
        0  ABILITY_RESIS:10

        @ 542
        0  DMG_RED:2

        @ 543
        0  HIT:2

        @ 544
        0  HIT:10

        @ 545
        0  HIT:17

        @ 547
        0  HIT:11

        @ 616,203006
        8..10  FEAR_PLERCE:-7:1
        11..15  FEAR_PLERCE:3

        @ 618,203017
        7..10  DRAGON_SPELL_PLERCE:-6:1
        11..15  DRAGON_SPELL_PLERCE:4

        @ 620
        0..15  BASE_MAGIC_CRITICAL:5

        @ 622,1136
        *..*  SPIRIT_PLERCE:3

        @ 1134,101134
        1..*  MPR:0:1

        @ 14012
        *..0  DMG_RED:1
        1..*  DMG_RED:1, ALL_PLERCE:0:1

        @ 14061
        *..-1  DMG_RED:1, ABILITY_PLERCE:8, MELEE_CRITICAL_RATE:1
        0..*  DMG_RED:1, ABILITY_PLERCE:8:1, MELEE_CRITICAL_RATE:1:1

        @ 14086
        *..-1  DMG_RED:1, SPIRIT_PLERCE:5
        0..*  DMG_RED:1, SPIRIT_PLERCE:5:1

        @ 14134
        *..-1  DMG_RED:2, SP:7, MAGIC_HIT:2, @SPMR
        0..*  DMG_RED:2, SP:7:1, MAGIC_HIT:2:1, @SPMR

        @ 123001
        0..8  FEAR_PLERCE:2, ABILITY_PLERCE:2, SPIRIT_PLERCE:2
        9..11  FEAR_PLERCE:-6:1, ABILITY_PLERCE:-6:1, SPIRIT_PLERCE:-6:1
        12..*  FEAR_PLERCE:5, ABILITY_PLERCE:5, SPIRIT_PLERCE:5

        @ 123002
        0..7  ABILITY_PLERCE:5
        8..10  ABILITY_PLERCE:-2:1
        11..*  ABILITY_PLERCE:8

        @ 123003
        9..11  SPIRIT_PLERCE:-8:1
        12..*  SPIRIT_PLERCE:3

        @ 123004
        0..8  SPIRIT_PLERCE:2
        9..11  SPIRIT_PLERCE:-6:1
        12..*  SPIRIT_PLERCE:5

        @ 123005
        0..8  MAGIC_HIT:1
        9..11  MAGIC_HIT:-7:1
        12..*  MAGIC_HIT:4

        @ 123006
        0..8  FEAR_PLERCE:2
        9..11  FEAR_PLERCE:-6:1
        12..*  FEAR_PLERCE:5

        @ 123007
        0..8  DRAGON_SPELL_PLERCE:5
        9..11  DRAGON_SPELL_PLERCE:-3:1
        12..*  DRAGON_SPELL_PLERCE:8

        @ 123008
        0..8  DRAGON_SPELL_PLERCE:5, SP:1
        9..11  DRAGON_SPELL_PLERCE:5, SP:-7:1
        12..*  DRAGON_SPELL_PLERCE:5, SP:4

        @ 123999
        *..*  MELEE_CRITICAL_RATE:0:1, FEAR_PLERCE:3:1, ABILITY_PLERCE:3:1, SPIRIT_PLERCE:3:1

        @ 202011,2202011
        *..0  SPIRIT_PLERCE:5, BOW_CRITICAL_RATE:1, DMG_RED_IGNORE:9, DMG_RED:2
        1..*  SPIRIT_PLERCE:5:1, DMG_RED_IGNORE:9:1, BOW_CRITICAL_RATE:1:1, DMG_RED:2

        @ 202012
        *..-1  SP:2, DRAGON_SPELL_PLERCE:7, MAGIC_CRITICAL_RATE:1, @SPMR
        0..*  SP:2:1, DRAGON_SPELL_PLERCE:7:1, MAGIC_CRITICAL_RATE:1:1, @SPMR

        @ 202013
        *..-1  MELEE_CRITICAL_RATE:1, DRAGON_SPELL_PLERCE:7
        0..*  MELEE_CRITICAL_RATE:1:1, DRAGON_SPELL_PLERCE:7:1

        @ 202014
        *..-1  MELEE_CRITICAL_RATE:1, FEAR_PLERCE:3
        0..*  MELEE_CRITICAL_RATE:1:1, FEAR_PLERCE:3:1

        @ 505015,7000223
        0..6  BASE_MAGIC_CRITICAL:1
        7..10  DRAGON_SPELL_PLERCE:-6:1, BASE_MAGIC_CRITICAL:-5:1
        11..15  DRAGON_SPELL_PLERCE:4, BASE_MAGIC_CRITICAL:5

        @ 2202012
        *..-1  DMG_RED:2, SP:2, DRAGON_SPELL_PLERCE:7, MAGIC_CRITICAL_RATE:1, @SPMR
        0..*  DMG_RED:2, SP:2:1, DRAGON_SPELL_PLERCE:7:1, MAGIC_CRITICAL_RATE:1:1, @SPMR

        @ 2202013
        *..-1  DMG_RED:1, MELEE_CRITICAL_RATE:1, DRAGON_SPELL_PLERCE:7
        0..*  DMG_RED:1, MELEE_CRITICAL_RATE:1:1, DRAGON_SPELL_PLERCE:7:1

        @ 2202014
        *..-1  DMG_RED:1, MELEE_CRITICAL_RATE:1, FEAR_PLERCE:3
        0..*  DMG_RED:1, MELEE_CRITICAL_RATE:1:1, FEAR_PLERCE:3:1

        @ 7000214,7000215,7000216,7000217,7000218,7000219,7000220
        0..7  PVP_DMG:3
        8..9  PVP_DMG:-11:2
        10..15  PVP_DMG:10

        @ 7000221
        *..-1  FEAR_PLERCE:5
        0..7  FEAR_PLERCE:5, PVP_DMG:3
        8..9  FEAR_PLERCE:5, PVP_DMG:-11:2
        10..15  FEAR_PLERCE:5, PVP_DMG:10
        16..*  FEAR_PLERCE:5

        """,
    };

    static final String[] ARMOR_PRIMARY = {
        """
        @ 20050
        5..9  EVASION:-4:1, @ER
        10..*  EVASION:5, @ER

        @ 20051
        0..*  MR:4:2

        @ 20272,222342
        *..*  BOW_HIT:3

        @ 22263
        5..*  MAX_HP:-80:20, ABILITY_PLERCE:-4:1

        @ 22360
        0..4  MR:1
        5..*  ABILITY_RESIS:-4:1, MR:-7:2

        @ 30184
        *..*  HIT:2

        @ 474700
        0..4  MR:1:2, FEAR_RESIS:5, ABILITY_RESIS:5
        5..9  MR:1:2, FEAR_RESIS:1:1, ABILITY_RESIS:1:1
        10  MR:21, FEAR_RESIS:10, ABILITY_RESIS:10

        @ 565611
        0..5  MR:12, DMG_RED:1, DRAGON_SPELL_RESIS:1, SPIRIT_RESIS:1, FEAR_RESIS:1, ABILITY_RESIS:12
        6..*  MR:7:1, DMG_RED:-4:1, DRAGON_SPELL_RESIS:-4:1, SPIRIT_RESIS:-4:1, FEAR_RESIS:-4:1, ABILITY_RESIS:7:1, PVP_DMG:-5:1

        @ 700117
        *..4  SP:2:1, PVP_DMG:2:1, PVP_DMG_RED:2:1, MR:3:1, MAGIC_HIT:1:1, @AC, @SPMR
        5..*  SP:2:1, PVP_DMG:2:1, PVP_DMG_RED:2:1, MR:3:1, MAGIC_HIT:1:1, @AC, @SPMR, ALL_RESIS:-4:1, ALL_PLERCE:-4:1

        @ 808011
        0  MR:12
        1  AC:-6, MR:26
        2  AC:-6, MAX_MP:10, MAX_HP:20, MR:28
        3..4  AC:-6, MAX_MP:-10:10, MAX_HP:-20:20, MR:24:2, ALL_RESIS:0:1
        5..*  AC:-6, MAX_MP:40, MAX_HP:80, MR:34, ALL_RESIS:5, EVASION:4, @ER

        @ 900116
        *..5  PVP_DMG:3, PVP_DMG_RED:1, MR:10, MAX_HP:100
        6..7  PVP_DMG:-2:1, PVP_DMG_RED:-4:1, MR:6:1, MAX_HP:60:10
        8  PVP_DMG:6, PVP_DMG_RED:4, MR:15, MAX_HP:150

        """,
    };

    static final String[] ARMOR_SLOT = {
        """
        @ 20016,20112,120016,120112
        7..15  CHARISMA:1

        @ 20049,20050,900056,900057
        0..5  SPIRIT_RESIS:3
        6..9  SPIRIT_RESIS:-2:1
        10..15  SPIRIT_RESIS:7

        @ 20079
        0..5  FEAR_RESIS:2
        6..10  FEAR_RESIS:-3:1
        11..15  FEAR_RESIS:7

        @ 20235,22200,22201,22202,22203
        0..5  DRAGON_SPELL_RESIS:2
        6..10  DRAGON_SPELL_RESIS:-3:1

        @ 20274
        0..1  WEIGHT:300
        3..4  WEIGHT:300
        5..*  WEIGHT:60:60

        @ 20279
        *..*  PVP_DMG_RED:2

        @ 20314
        *..*  PVP_DMG:2

        @ 22000
        0..4  BOW_HIT:1
        5..6  BOW_HIT:2
        7..8  BOW_HIT:3
        9..10  BOW_HIT:4

        @ 22003
        0..4  DMG_RATE:1
        5..6  DMG_RATE:2
        7..8  DMG_RATE:3
        9..10  DMG_RATE:4

        @ 22196,22197,22198,22199
        0..5  DRAGON_SPELL_RESIS:2
        6..10  DMG_RED:-3:1, DRAGON_SPELL_RESIS:-3:1

        @ 22204,22205,22206,22207
        0..4  DRAGON_SPELL_RESIS:2
        5..11  DRAGON_SPELL_RESIS:-2:1
        12..*  DRAGON_SPELL_RESIS:9

        @ 22208,22209,22210,22211
        0..5  DRAGON_SPELL_RESIS:2
        6..10  PVP_DMG_RED:-3:1, DRAGON_SPELL_RESIS:-3:1

        @ 22214
        0..5  SPIRIT_RESIS:3
        6  SPIRIT_RESIS:4
        7..10  SPIRIT_RESIS:-2:1, MAGIC_HIT:-6:1
        11..*  MAGIC_HIT:-6:1

        @ 22254
        0..4  DMG_RED:1
        5..6  DMG_RED:2
        7..8  DMG_RED:3
        9..10  DMG_RED:4

        @ 22255
        5..6  SP:1
        7..8  SP:2
        9..10  SP:3

        @ 22256
        5..6  MAX_HP:25
        7..8  MAX_HP:50
        9..10  MAX_HP:75

        @ 22359,222307,222308,222309
        7..8  MAX_HP:-120:20
        9..10  MAX_HP:60, PVP_DMG_RED:1

        @ 123111
        0..*  BASE_MAGIC_CRITICAL:5, DMG_RED:7:2

        @ 123112
        0..*  BOW_CRITICAL_RATE:5, DMG_RED:7:2

        @ 123113
        0..*  MELEE_CRITICAL_RATE:5, DMG_RED:7:2

        @ 202022
        0..5  SPIRIT_RESIS:2
        6..10  SPIRIT_RESIS:-3:1
        11..15  SPIRIT_RESIS:7

        @ 222317
        7..*  HIT:-3:1

        @ 900019
        0..5  MR:12, DMG_RED:1, ALL_RESIS:1, ABILITY_RESIS:11
        6..10  MR:7:1, DMG_RED:-4:1, ALL_RESIS:-4:1, ABILITY_RESIS:11
        11..15  MR:17, DMG_RED:6, ALL_RESIS:6, ABILITY_RESIS:11

        @ 900020,900021,900099
        0..10  AC:0:1

        @ 900023
        0..9  MAX_HP:10:5

        @ 900024
        0..10  MAGIC_HIT:3

        @ 900025
        0..5  MR:4
        6  MR:5
        7  DMG_RED:1
        8..9  MR:-16:3, DMG_RED:-7:1
        10..15  MR:14, DMG_RED:2, MAX_HP:100, PVP_DMG:1, PVP_DMG_RED:1

        @ 900026
        0..4  DMG_RATE:1
        5..7  DMG_RATE:1, ABILITY_RESIS:3:1
        8..9  DMG_RATE:-7:1, HIT:-14:2, ABILITY_RESIS:-12:3
        10..15  DMG_RATE:2, HIT:6, ABILITY_RESIS:18, MAX_HP:100, PVP_DMG:1, PVP_DMG_RED:1

        @ 900027
        0..4  BOW_HIT:1
        5..7  BOW_HIT:1, ABILITY_RESIS:3:1
        8..9  BOW_HIT:-7:1, BOW_HIT:-6:1, ABILITY_RESIS:-12:3
        10..15  BOW_HIT:2, BOW_HIT:6, MAX_HP:100, PVP_DMG:1, PVP_DMG_RED:1, ABILITY_RESIS:18

        @ 900028
        0..4  SP:1
        5..7  SP:1, ABILITY_RESIS:3:1
        8..9  SP:2, MAGIC_HIT:-15:2, ABILITY_RESIS:-12:3
        10..15  SP:3, MAGIC_HIT:4, ABILITY_RESIS:18, MAX_HP:100, PVP_DMG:1, PVP_DMG_RED:1

        @ 900030
        9..12  DMG_RATE:1

        @ 900031
        9..12  BOW_HIT:1

        @ 900032
        9..12  SP:1

        @ 900035,900038,900054,900072
        5..10  MR:-16:4

        @ 900036,900073
        5..9  HIT:-4:1
        10..*  HIT:5

        @ 900037,900074
        5..9  BOW_HIT:-4:1
        10..*  BOW_HIT:5

        @ 900039
        0..10  HIT:2

        @ 900040
        0..10  BOW_HIT:2

        @ 900041,900114
        0..10  MAGIC_HIT:2

        @ 900049,900093,900096
        0..4  AC:0:1
        5  HIT:1, AC:5
        6..10  HIT:-5:1, DMG_RATE:-5:1, AC:0:1

        @ 900050,900094,900097
        0..4  AC:0:1
        5  BOW_HIT:1, AC:5
        6..10  BOW_HIT:-5:1, BOW_DAMAGE_UP:-5:1, AC:0:1

        @ 900051,900095,900098
        0..4  AC:0:1
        5  MAGIC_HIT:1, AC:5
        6..10  MAGIC_HIT:-5:1, SP:-5:1, AC:0:1

        @ 900076
        0..6  CHARISMA:1
        7..9  CHARISMA:-5:1
        10..15  CHARISMA:4

        @ 900080
        0..15  MAGIC_HIT:1

        @ 900081
        0..3  MAX_HP:5:5
        4  AC:-1, MAX_HP:25
        5  AC:-3, MAX_HP:30, HIT:1, DMG_RED:1, PVP_DMG_RED:1, BLESS_CONSUMO:5
        6  AC:-5, MAX_HP:35, HIT:1, DMG_RED:2, MR:3, DMG_RATE:2, PVP_DMG_RED:2, BLESS_CONSUMO:10, MELEE_CRITICAL_RATE:1
        7..9  AC:1:-1, MAX_HP:-30:10, DMG_RED:-4:1, MR:-9:2, DMG_RATE:-4:1, HIT:-11:2, PVP_DMG_RED:-11:2, BLESS_CONSUMO:-20:5, MELEE_CRITICAL_RATE:-11:2

        @ 900082
        0..3  MAX_HP:5:5
        4  AC:-1, MAX_HP:25
        5  AC:-3, MAX_HP:30, BOW_HIT:1, DMG_RED:1, PVP_DMG_RED:1, BLESS_CONSUMO:5
        6..7  AC:1:-1, MAX_HP:5:5, DMG_RED:-4:1, MR:-9:2, BOW_DAMAGE_UP:-4:1, BOW_HIT:-11:2, PVP_DMG_RED:-4:1, BLESS_CONSUMO:-20:5, BOW_CRITICAL_RATE:-11:2
        8..9  AC:1:-1, MAX_HP:-30:10, DMG_RED:-4:1, MR:-9:2, BOW_DAMAGE_UP:-4:1, BOW_HIT:-11:2, PVP_DMG_RED:-11:2, BLESS_CONSUMO:-20:5, BOW_CRITICAL_RATE:-11:2

        @ 900083
        0..3  MAX_HP:5:5
        4  AC:-1, MAX_HP:25
        5  AC:-3, MAX_HP:30, DMG_RED:1, HIT:1, PVP_DMG_RED:1, BLESS_CONSUMO:5
        6..7  AC:1:-1, MAX_HP:5:5, DMG_RED:-4:1, MR:-9:2, HIT:-4:1, BASE_MAGIC_CRITICAL:-5:1, PVP_DMG_RED:-4:1, BLESS_CONSUMO:-20:5
        8..9  AC:1:-1, MAX_HP:-30:10, DMG_RED:-4:1, MR:-9:2, HIT:-4:1, BASE_MAGIC_CRITICAL:-5:1, PVP_DMG_RED:-11:2, BLESS_CONSUMO:-20:5

        @ 900084
        0..2  MAX_HP:5:5
        3..4  AC:2:-1, MAX_HP:5:5
        5  AC:-3, MAX_HP:30, DMG_RED:1
        6..7  AC:1:-1, MAX_HP:5:5, DMG_RED:-4:1, MR:-9:2
        8  AC:-7, MAX_HP:50, DMG_RED:4, MR:7

        @ 900117
        0..4  DMG_RATE:1
        5  DMG_RATE:1, HIT:1
        6..10  DMG_RATE:-5:1, HIT:-4:1, ALL_RESIS:-5:1

        @ 900118
        0..4  BOW_DAMAGE_UP:1
        5  BOW_DAMAGE_UP:1, BOW_HIT:1
        6..10  BOW_DAMAGE_UP:-5:1, BOW_HIT:-4:1, ALL_RESIS:-5:1

        @ 900119
        0..4  SP:1
        5  SP:1, MAGIC_HIT:1
        6..10  SP:-5:1, MAGIC_HIT:-4:1, ALL_RESIS:-5:1

        @ 900120
        0..4  PVP_DMG_RED:1
        5..6  PVP_DMG_RED:1, MR:-3:1
        7..10  PVP_DMG_RED:-5:1, MR:-3:1

        @ 900152
        0..2  MAX_HP:5:5
        4  AC:-1, MAX_HP:25
        5  AC:-2, MAX_HP:30, DMG_RATE:1
        6..7  AC:-3, MAX_HP:5:5, DMG_RATE:-4:1, MELEE_CRITICAL_RATE:-11:2
        8  AC:-3, DMG_RATE:4, MELEE_CRITICAL_RATE:5

        @ 900153
        0..2  MAX_HP:5:5
        4  AC:-1, MAX_HP:25
        5  AC:-2, MAX_HP:30, BOW_DAMAGE_UP:1
        6..7  AC:-3, MAX_HP:5:5, BOW_DAMAGE_UP:-4:1, BOW_CRITICAL_RATE:-11:2
        8  AC:-3, MAX_HP:50, BOW_DAMAGE_UP:4, BOW_CRITICAL_RATE:5

        @ 900154
        0..2  MAX_HP:5:5
        4  AC:-1, MAX_HP:25
        5  AC:-2, MAX_HP:30, HIT:1
        6..7  AC:-3, MAX_HP:5:5, HIT:-4:1, BASE_MAGIC_CRITICAL:-5:1
        8  AC:-3, MAX_HP:50, HIT:4, BASE_MAGIC_CRITICAL:4

        @ 900155
        5..9  ALL_RESIS:-4:1
        10..15  ALL_RESIS:5

        @ 900156
        0..6  SP:1
        7..8  SP:-5:1
        9..15  SP:3

        @ 900157
        0..4  HIT:3
        5..9  HIT:-1:1, ALL_RESIS:-4:1
        10..15  HIT:8, ALL_RESIS:5

        @ 900158
        0..4  BOW_HIT:3
        5..9  BOW_HIT:-1:1, ALL_RESIS:-4:1
        10..15  BOW_HIT:8, ALL_RESIS:5

        @ 900159
        5  HIT:1
        6..9  BOW_HIT:-4:1
        10..15  BOW_HIT:5

        @ 900160
        5..9  BOW_HIT:-4:1
        10..15  BOW_HIT:5

        @ 900161
        5..9  MAGIC_HIT:-4:1
        10..15  MAGIC_HIT:5

        @ 900163
        0..6  SP:3, BASE_MAGIC_CRITICAL:3
        7..9  SP:-3:1, BASE_MAGIC_CRITICAL:3
        10..15  SP:6, BASE_MAGIC_CRITICAL:3

        @ 900184
        5..6  MR:-1:1
        7..8  MR:-8:2, DMG_RED:1
        9  MR:11, DMG_RED:2
        10..15  MR:14, DMG_RED:2, MAX_HP:100, PVP_DMG:1, PVP_DMG_RED:1

        @ 900185
        5..6  ABILITY_RESIS:3:1
        7..8  DMG_RATE:1, HIT:-13:2, ABILITY_RESIS:-4:2, ALL_RESIS:-6:1
        9  DMG_RATE:2, HIT:5, ABILITY_RESIS:15, ALL_RESIS:4
        10..15  DMG_RATE:2, HIT:7, ABILITY_RESIS:18, ALL_RESIS:6, MAX_HP:100, PVP_DMG:1, PVP_DMG_RED:1

        @ 900186
        0..4  BOW_HIT:1
        5..6  BOW_HIT:1, ABILITY_RESIS:3:1
        7..8  BOW_HIT:-5:1, ABILITY_RESIS:-4:2, ALL_RESIS:-6:1
        9  BOW_HIT:5, ABILITY_RESIS:15, ALL_RESIS:4
        10..15  BOW_HIT:7, ABILITY_RESIS:18, ALL_RESIS:6, MAX_HP:100, PVP_DMG:1, PVP_DMG_RED:1

        @ 900187
        0..4  SP:1
        5..6  SP:1, ABILITY_RESIS:3:1
        7  SP:2, ABILITY_RESIS:10, ALL_RESIS:1
        8..9  SP:2, MAGIC_HIT:-14:2, ABILITY_RESIS:-12:3, ALL_RESIS:-14:2
        10..15  SP:3, MAGIC_HIT:5, ABILITY_RESIS:18, ALL_RESIS:6, MAX_HP:100, PVP_DMG:1, PVP_DMG_RED:1

        @ 988171
        1..7  AC:-5, MAX_HP:50, MAX_MP:20, MR:20, EVASION:2, PVP_DMG:1
        8..10  AC:2:-1, MAX_HP:-20:10, MAX_MP:-50:10, MR:13:1, EVASION:-5:1, PVP_DMG:-6:1

        """,
    };

    static final String[] ACCESSORY = {
        """
        @ 20017 NON_ACCESSORY
        5..*  BOW_DAMAGE_UP:0:1, MR:-12:3

        @ * NECKLACE
        1..2  MAX_HP:0:5
        3..4  MAX_HP:-10:10
        5..6  MAX_HP:40, MR:-9:2
        7..8  MAX_HP:50, MR:-9:2, ABILITY_RESIS:-5:1
        9  MAX_HP:60, MR:10, ABILITY_RESIS:4
        10  MAX_HP:100, MR:12, ABILITY_RESIS:4, FEAR_RESIS:2

        @ 20017 NECKLACE
        1..2  MAX_HP:0:5
        3..4  MAX_HP:-10:10
        5..6  BOW_DAMAGE_UP:0:1, MR:-12:3, MAX_HP:40, MR:-9:2
        7..8  BOW_DAMAGE_UP:0:1, MR:-12:3, MAX_HP:50, MR:-9:2, ABILITY_RESIS:-5:1
        9  BOW_DAMAGE_UP:9, MR:15, MAX_HP:60, MR:10, ABILITY_RESIS:4
        10  BOW_DAMAGE_UP:10, MR:18, MAX_HP:100, MR:12, ABILITY_RESIS:4, FEAR_RESIS:2
        11..*  BOW_DAMAGE_UP:0:1, MR:-12:3

        @ * RING
        1..2  MAX_HP:0:5
        3..4  MAX_HP:-10:10
        5  MELEE_DAMAGE_UP:1, BOW_DAMAGE_UP:1, MAX_HP:40
        6  MELEE_DAMAGE_UP:2, BOW_DAMAGE_UP:2, MAX_HP:40, MR:1, PVP_DMG:1
        7..8  MELEE_DAMAGE_UP:-4:1, BOW_DAMAGE_UP:-4:1, MAX_HP:50, SP:-6:1, MR:-11:2, PVP_DMG:-5:1
        9  MELEE_DAMAGE_UP:5, BOW_DAMAGE_UP:5, MAX_HP:60, SP:3, MR:7, PVP_DMG:5
        10  MELEE_DAMAGE_UP:6, BOW_DAMAGE_UP:6, MAX_HP:70, SP:4, MR:9, PVP_DMG:7, ALL_RESIS:1

        @ 20017 RING
        1..2  MAX_HP:0:5
        3..4  MAX_HP:-10:10
        5  BOW_DAMAGE_UP:5, MR:3, MELEE_DAMAGE_UP:1, BOW_DAMAGE_UP:1, MAX_HP:40
        6  BOW_DAMAGE_UP:6, MR:6, MELEE_DAMAGE_UP:2, BOW_DAMAGE_UP:2, MAX_HP:40, MR:1, PVP_DMG:1
        7..8  BOW_DAMAGE_UP:0:1, MR:-12:3, MELEE_DAMAGE_UP:-4:1, BOW_DAMAGE_UP:-4:1, MAX_HP:50, SP:-6:1, MR:-11:2, PVP_DMG:-5:1
        9  BOW_DAMAGE_UP:9, MR:15, MELEE_DAMAGE_UP:5, BOW_DAMAGE_UP:5, MAX_HP:60, SP:3, MR:7, PVP_DMG:5
        10  BOW_DAMAGE_UP:10, MR:18, MELEE_DAMAGE_UP:6, BOW_DAMAGE_UP:6, MAX_HP:70, SP:4, MR:9, PVP_DMG:7, ALL_RESIS:1
        11..*  BOW_DAMAGE_UP:0:1, MR:-12:3

        @ 900163 RING
        1..2  MAX_HP:0:5
        3..4  MAX_HP:-10:10
        5  MELEE_DAMAGE_UP:1, BOW_DAMAGE_UP:1, MAX_HP:40
        6..7  MELEE_DAMAGE_UP:-4:1, BOW_DAMAGE_UP:-4:1, MAX_HP:-20:10, MR:-11:2, PVP_DMG:-5:1
        8..9  MELEE_DAMAGE_UP:-4:1, BOW_DAMAGE_UP:-4:1, MAX_HP:-30:10, MR:-11:2, PVP_DMG:-13:2
        10  MELEE_DAMAGE_UP:6, BOW_DAMAGE_UP:6, MAX_HP:70, MR:9, PVP_DMG:7, ALL_RESIS:1

        @ * BELT
        1..2  MAX_MP:0:5
        3..4  MAX_MP:-10:10
        5  DMG_RED:1, MAX_MP:40
        6..7  DMG_RED:-4:1, MAX_MP:-20:10, MAX_HP:-40:10, PVP_DMG_RED:-11:2
        8..9  DMG_RED:-4:1, MAX_MP:-30:10, MAX_HP:-40:10, PVP_DMG_RED:-11:2
        10  DMG_RED:6, MAX_MP:70, MAX_HP:100, PVP_DMG_RED:9, FEAR_RESIS:2

        @ 20017 BELT
        1..2  MAX_MP:0:5
        3..4  MAX_MP:-10:10
        5  BOW_DAMAGE_UP:5, MR:3, DMG_RED:1, MAX_MP:40
        6..7  BOW_DAMAGE_UP:0:1, MR:-12:3, DMG_RED:-4:1, MAX_MP:-20:10, MAX_HP:-40:10, PVP_DMG_RED:-11:2
        8..9  BOW_DAMAGE_UP:0:1, MR:-12:3, DMG_RED:-4:1, MAX_MP:-30:10, MAX_HP:-40:10, PVP_DMG_RED:-11:2
        10  BOW_DAMAGE_UP:10, MR:18, DMG_RED:6, MAX_MP:70, MAX_HP:100, PVP_DMG_RED:9, FEAR_RESIS:2
        11..*  BOW_DAMAGE_UP:0:1, MR:-12:3

        @ 500012,900007 BELT
        *..0  PVP_DMG_RED:2
        1..2  PVP_DMG_RED:2, MAX_MP:0:5
        3..4  PVP_DMG_RED:2, MAX_MP:-10:10
        5  PVP_DMG_RED:2, DMG_RED:1, MAX_MP:40
        6..7  PVP_DMG_RED:2, DMG_RED:-4:1, MAX_MP:-20:10, MAX_HP:-40:10, PVP_DMG_RED:-11:2
        8..9  PVP_DMG_RED:2, DMG_RED:-4:1, MAX_MP:-30:10, MAX_HP:-40:10, PVP_DMG_RED:-11:2
        10  PVP_DMG_RED:2, DMG_RED:6, MAX_MP:70, MAX_HP:100, PVP_DMG_RED:9, FEAR_RESIS:2
        11..*  PVP_DMG_RED:2

        @ * EARRING
        1..2  MAX_HP:0:5
        3..5  MAX_HP:-10:10
        6  MAX_HP:40
        7..8  MAX_HP:50, ABILITY_RESIS:-5:1
        9  MAX_HP:60, ABILITY_RESIS:4
        10  MAX_HP:70, ABILITY_RESIS:4, FEAR_RESIS:2, ALL_RESIS:1

        @ 20017 EARRING
        1..2  MAX_HP:0:5
        3..4  MAX_HP:-10:10
        5..6  BOW_DAMAGE_UP:0:1, MR:-12:3, MAX_HP:40
        7..8  BOW_DAMAGE_UP:0:1, MR:-12:3, MAX_HP:50, ABILITY_RESIS:-5:1
        9  BOW_DAMAGE_UP:9, MR:15, MAX_HP:60, ABILITY_RESIS:4
        10  BOW_DAMAGE_UP:10, MR:18, MAX_HP:70, ABILITY_RESIS:4, FEAR_RESIS:2, ALL_RESIS:1
        11..*  BOW_DAMAGE_UP:0:1, MR:-12:3

        @ 20017 GRADE_3
        5..*  BOW_DAMAGE_UP:0:1, MR:-12:3

        @ 22224,22225,22227 GRADE_3
        1  MAX_HP:15
        2..4  MAX_HP:10:5, AC:1:-1
        5  MAX_HP:35, AC:-3, MELEE_DAMAGE_UP:1
        6  MAX_HP:40, AC:-3, MELEE_DAMAGE_UP:2, ABILITY_RESIS:5
        7..8  MAX_HP:10:5, AC:-3, MELEE_DAMAGE_UP:-4:1, ABILITY_RESIS:-7:2, PVP_DMG:-6:1

        @ 22226 GRADE_3
        1  MAX_HP:15
        2..3  MAX_HP:10:5, AC:1:-1
        4..5  MAX_HP:10:5, AC:-3, MELEE_DAMAGE_UP:-3:1
        6  MAX_HP:40, AC:-4, MELEE_DAMAGE_UP:3, ABILITY_RESIS:5, DMG_RED:1
        7  MAX_HP:45, AC:-4, MELEE_DAMAGE_UP:4, ABILITY_RESIS:7, PVP_DMG:1, DMG_RED:2
        8..9  MAX_HP:10:5, AC:10:-2, MELEE_DAMAGE_UP:-11:2, ABILITY_RESIS:9, FEAR_RESIS:-7:1, PVP_DMG:-29:4, DMG_RED:-13:2

        @ 22228 GRADE_3
        0  MR:7
        1  MAX_HP:15, MR:7
        2..4  MAX_HP:10:5, AC:1:-1, MR:7
        5  MAX_HP:35, AC:-3, MELEE_DAMAGE_UP:1, MR:7
        6  MAX_HP:40, AC:-4, MELEE_DAMAGE_UP:2, ABILITY_RESIS:5, MR:7
        7  MAX_HP:45, AC:-4, MELEE_DAMAGE_UP:3, ABILITY_RESIS:7, PVP_DMG:1, MR:7
        8..9  MAX_HP:-30:10, AC:10:-2, MELEE_DAMAGE_UP:-11:2, ABILITY_RESIS:9, FEAR_RESIS:-7:1, PVP_DMG:-29:4, MR:-15:3

        @ 22229 GRADE_3
        1..2  MAX_HP:10:10
        3..4  MAX_HP:10:10, DMG_RED:1
        5  MAX_HP:60, DMG_RED:2
        6  MAX_HP:70, DMG_RED:3, AC:-7
        7..8  BOW_HIT:-13:2, HIT:-13:2, MAX_HP:10:10, DMG_RED:-17:3, AC:-1:-1
        9  BOW_HIT:5, HIT:5, MAX_HP:100, DMG_RED:7, AC:-10

        @ 22231 GRADE_3
        0  MR:2
        1..2  MAX_MP:5:5, MR:4:1
        3..4  MAX_MP:15:5, MR:4:1, SP:1
        5  MAX_MP:50, MR:9, SP:2
        6  MAX_MP:55, MR:10, SP:2, AC:-1
        7  MAGIC_HIT:1, MAX_MP:70, MR:12, SP:3, AC:-2
        8  MAGIC_HIT:5, MAX_MP:95, MR:17, SP:5, MAX_HP:50, AC:-3
        9  MAGIC_HIT:9, MAX_MP:125, MAX_HP:100, MR:20, SP:7, AC:-3

        @ 222290 GRADE_3
        0  MAX_MP:15
        1  MAX_HP:5, MAX_MP:15
        2..4  MAX_HP:0:5, MAX_MP:15, AC:1:-1
        5  MAX_HP:25, MAX_MP:15, AC:-3, SP:1
        6  MAX_HP:30, MAX_MP:15, AC:-4, SP:2, ABILITY_RESIS:5
        7  MAX_HP:35, MAX_MP:15, AC:-4, SP:3, ABILITY_RESIS:7, PVP_DMG:1
        8..9  MAX_HP:-40:10, MAX_MP:-290:40, AC:10:-2, SP:-11:2, ABILITY_RESIS:9, FEAR_RESIS:-7:1, PVP_DMG:-29:4

        @ 222291 GRADE_3
        1..2  AC:0:-1
        3..4  MAX_HP:-10:5, AC:0:-1
        5  MAX_HP:15, AC:-4, HIT:1, BOW_HIT:1, MELEE_DAMAGE_UP:1
        6  MAX_HP:20, AC:-4, HIT:2, MELEE_DAMAGE_UP:2, BOW_HIT:2, ABILITY_RESIS:5
        7  MAX_HP:25, AC:-4, HIT:3, BOW_HIT:3, MELEE_DAMAGE_UP:3, ABILITY_RESIS:7, PVP_DMG:1
        8..9  MAX_HP:-50:10, AC:10:-2, HIT:-11:2, MELEE_DAMAGE_UP:-11:2, BOW_HIT:-11:2, ABILITY_RESIS:9, FEAR_RESIS:-7:1, PVP_DMG:-29:4

        @ 222330,222331,222333 GRADE_3
        1  MAX_HP:15
        2..3  MAX_HP:0:10, AC:1:-1
        4..5  MAX_HP:15:5, AC:-3, MELEE_DAMAGE_UP:-3:1
        6  MAX_HP:45, AC:-3, MELEE_DAMAGE_UP:3, ABILITY_RESIS:5
        7..8  MAX_HP:50, AC:-3, MELEE_DAMAGE_UP:-3:1, ABILITY_RESIS:-7:2, PVP_DMG:-6:1

        @ 222332 GRADE_3
        1  MAX_HP:15
        2..3  MAX_HP:0:10, AC:1:-1
        4..5  MAX_HP:15:5, AC:1:-1, MELEE_DAMAGE_UP:-3:1
        6  MAX_HP:45, AC:-4, MELEE_DAMAGE_UP:3, ABILITY_RESIS:4, DMG_RED:1
        7  MAX_HP:55, AC:-5, MELEE_DAMAGE_UP:4, ABILITY_RESIS:7, PVP_DMG:1, DMG_RED:2
        8..9  MAX_HP:-15:10, AC:17:-3, MELEE_DAMAGE_UP:-9:2, ABILITY_RESIS:9, FEAR_RESIS:-14:2, PVP_DMG:-27:4, DMG_RED:-11:2

        @ 222334 GRADE_3
        0  MR:7
        1  MAX_HP:15, MR:7
        2..3  MAX_HP:0:10, AC:1:-1, MR:7
        4..5  MAX_HP:15:5, AC:1:-1, MELEE_DAMAGE_UP:-3:1, MR:7
        6  MAX_HP:45, AC:-4, MELEE_DAMAGE_UP:3, MR:8, ABILITY_RESIS:5
        7  MAX_HP:50, AC:-5, MELEE_DAMAGE_UP:4, MR:9, ABILITY_RESIS:7, PVP_DMG:1
        8..9  MAX_HP:-30:10, AC:17:-3, MELEE_DAMAGE_UP:-9:2, MR:-12:3, ABILITY_RESIS:9, FEAR_RESIS:-14:2, PVP_DMG:-27:4

        @ 222335 GRADE_3
        0  MAX_MP:15
        1  MAX_HP:5, MAX_MP:15
        2..3  MAX_HP:-10:10, MAX_MP:15, AC:1:-1
        4..5  MAX_HP:5:5, MAX_MP:15, AC:-3, SP:-3:1
        6  MAX_HP:35, MAX_MP:15, AC:-3, SP:3, ABILITY_RESIS:5, MAGIC_HIT:1
        7  MAX_HP:40, MAX_MP:30, AC:-5, SP:4, ABILITY_RESIS:7, PVP_DMG:1, MAGIC_HIT:2
        8..9  MAX_HP:-30:10, MAX_MP:-325:45, AC:17:-3, SP:-9:2, ABILITY_RESIS:9, FEAR_RESIS:-14:2, PVP_DMG:-27:4, MAGIC_HIT:-11:2

        @ 222336 GRADE_3
        1..2  AC:0:-1
        3  MAX_HP:10, AC:-3
        4..5  MAX_HP:-5:5, AC:-4, HIT:-3:1, BOW_HIT:-3:1, MELEE_DAMAGE_UP:-3:1
        6  MAX_HP:25, AC:-4, HIT:3, BOW_HIT:3, MELEE_DAMAGE_UP:3, ABILITY_RESIS:5
        7  MAX_HP:30, AC:-5, HIT:4, MELEE_DAMAGE_UP:4, BOW_HIT:4, ABILITY_RESIS:7, PVP_DMG:1
        8..9  MAX_HP:-50:10, AC:17:-3, HIT:-9:2, MELEE_DAMAGE_UP:-9:2, BOW_HIT:-9:2, ABILITY_RESIS:9, FEAR_RESIS:-14:2, PVP_DMG:-27:4

        @ 222337 GRADE_3
        1..2  MAX_HP:0:10
        3..4  MAX_HP:10:10, DMG_RED:-2:1
        5  MAX_HP:60, DMG_RED:3, AC:-7
        6..7  BOW_HIT:-11:2, HIT:-11:2, MAX_HP:10:10, DMG_RED:-2:1, AC:-2:-1
        8..9  BOW_HIT:-11:2, HIT:-11:2, MAX_HP:60:10, DMG_RED:-9:2, AC:-2:-1

        @ 222339 GRADE_3
        0..1  MAX_MP:10
        2  MAX_MP:15
        3..4  MAX_MP:-10:15, MR:5:1, SP:-2:1
        5  MAX_MP:55, MR:10, SP:2, AC:-1
        6..7  MAGIC_HIT:-11:2, MAX_MP:-80:25, MR:-6:3, SP:3, AC:4:-1
        8..9  MAGIC_HIT:-33:5, MAX_MP:-115:30, MAX_HP:-300:50, MR:-4:3, SP:-27:4, AC:-4

        @ 222340,222341 GRADE_3 BLESSED
        *..2  AC:0:-1
        3  AC:-4
        4..7  AC:-1:-1, MELEE_DAMAGE_UP:-2:1, BOW_DAMAGE_UP:-2:1
        8..9  AC:-1:-1, MELEE_DAMAGE_UP:-15:3, BOW_DAMAGE_UP:-15:3
        10..*  AC:-1:-1, MELEE_DAMAGE_UP:12, BOW_DAMAGE_UP:12

        @ 222340,222341 GRADE_3 OTHER
        *..3  AC:0:-1
        4..7  AC:0:-1, MELEE_DAMAGE_UP:-3:1, BOW_DAMAGE_UP:-3:1
        8..9  AC:0:-1, MELEE_DAMAGE_UP:-9:2, BOW_DAMAGE_UP:-9:2
        10..*  AC:0:-1, MELEE_DAMAGE_UP:9, BOW_DAMAGE_UP:9

        @ 20017 OTHER_ACCESSORY
        5..*  BOW_DAMAGE_UP:0:1, MR:-12:3

        @ 22229 OTHER_ACCESSORY
        1..2  MAX_HP:10:10
        3..4  MAX_HP:10:10, DMG_RED:1
        5  MAX_HP:60, DMG_RED:2
        6  MAX_HP:70, DMG_RED:3, AC:-7
        7..8  BOW_HIT:-13:2, HIT:-13:2, MAX_HP:10:10, DMG_RED:-17:3, AC:-1:-1
        9  BOW_HIT:5, HIT:5, MAX_HP:100, DMG_RED:7, AC:-10

        @ 22231 OTHER_ACCESSORY
        0  MR:2
        1..2  MAX_MP:5:5, MR:4:1
        3..4  MAX_MP:15:5, MR:4:1, SP:1
        5  MAX_MP:50, MR:9, SP:2
        6  MAX_MP:55, MR:10, SP:2, AC:-1
        7  MAGIC_HIT:1, MAX_MP:70, MR:12, SP:3, AC:-2
        8  MAGIC_HIT:5, MAX_MP:95, MR:17, SP:5, MAX_HP:50, AC:-3
        9  MAGIC_HIT:9, MAX_MP:125, MAX_HP:100, MR:20, SP:7, AC:-3

        @ 222331 OTHER_ACCESSORY
        1  MAX_HP:15
        2..3  MAX_HP:0:10, AC:1:-1
        4..5  MAX_HP:15:5, AC:-3, MELEE_DAMAGE_UP:-3:1
        6  MAX_HP:45, AC:-3, MELEE_DAMAGE_UP:3, ABILITY_RESIS:5
        7..8  MAX_HP:50, AC:-3, MELEE_DAMAGE_UP:-3:1, ABILITY_RESIS:-7:2, PVP_DMG:-6:1

        @ 222337 OTHER_ACCESSORY
        1..2  MAX_HP:0:10
        3..4  MAX_HP:10:10, DMG_RED:-2:1
        5  MAX_HP:60, DMG_RED:3, AC:-7
        6..7  BOW_HIT:-11:2, HIT:-11:2, MAX_HP:10:10, DMG_RED:-2:1, AC:-2:-1
        8..9  BOW_HIT:-11:2, HIT:-11:2, MAX_HP:60:10, DMG_RED:-9:2, AC:-2:-1

        @ 222339 OTHER_ACCESSORY
        0..1  MAX_MP:10
        2  MAX_MP:15
        3..4  MAX_MP:-10:15, MR:5:1, SP:-2:1
        5  MAX_MP:55, MR:10, SP:2, AC:-1
        6..7  MAGIC_HIT:-11:2, MAX_MP:-80:25, MR:-6:3, SP:3, AC:4:-1
        8..9  MAGIC_HIT:-33:5, MAX_MP:-115:30, MAX_HP:-300:50, MR:-4:3, SP:-27:4, AC:-4

        @ 222340,222341 OTHER_ACCESSORY BLESSED
        *..2  AC:0:-1
        3  AC:-4
        4..7  AC:-1:-1, MELEE_DAMAGE_UP:-2:1, BOW_DAMAGE_UP:-2:1
        8..9  AC:-1:-1, MELEE_DAMAGE_UP:-15:3, BOW_DAMAGE_UP:-15:3
        10..*  AC:-1:-1, MELEE_DAMAGE_UP:12, BOW_DAMAGE_UP:12

        @ 222340,222341 OTHER_ACCESSORY OTHER
        *..3  AC:0:-1
        4..7  AC:0:-1, MELEE_DAMAGE_UP:-3:1, BOW_DAMAGE_UP:-3:1
        8..9  AC:0:-1, MELEE_DAMAGE_UP:-9:2, BOW_DAMAGE_UP:-9:2
        10..*  AC:0:-1, MELEE_DAMAGE_UP:9, BOW_DAMAGE_UP:9

        """,
    };


    static final String[] ARMOR_DODGE = {"""
        @ 22228
        7  MAGIC_DODGE:1
        8  MAGIC_DODGE:4
        9..*  MAGIC_DODGE:7

        @ 222334
        6  MAGIC_DODGE:1
        7  MAGIC_DODGE:3
        8  MAGIC_DODGE:5
        9..*  MAGIC_DODGE:9
        """};

    static final String[] ARMOR_CASTING = {"""
        @ 202022
        1..*  MAX_MP:0:10, MR:0:2

        @ 20107
        *..-2147483647  SP:-2:1
        3..*  SP:-2:1
        """};

    // Item IDs/ranges followed by named properties. IDs can belong to several rows.
    static final String TRAITS = """
        20077,20062,120077  INVISIBILITY
        123111..123113  HALPHAS
        900022  MAP_BREATH_ICON
        20207  WATER_BREATHING
        900111  EQUIPPED_SKILL=TELEPORT_RULER EXCLUSIVE_ABILITY
        20288,900111  ABILITY=1
        20281  ABILITY=2
        20036  ABILITY=3
        20284  ABILITY=5
        200002  EQUIP_MESSAGE=149
        203003  MORPH=12232
        20383  CHARGED_POLYMORPH
        800110  CLASS_DEPENDENT_BONUS
        20236  ELF_MR=5
        22229..22231,222337..222341,900124..900130  FIXED_ARMOR_AC
        900117..900120  NONNEGATIVE_ENCHANT_AC
        222295..222299  RUNE_TIER=1
        222312..222316,900135..900139  RUNE_TIER=2
        900140..900144  RUNE_TIER=2 RUNE_ACCURACY=5 RUNE_MAGIC_DR=2
        900145..900149  RUNE_TIER=2 RUNE_ACCURACY=10
        222297  RUNE_WIZARD_MP=30
        20013  SPELLS=26,43
        20014  SPELLS=1,19
        20015  SPELLS=12,13,42
        20008  SPELLS=43
        20023  SPELLS=54
        20011,20110,120011,22223,20117,22213,120110,93001,490008,22365,222328,222300..222303  MR_PER_ENCHANT=1
        20056,120056,220056,93002,20074,900090,120074,222324,222325,900043,900044  MR_PER_ENCHANT=2
        20079,20078,900047,900055,20049,20050,900057,900056  MR_PER_ENCHANT=3
        """;
}
