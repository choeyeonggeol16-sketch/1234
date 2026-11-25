package l1j.server.server.command;

import java.util.Objects;
import java.util.function.Supplier;
import l1j.server.server.command.executor.L1CommandExecutor;
import l1j.server.server.templates.L1Command;

/** OFF command entry adapted to RKern's existing permission policy. */
public final class BuilderCmdPairType extends L1Command {
    private final boolean legacy;
    private L1CommandExecutor direct;

    private BuilderCmdPairType(String name, int level, String executorClassName,
                               Supplier<L1CommandExecutor> factory, boolean legacy) {
        super(name, level, executorClassName, factory);
        this.legacy = legacy;
    }

    public static BuilderCmdPairType legacy(String name, int level, String executorClassName,
                                           Supplier<L1CommandExecutor> factory) {
        return new BuilderCmdPairType(name, level, executorClassName, factory, true);
    }

    public static BuilderCmdPairType direct(String name, L1CommandExecutor handler) {
        BuilderCmdPairType entry = new BuilderCmdPairType(name, 0, "", null, false);
        entry.bindDirect(handler);
        return entry;
    }

    public boolean isLegacy() { return legacy; }
    public L1CommandExecutor directHandler() { return direct; }

    /** Only during startup registration. */
    public void bindDirect(L1CommandExecutor handler) {
        if (direct != null) throw new IllegalStateException("Duplicate builder command: " + getName());
        direct = Objects.requireNonNull(handler);
    }
}
