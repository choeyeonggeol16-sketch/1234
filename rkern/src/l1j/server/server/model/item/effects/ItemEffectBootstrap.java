package l1j.server.server.model.item.effects;

import java.util.logging.Logger;

import l1j.server.server.model.item.ItemEffectRegistry;

public final class ItemEffectBootstrap {

    private static final Logger _log = Logger.getLogger(ItemEffectBootstrap.class.getName());

    private ItemEffectBootstrap() {}

    public static void loadAll() {
     //   final int before = ItemEffectRegistry.size();

        load("EarringDefs",  () -> EarringDefs.load());
        load("PauldronDefs", () -> PauldronDefs.load());
        load("EmblemDefs", () -> EmblemDefs.load());
        load("TDefs", () -> TDefs.load());


   //     final int added = ItemEffectRegistry.size() - before;
   //     _log.info("[ItemEffectBootstrap] 카테고리 로드 완료: 신규 등록 " + added + "건");
    }

    private static void load(String category, Runnable loader) {
        try {
            loader.run();
        } catch (Throwable t) {
            _log.severe("[ItemEffectBootstrap] ★" + category + " 로드 실패★ — 해당 카테고리만 미동작: " + t);
            t.printStackTrace();
        }
    }
}