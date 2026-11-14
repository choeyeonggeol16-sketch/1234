import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Run in separate JVMs against the baseline and renamed production classes. */
public final class SerializationSnapshot {
    private static Object value(Class<?> type, int variant) {
        if (type == boolean.class) return variant == 1;
        if (type == int.class) return variant == 2 ? -1 : variant;
        if (type == long.class) return variant == 2 ? -1L : (long) variant;
        if (type == byte.class) return (byte) variant;
        if (type == short.class) return (short) variant;
        if (type == double.class) return (double) variant;
        if (type == float.class) return (float) variant;
        if (type == String.class) return variant == 0 ? "" : "한글-test";
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type.isArray()) {
            Object array = Array.newInstance(type.getComponentType(), variant == 0 ? 0 : 2);
            for (int i = 0; i < Array.getLength(array); i++)
                Array.set(array, i, value(type.getComponentType(), variant));
            return array;
        }
        if (type == List.class) return new ArrayList<>();
        if (type == Set.class) return Set.of();
        if (type == Map.class) return Map.of();
        return null;
    }
    public static void main(String[] args) throws Exception {
        boolean candidate = args[1].equals("candidate");
        List<String> lines = new ArrayList<>();
        int success = 0;
        int errors = 0;
        for (String row : Files.readAllLines(Path.of(args[0]))) {
            String[] names = row.split("\t");
            Class<?> type = Class.forName(names[candidate ? 1 : 0]);
            Method[] methods = type.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::toGenericString));
            for (Method method : methods) {
                if (!method.getName().equals("encodeFields") || !Modifier.isStatic(method.getModifiers())) continue;
                for (int variant = 0; variant < 3; variant++) {
                    Class<?>[] types = method.getParameterTypes();
                    Object[] values = new Object[types.length];
                    for (int i = 0; i < values.length; i++) values[i] = value(types[i], variant);
                    String key = names[0] + "/" + types.length + "/" + variant;
                    try {
                        byte[] bytes = (byte[]) method.invoke(null, values);
                        lines.add(key + "\t" + HexFormat.of().formatHex(bytes));
                        success++;
                    } catch (InvocationTargetException e) {
                        // Null DTO cases are compared separately, not counted as wire evidence.
                        lines.add(key + "\tERROR:" + e.getCause().getClass().getName());
                        errors++;
                    }
                }
            }
        }
        Files.write(Path.of(args[2]), lines);
        if (success < 150) throw new AssertionError("insufficient successful wire cases: " + success);
        System.out.println("PASS serialization snapshot: " + success + " wire cases; " + errors + " null-DTO cases");
    }
}
