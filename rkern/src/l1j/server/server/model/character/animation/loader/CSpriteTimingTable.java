package l1j.server.server.model.character.animation.loader;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilderFactory;

import gnu.trove.map.TIntLongMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.animation.CActionTiming;
import l1j.server.server.model.character.animation.EActionCodes;
import l1j.server.server.model.character.animation.CSpriteTiming;
import l1j.server.server.model.character.animation.intervaldecorator.ActionIntervalDecoratorFactory;
import l1j.server.server.shared.arrangement.ArrangeParseeFactory;
import l1j.server.server.shared.arrangement.ArrangeParser;

public final class CSpriteTimingTable {
    public static final int DEFAULT_SPRITE_ID = 1120;
    private static final int LEVEL_LIMIT = 128;
    private static final Logger LOG = Logger.getLogger(CSpriteTimingTable.class.getName());
    private static final SpriteData EMPTY = new SpriteData(new TIntObjectHashMap<>(), new int[LEVEL_LIMIT]);
    private static final CSpriteTimingTable INSTANCE = new CSpriteTimingTable();

    private record SpriteData(TIntObjectMap<CSpriteTiming> sprites, int[] levelIndices) {}

    private volatile SpriteData spriteData = EMPTY;
    private volatile TIntLongMap spellDelays;

    private CSpriteTimingTable() {}

    public static CSpriteTimingTable getInstance() {
        return INSTANCE;
    }

    public static int levelToIndex(int level, int spriteId) {
        return CSpriteTiming.levelIndex(INSTANCE.spriteData.levelIndices(), level, spriteId);
    }

