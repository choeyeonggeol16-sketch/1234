package l1j.server.server.command;

import java.util.Objects;
import l1j.server.server.model.Instance.L1PcInstance;

/** Immutable Builder command entry: name, permission policy and direct execution function. */
public final class BuilderCmdPairType {
    @FunctionalInterface
    public interface BuilderCmdFunction {
        void execute(L1PcInstance pc, String command, String arguments);
    }

    private final String name;
    private final int requiredBuilderLevel;
    private final boolean levelRestricted;
    private final BuilderCmdFunction function;

    public BuilderCmdPairType(String name, int requiredBuilderLevel, boolean levelRestricted,
                              BuilderCmdFunction function) {
        this.name = Objects.requireNonNull(name);
        this.requiredBuilderLevel = requiredBuilderLevel;
        this.levelRestricted = levelRestricted;
        this.function = Objects.requireNonNull(function);
    }

    public String getName() { return name; }
    public int getLevel() { return requiredBuilderLevel; }
    public boolean isLevelRestricted() { return levelRestricted; }

    public void execute(L1PcInstance pc, String command, String arguments) {
        function.execute(pc, command, arguments);
    }
}
