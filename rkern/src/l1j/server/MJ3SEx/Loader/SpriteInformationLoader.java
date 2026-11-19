package l1j.server.MJ3SEx.Loader;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import gnu.trove.map.TIntLongMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.MJ3SEx.ActionInformation;
import l1j.server.MJ3SEx.EActionCodes;
import l1j.server.MJ3SEx.MJSprBoundary;
import l1j.server.MJ3SEx.SpriteInformation;
import l1j.server.MJ3SEx.IntervalDecorator.ActionIntervalDecoratorFactory;
import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeHelper;
import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeParseeFactory;
import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeParser;
import l1j.server.server.model.Instance.L1PcInstance;

public class SpriteInformationLoader {
    private static final Logger _log = Logger.getLogger(SpriteInformationLoader.class.getName());

    private static volatile Integer[] _levelToIdx;

    public static int levelToIndex(int level, int sprId) {
        MJSprBoundary boundary = MJSprBoundary.get_boundary(sprId);
        if (boundary != null) {
            level = boundary.get_boundary_level();
        }
        final Integer[] map = _levelToIdx; 
        if (map == null) {
            return 0;
        }
        if (level < 0) {
            level = 0;
        } else if (level >= map.length) {
            level = map.length - 1;
        }
        final Integer idx = map[level];
        return idx == null ? 0 : idx;
    }

    private static volatile SpriteInformationLoader _instance;

    public static SpriteInformationLoader getInstance() {
        if (_instance == null) {
            synchronized (SpriteInformationLoader.class) {
                if (_instance == null) {
                    _instance = new SpriteInformationLoader();
                }
            }
        }
        return _instance;
    }

    public static void release() {
        SpriteInformationLoader tmp = _instance;
        if (tmp != null) {
            tmp.clear();
            _instance = null;
        }
    }

    public static void reload() {
        SpriteInformationLoader oldInstance = _instance;
        SpriteInformationLoader newInstance = new SpriteInformationLoader().loadSpriteInformation();
        _instance = newInstance;
        if (oldInstance != null) {
            oldInstance.clear();
        }
    }

    private volatile TIntObjectMap<SpriteInformation> _sprInfos;
    private volatile TIntLongMap _spellInfos;

    private SpriteInformationLoader() {
    	
    }

