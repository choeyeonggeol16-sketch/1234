package l1j.server.server.shared.reflection;

import java.util.Map;

public final class ConfiguredType {
    private ConfiguredType() {}

    public static <T> T create(String configuredName, Class<T> baseType, String previousPackage,
            Map<String, String> previousNames) throws ReflectiveOperationException {
        final String currentPrefix = baseType.getPackageName() + ".";
        final String previousPrefix = previousPackage + ".";
        String name = configuredName;
        if (name.startsWith(previousPrefix)) name = name.substring(previousPrefix.length());
        else if (name.startsWith(currentPrefix)) name = name.substring(currentPrefix.length());
        if (name.indexOf('.') < 0 && name.indexOf(',') < 0) {
            name = currentPrefix + previousNames.getOrDefault(name, name);
        }
        return Class.forName(name).asSubclass(baseType).getConstructor().newInstance();
    }
}
