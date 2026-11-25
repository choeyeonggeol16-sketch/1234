package l1j.server.server.command;

import java.util.Objects;
import l1j.server.server.command.executor.L1CommandExecutor;
import l1j.server.server.model.Instance.L1PcInstance;

/** Immutable Builder command entry: name, permission policy and direct execution function. */
public final class BuilderCmdPairType {
    private final String name;
    private final int requiredBuilderLevel;
    private final boolean legacy;
    private final L1CommandExecutor function;

    public BuilderCmdPairType(String name, int requiredBuilderLevel, boolean legacy,
                              L1CommandExecutor function) {
        this.name = Objects.requireNonNull(name);
        this.requiredBuilderLevel = requiredBuilderLevel;
        this.legacy = legacy;
        this.function = Objects.requireNonNull(function);
    }

    public String getName() { return name; }
    public int getLevel() { return requiredBuilderLevel; }
    public boolean isLegacy() { return legacy; }

    public void execute(L1PcInstance pc, String command, String arguments) {
        function.execute(pc, command, arguments);
    }
}