    private TIntLongMap loadSpellDelayInformation() {
        final TIntLongMap sps = new TIntLongHashMap(320);
        final String sql = "SELECT skill_id, delay FROM tb_magicdelay";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql);
             ResultSet rs = pstm.executeQuery()) {
            while (rs.next()) {
                sps.put(rs.getInt("skill_id"), rs.getLong("delay"));
            }
        } catch (Exception e) {
            _log.log(Level.SEVERE, "loadSpellDelayInformation error", e);
        }
        return sps;
    }

    public void reloadSpellDelayInformation() {
        _spellInfos = loadSpellDelayInformation();
    }

    public SpriteInformationLoader loadSpriteInformation() {
        try {
            final TIntObjectMap<SpriteInformation> sprInfos = loadBaseSprites();
            loadUserActionInformation(sprInfos);
            _sprInfos = sprInfos;
        } catch (Exception e) {
            _log.log(Level.SEVERE, "loadSpriteInformation error", e);
        } finally {
            _spellInfos = loadSpellDelayInformation();
        }
        return this;
    }

    private TIntObjectMap<SpriteInformation> loadBaseSprites() throws Exception {
        final TIntObjectMap<List<ActionInformation>> actionsBySprite = loadAllActions();

        final TIntObjectMap<SpriteInformation> sprInfos = new TIntObjectHashMap<>();
        final String sql = "SELECT spr_id, width, height, action_count FROM spr_info";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql);
             ResultSet rs = pstm.executeQuery()) {

            while (rs.next()) {
                final SpriteInformation sInfo = SpriteInformation.create(rs);
                final List<ActionInformation> actions = actionsBySprite.get(sInfo.getSpriteId());
                if (actions != null) {
                    for (int i = 0; i < actions.size(); i++) {
                        sInfo.put(actions.get(i));
                    }
                }
                sprInfos.put(sInfo.getSpriteId(), sInfo);
            }
        }
        return sprInfos;
    }

    private TIntObjectMap<List<ActionInformation>> loadAllActions() {
        final TIntObjectMap<List<ActionInformation>> map = new TIntObjectHashMap<>();
        final String sql = "SELECT spr_id, act_id, framecount, framerate FROM spr_action";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql);
             ResultSet rs = pstm.executeQuery()) {

            while (rs.next()) {
                final int sprId = rs.getInt("spr_id");
                List<ActionInformation> list = map.get(sprId);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(sprId, list);
                }
                list.add(ActionInformation.create(rs));
            }
        } catch (Exception e) {
            _log.log(Level.SEVERE, "loadAllActions error", e);
        }
        return map;
    }

    private void loadUserActionInformation(TIntObjectMap<SpriteInformation> sprInfos) {
        try {
            final Document doc = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(new File("./data/xml/PolyFrame/polyframerate.xml"));

            loadLevelInformation(doc);
            loadActionRates(doc, sprInfos);
            loadExceptionActionRates(doc, sprInfos);
        } catch (Exception e) {
            _log.log(Level.SEVERE, "loadUserActionInformation XML parse error", e);
        }
    }

    private void loadLevelInformation(Document doc) {
        final Integer[] lvlToIdx = new Integer[128];
        for (int i = 0; i < lvlToIdx.length; i++) {
            lvlToIdx[i] = 0;
        }

        final Element element = (Element) doc.getElementsByTagName("Level").item(0);
        final Integer[] levels = (Integer[]) MJArrangeParser.parsing(
                element.getAttribute("range"), ",", MJArrangeParseeFactory.createIntArrange()).result();

        for (int i = 0; i < levels.length; i++) {
            if (i + 1 >= levels.length) {
                MJArrangeHelper.<Integer>setArrayValues(lvlToIdx, levels[i], 127, i);
                break;
            }
            MJArrangeHelper.<Integer>setArrayValues(lvlToIdx, levels[i], levels[i + 1] - 1, i);
        }

        _levelToIdx = lvlToIdx; 
    }

    private Integer[] loadTargetSprite(Document doc) {
        return (Integer[]) MJArrangeParser.parsing(
                doc.getElementsByTagName("Target"), MJArrangeParseeFactory.createIntArrange()).result();
    }

    private Integer[] loadExceptionTargetSprite(Document doc) {
        return (Integer[]) MJArrangeParser
                .parsing(((Element) ((NodeList) doc.getElementsByTagName("Sprite")).item(0)).getAttribute("target"),
                        ",", MJArrangeParseeFactory.createIntArrange())
                .result();
    }

    private SpriteInformation getFrom(TIntObjectMap<SpriteInformation> sprInfos, int n) {
        final SpriteInformation sInfo = sprInfos.get(n);
        return sInfo == null ? sprInfos.get(1120) : sInfo;
    }

    private void loadActionRates(Document doc, TIntObjectMap<SpriteInformation> sprInfos) {
        final Integer[] targets = loadTargetSprite(doc);
        final NodeList nodes = ((Element) ((NodeList) doc.getElementsByTagName("PolyFrameRate")).item(0)).getChildNodes();
        final int size = nodes.getLength();
        for (int i = size - 1; i >= 0; --i) {
            final Node node = nodes.item(i);
            if (Node.ELEMENT_NODE != node.getNodeType()) {
                continue;
            }

            final Element element = (Element) node;
            final Double[] rates = (Double[]) MJArrangeParser.parsing(element.getAttribute("rate"), ",", MJArrangeParseeFactory.createFrameRateArrange()).result();
            final Integer[] actions = (Integer[]) MJArrangeParser.parsing(element.getAttribute("action"), ",", MJArrangeParseeFactory.createIntArrange()).result();
            for (Integer a : actions) {
                if (EActionCodes.fromInt(a) == null) {
                    continue;
                }

                for (Integer target : targets) {
                    final SpriteInformation sInfo = getFrom(sprInfos, target);
                    if (sInfo == null) {
                        _log.warning(String.format("invalid user sprite : %d", target));
                    } else {
                        sInfo.registerUserActions(a, rates);
                    }
                }
            }
        }
    }

    private void loadExceptionActionRates(Document doc, TIntObjectMap<SpriteInformation> sprInfos) {
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
            final Double[] rates = (Double[]) MJArrangeParser.parsing(element.getAttribute("rate"), ",", MJArrangeParseeFactory.createFrameRateArrange()).result();
            final Integer[] actions = (Integer[]) MJArrangeParser.parsing(element.getAttribute("action"), ",", MJArrangeParseeFactory.createIntArrange()).result();

            for (Integer a : actions) {
                for (Integer target : targets) {
                    final SpriteInformation sInfo = getFrom(sprInfos, target);
                    if (sInfo == null) {
                        _log.warning(String.format("invalid user exception sprite : %d", target));
                    } else {
                        sInfo.registerUserActions(a, rates);
                    }
                }
            }
            break;
        }
    }

    public SpriteInformation get(int n) {
        final TIntObjectMap<SpriteInformation> sprInfos = _sprInfos;
        if (sprInfos == null) {
            return null;
        }
        final SpriteInformation sInfo = sprInfos.get(n);
        return sInfo == null ? sprInfos.get(1120) : sInfo;
    }

    public void clear() {
        if (_spellInfos != null) {
            _spellInfos.clear();
            _spellInfos = null;
        }

        final Stream<SpriteInformation> stream = createValuesStream();
        if (stream != null) {
            stream.forEach((SpriteInformation sInfo) -> { sInfo.dispose(); });
        }

        if (_sprInfos != null) {
            _sprInfos.clear();
            _sprInfos = null;
        }
    }

    public long getUseSpellInterval(L1PcInstance pc, int skillId) {
        final TIntLongMap spellInfos = _spellInfos;
        if (spellInfos == null || !spellInfos.containsKey(skillId)) {
            return 0L;
        }
        return (long) ActionIntervalDecoratorFactory.PC_ATT_DECORATOR.decoration(pc, spellInfos.get(skillId));
    }

    public Stream<SpriteInformation> createValuesStream() {
        final TIntObjectMap<SpriteInformation> sprInfos = _sprInfos;
        if (sprInfos == null) {
            return null;
        }
        return sprInfos.size() > 1024 ? sprInfos.valueCollection().parallelStream() : sprInfos.valueCollection().stream();
    }

    public Stream<Integer> createKeysStream() {
        final TIntObjectMap<SpriteInformation> sprInfos = _sprInfos;
        if (sprInfos == null) {
            return null;
        }
        final int[] keys = sprInfos.keys();
        final IntStream is = IntStream.of(keys);
        return sprInfos.size() > 1024 ? is.boxed().parallel() : is.boxed();
    }
}