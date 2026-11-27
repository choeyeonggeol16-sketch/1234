package l1j.server.server.auth.captcha.loader;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import l1j.server.server.auth.captcha.CaptchaData;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class CaptchaDataLoader {
    private static final Logger LOG = Logger.getLogger(CaptchaDataLoader.class.getName());
    private static CaptchaDataLoader instance;
    private volatile List<CaptchaData> challenges;

    private CaptchaDataLoader() {
        var rows = new ArrayList<CaptchaData>();
        Selector.exec("select * from tb_captcha", new FullSelectorHandler() {
            @Override
            public void result(ResultSet rs) throws Exception {
                while (rs.next()) rows.add(CaptchaData.newInstance(rs));
            }
        });
        challenges = List.copyOf(rows);
        if (challenges.isEmpty()) LOG.warning("No captcha challenges loaded from tb_captcha");
    }

    public static synchronized CaptchaDataLoader getInstance() {
        if (instance == null) instance = new CaptchaDataLoader();
        return instance;
    }

    public static synchronized void release() {
        instance = null;
    }

    public static synchronized void reload() {
        CaptchaDataLoader replacement = new CaptchaDataLoader();
        if (instance == null || !replacement.challenges.isEmpty()) instance = replacement;
    }

    public CaptchaData to_rand_captcha() {
        List<CaptchaData> snapshot = challenges;
        return snapshot.isEmpty() ? null : snapshot.get(GameRandom.secureIndex(snapshot.size()));
    }

    public void dispose() {
        challenges = List.of();
    }
}