    private TIntLongMap loadSpellDelayInformation() throws SQLException {
        final TIntLongMap sps = new TIntLongHashMap(320);
        final String sql = "SELECT skill_id, delay FROM tb_magicdelay";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql);
             ResultSet rs = pstm.executeQuery()) {
            while (rs.next()) {
                sps.put(rs.getInt("skill_id"), rs.getLong("delay"));
            }
        }
        return sps;
    }

    public synchronized boolean reloadSpellDelayInformation() {
        try {
            spellDelays = loadSpellDelayInformation();
            return true;
        } catch (SQLException | RuntimeException error) {
            LOG.log(Level.SEVERE, "Could not load spell delays; keeping the previous table", error);
            return false;
        }
    }

    public void load() {
        reloadSprites();
        reloadSpellDelayInformation();
    }

    public synchronized boolean reloadSprites() {
        try {
            spriteData = readSpriteData();
            return true;
        } catch (Exception error) {
            LOG.log(Level.SEVERE, "Could not load SPR timing data; keeping the previous table", error);
            return false;
        }
    }

    private SpriteData readSpriteData() throws Exception {
        File file = new File("./data/xml/PolyFrame/polyframerate.xml");
        Document document = file.exists() ? DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(file) : null;
        int[] levelIndices = document == null ? new int[LEVEL_LIMIT] : loadLevelInformation(document);
        TIntObjectMap<CSpriteTiming> sprites = loadBaseSprites(levelIndices);
        if (!sprites.containsKey(DEFAULT_SPRITE_ID)) {
            throw new SQLException("Missing default SPR " + DEFAULT_SPRITE_ID);
        }
        if (document != null) {
            loadActionRates(document, sprites);
            loadExceptionActionRates(document, sprites);
        }
        return new SpriteData(sprites, levelIndices);
    }

    private TIntObjectMap<CSpriteTiming> loadBaseSprites(int[] levelIndices) throws SQLException {
        final TIntObjectMap<List<CActionTiming>> actionsBySprite = loadAllActions();

        final TIntObjectMap<CSpriteTiming> sprInfos = new TIntObjectHashMap<>();
        final String sql = "SELECT spr_id, width, height, action_count FROM spr_info";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql);
             ResultSet rs = pstm.executeQuery()) {

            while (rs.next()) {
                int spriteId = rs.getInt("spr_id");
                sprInfos.put(spriteId, CSpriteTiming.read(rs, levelIndices, actionsBySprite.get(spriteId)));
            }
        }
        return sprInfos;
    }

    private TIntObjectMap<List<CActionTiming>> loadAllActions() throws SQLException {
        final TIntObjectMap<List<CActionTiming>> map = new TIntObjectHashMap<>();
        final String sql = "SELECT spr_id, act_id, framecount, framerate FROM spr_action";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql);
             ResultSet rs = pstm.executeQuery()) {

            while (rs.next()) {
                final int sprId = rs.getInt("spr_id");
                List<CActionTiming> list = map.get(sprId);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(sprId, list);
                }
                list.add(CActionTiming.read(rs));
            }
        }
        return map;
    }

    private int[] loadLevelInformation(Document document) {
        int[] indices = new int[LEVEL_LIMIT];
        Element levels = (Element) document.getElementsByTagName("Level").item(0);
        Integer[] boundaries = (Integer[]) ArrangeParser.parsing(
                levels.getAttribute("range"), ",", ArrangeParseeFactory.createIntArrange()).result();
        for (int i = 0; i < boundaries.length; i++) {
            int from = Math.clamp(boundaries[i], 0, LEVEL_LIMIT);
            int to = i + 1 == boundaries.length ? LEVEL_LIMIT : Math.clamp(boundaries[i + 1], 0, LEVEL_LIMIT);
            if (from < to) Arrays.fill(indices, from, to, i);
        }
        return indices;
    }

    private Integer[] loadTargetSprite(Document doc) {
        return (Integer[]) ArrangeParser.parsing(
                doc.getElementsByTagName("Target"), ArrangeParseeFactory.createIntArrange()).result();
    }

    private Integer[] loadExceptionTargetSprite(Document doc) {
        return (Integer[]) ArrangeParser
                .parsing(((Element) ((NodeList) doc.getElementsByTagName("Sprite")).item(0)).getAttribute("target"),
                        ",", ArrangeParseeFactory.createIntArrange())
                .result();
    }

    private CSpriteTiming getFrom(TIntObjectMap<CSpriteTiming> sprInfos, int n) {
        final CSpriteTiming sInfo = sprInfos.get(n);
        return sInfo == null ? sprInfos.get(DEFAULT_SPRITE_ID) : sInfo;
    }

    private void loadActionRates(Document doc, TIntObjectMap<CSpriteTiming> sprInfos) {
        final Integer[] targets = loadTargetSprite(doc);
        final NodeList nodes = ((Element) ((NodeList) doc.getElementsByTagName("PolyFrameRate")).item(0)).getChildNodes();
        final int size = nodes.getLength();
        for (int i = size - 1; i >= 0; --i) {
            final Node node = nodes.item(i);
            if (Node.ELEMENT_NODE != node.getNodeType()) {
                continue;
            }

            final Element element = (Element) node;
            final Double[] rates = (Double[]) ArrangeParser.parsing(element.getAttribute("rate"), ",", ArrangeParseeFactory.createFrameRateArrange()).result();
            final Integer[] actions = (Integer[]) ArrangeParser.parsing(element.getAttribute("action"), ",", ArrangeParseeFactory.createIntArrange()).result();
            for (Integer a : actions) {
                if (EActionCodes.fromInt(a) == null) {
                    continue;
                }

                for (Integer target : targets) {
                    final CSpriteTiming sInfo = getFrom(sprInfos, target);
                    if (sInfo == null) {
                        LOG.warning(String.format("invalid user sprite : %d", target));
                    } else {
                        sInfo.registerLevelRates(a, rates);
                    }
                }
            }
        }
    }

    private void loadExceptionActionRates(Document doc, TIntObjectMap<CSpriteTiming> sprInfos) {
        final Integer[] targets = loadExceptionTargetSprite(doc);
        final NodeList nodes = ((Element) ((NodeList) doc.getElementsByTagName("PolyFrameException")).item(0)).getChildNodes();
        final int size = nodes.getLength();
        for (int i = size - 1; i >= 0; --i) {
            final Node node = nodes.item(i);
            if (Node.ELEMENT_NODE != node.getNodeType()) {
                continue;
            }

            if (!node.getNodeName().equalsIgnoreCase("Frame")) {
                continue;
            }

            final Element element = (Element) node;
            final Double[] rates = (Double[]) ArrangeParser.parsing(element.getAttribute("rate"), ",", ArrangeParseeFactory.createFrameRateArrange()).result();
            final Integer[] actions = (Integer[]) ArrangeParser.parsing(element.getAttribute("action"), ",", ArrangeParseeFactory.createIntArrange()).result();

            for (Integer a : actions) {
                for (Integer target : targets) {
                    final CSpriteTiming sInfo = getFrom(sprInfos, target);
                    if (sInfo == null) {
                        LOG.warning(String.format("invalid user exception sprite : %d", target));
                    } else {
                        sInfo.registerLevelRates(a, rates);
                    }
                }
            }
            break;
        }
    }

    public CSpriteTiming get(int spriteId) {
        return getFrom(spriteData.sprites(), spriteId);
    }

    public CSpriteTiming refresh(CSpriteTiming held) {
        SpriteData current = spriteData;
        if (held.belongsTo(current.levelIndices())) return held;
        CSpriteTiming replacement = current.sprites().get(held.getSpriteId());
        return replacement == null ? held : replacement;
    }

    public long getUseSpellInterval(L1PcInstance pc, int skillId) {
        TIntLongMap delays = spellDelays;
        if (delays == null || !delays.containsKey(skillId)) return 0L;
        return (long) ActionIntervalDecoratorFactory.PC_ATT_DECORATOR.decoration(pc, delays.get(skillId));
    }
}
