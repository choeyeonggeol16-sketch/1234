package l1j.server.server.model.character.skill;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import l1j.server.server.templates.L1Skills;

public final class SkillDefinitionFixture {
    private SkillDefinitionFixture() {}

    public static L1Skills definition(Map<String, ?> columns) {
        ResultSet row = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getInt" -> columns.containsKey(args[0]) ? columns.get(args[0]) : 0;
                    case "getString" -> columns.get(args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        try {
            return new L1Skills(row);
        } catch (SQLException failure) {
            throw new AssertionError(failure);
        }
    }
}
