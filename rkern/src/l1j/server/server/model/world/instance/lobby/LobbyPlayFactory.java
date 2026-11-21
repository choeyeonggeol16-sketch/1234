package l1j.server.server.model.world.instance.lobby;

import java.util.Map;

import l1j.server.server.model.world.instance.lobby.template.LobbyObject;
import l1j.server.server.shared.reflection.ConfiguredType;

public class LobbyPlayFactory {
    private static final String PREVIOUS_PACKAGE = "l1j.server.MJInstanceSystem.MJLFC.Template";
    private static final Map<String, String> PREVIOUS_NAMES = Map.of(
            "MJLFCArenaTower", "LobbyArenaTower",
            "MJLFCDummy", "LobbyDummy",
            "MJLFCLastDuel", "LobbyLastDuel",
            "MJLFCObject", "LobbyObject",
            "MJLFCWarSquareTrap", "LobbyWarSquareTrap");

    public static LobbyObject create(String configuredName) {
        try {
            return ConfiguredType.create(configuredName, LobbyObject.class, PREVIOUS_PACKAGE, PREVIOUS_NAMES);
        } catch (Exception failure) {
            failure.printStackTrace();
            return null;
        }
    }
}
