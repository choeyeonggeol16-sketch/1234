package l1j.server.server.command;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.templates.L1Command;

public final class L1Commands {
    private static final Logger LOG = Logger.getLogger(L1Commands.class.getName());
    private static volatile Map<String, L1Command> commands = Collections.emptyMap();

    private L1Commands() {}

    public static synchronized void reload() {
        final Map<String, L1Command> loaded = new LinkedHashMap<>();
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement stmt = con.prepareStatement(
                     "SELECT name, access_level, class_name FROM commands");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                final L1Command command = new L1Command(
                        rs.getString(1), rs.getInt(2), rs.getString(3));
                loaded.put(command.getName().toLowerCase(Locale.ROOT), command);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "커맨드 목록 로드 실패", e);
            throw new IllegalStateException("Unable to load commands", e);
        }
        commands = Collections.unmodifiableMap(loaded);
    }

    public static L1Command get(String name) {
        return name == null ? null : commands.get(name.toLowerCase(Locale.ROOT));
    }

    public static List<L1Command> availableCommandList(int accessLevel) {
        final Map<String, L1Command> snapshot = commands;
        final List<L1Command> result = new ArrayList<>();
        for (L1Command command : snapshot.values()) {
            if (command.getLevel() <= accessLevel) {
                result.add(command);
            }
        }
        return result;
    }
}
