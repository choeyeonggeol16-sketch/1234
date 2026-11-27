package l1j.server.server.packets.Protocol.MainServer.Proto;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import l1j.server.server.model.network.buffer.Assemble;

public final class ScopedProtoWriterRegression {
    public static void main(String[] args) throws Exception {
        nestedBorrowWithoutAliasing();
        copyAndRewindAtVarintBoundaries();
        directAppendWhileBorrowed();
        retainedStorageIsBounded();
        releasingFromOtherThreadIsRejected();
        System.out.println("PASS scoped ProtoWriter reuse, independent nested/TL/direct buffers, byte parity, rollback, trimming and thread ownership");
    }

    private static void nestedBorrowWithoutAliasing() {
        ProtoWriter first;
        ProtoWriter retainedChild;
        ProtoWriter temporary;
        byte[] escaped;
        try (ProtoWriter outer = ProtoWriter.borrow()) {
            first = outer;
            outer.msg(2).uint32(1, 129);
            ProtoWriter tl = ProtoWriter.tl().string(7, "독립");
            try (ProtoWriter child = ProtoWriter.borrow()) {
                retainedChild = child;
                child.string(3, "마법").int32(6, -7);
                try (ProtoWriter deeper = ProtoWriter.borrow()) {
                    temporary = deeper;
                    check(outer != child && child != deeper && outer != deeper
                            && tl != outer && tl != child && tl != deeper, "live scopes have independent writers");
                    deeper.bool(8, true);
                    child.appendBody(deeper);
                }
                outer.appendBody(child);
            }
            check(tl.length() != 0, "borrow and nested close do not clear TL writer");
            outer.end();
            escaped = outer.bodyBytes();
        }
        final ProtoWriter standalone = ProtoWriter.newInstance();
        final byte[] expected = standalone.msg(2).uint32(1, 129).string(3, "마법").int32(6, -7).bool(8, true)
                .end().bodyBytes();
        check(Arrays.equals(escaped, expected), "assembled nested scopes match independent encoder");

        try (ProtoWriter reused = ProtoWriter.borrow(); ProtoWriter child = ProtoWriter.borrow();
                ProtoWriter deeper = ProtoWriter.borrow()) {
            check(reused == first && child == retainedChild && deeper != temporary,
                    "only the configured two scratch depths persist");
            check(reused.length() == 0 && child.length() == 0 && deeper.length() == 0, "new scopes begin empty");
            reused.rawByte(0);
            check(Arrays.equals(escaped, expected), "finished copies have no alias with reused writer storage");
        }
    }

    private static void copyAndRewindAtVarintBoundaries() {
        for (int size : new int[] {0, 1, 125, 126, 127, 128, 16_381, 16_382, 16_383, 16_384}) {
            final byte[] payload = new byte[size];
            Arrays.fill(payload, (byte) 0x91);
            byte[] directStyle = ProtoWriter.newInstance().uint64(1, 140_000L).msg(9).bytes(5, payload).end()
                    .fixed64(3, 0x4191_7525_91bfL).finishWithTrailer(0x403);
            try (ProtoWriter dest = ProtoWriter.borrow(); ProtoWriter row = ProtoWriter.borrow()) {
                dest.uint64(1, 140_000L);
                int checkpoint = dest.length();
                dest.msg(4).string(1, "폐기");
                expectFailure(IllegalArgumentException.class, () -> dest.rewindBody(dest.length() - 1));
                dest.rewindBody(checkpoint);
                row.bytes(5, payload);
                dest.msg(9);
                dest.appendBody(row);
                dest.end().fixed64(3, 0x4191_7525_91bfL);
                check(Arrays.equals(directStyle, dest.finishWithTrailer(0x403)), "literal byte parity size " + size);
                expectFailure(IllegalArgumentException.class, () -> dest.appendBody(dest));
                row.msg(3).rawByte(5);
                expectFailure(IllegalStateException.class, () -> dest.appendBody(row));
                check(Arrays.equals(directStyle, dest.finishWithTrailer(0x403)), "failed appends leave output unchanged");
            }
        }
    }

    private static void directAppendWhileBorrowed() {
        final int base = 3;
        final ByteBuffer bytes = ByteBuffer.allocate(4_096);
        final Assemble buffer = new Assemble();
        buffer.armExternal(bytes, base, bytes.capacity() - base);
        buffer.reset(ScopedProtoWriterRegression.class);
        final ProtoWriter direct = ProtoWriter.direct(buffer);
        try (ProtoWriter scratch = ProtoWriter.borrow()) {
            check(direct != scratch, "borrow does not alias an active direct writer");
            scratch.uint64(3, 0x1_0000_0000L).string(4, "혼합");
            direct.msg(2);
            direct.appendBody(scratch);
            direct.end();
            direct.finishDirect(false);
            byte[] expected = ProtoWriter.newInstance().msg(2).uint64(3, 0x1_0000_0000L)
                    .string(4, "혼합").end().bodyBytes();
            check(buffer.position() == expected.length, "append advances direct frame by exact encoded length");
            for (int i = 0; i < expected.length; i++) {
                check(bytes.get(base + i) == expected[i], "direct byte matches at " + i);
            }
            expectFailure(IllegalStateException.class, () -> scratch.appendBody(direct));
        } finally {
            direct.detachDirect();
            buffer.releaseResources();
        }
    }

    private static void retainedStorageIsBounded() throws Exception {
        final Field buffer = ProtoWriter.class.getDeclaredField("_a");
        final Field patches = ProtoWriter.class.getDeclaredField("_patch");
        buffer.setAccessible(true);
        patches.setAccessible(true);
        final ProtoWriter oversized;
        try (ProtoWriter borrowed = ProtoWriter.borrow()) {
            oversized = borrowed;
            oversized.bytes(1, new byte[300 * 1024]);
            for (int i = 0; i < 300; i++) oversized.msg(2);
            check(((byte[]) buffer.get(oversized)).length > 256 * 1024
                    && ((int[]) patches.get(oversized)).length > 256, "exercise retained-buffer and nesting limits");
            // Closing releases the partial message stack after this failed body.
            expectFailure(IllegalStateException.class, oversized::bodyBytes);
        }
        try (ProtoWriter clean = ProtoWriter.borrow()) {
            check(clean == oversized && clean.length() == 0 && clean.bodyBytes().length == 0,
                    "close rolls back an unfinished encode");
            check(((byte[]) buffer.get(clean)).length <= 256 * 1024 && ((int[]) patches.get(clean)).length <= 256,
                    "a rare oversized message cannot inflate the retained scratch writer");
        }
    }

    private static void releasingFromOtherThreadIsRejected() throws Exception {
        final AtomicReference<Throwable> result = new AtomicReference<>();
        try (ProtoWriter owner = ProtoWriter.borrow()) {
            owner.uint32(1, 300);
            final Thread other = Thread.ofVirtual().start(() -> {
                try (ProtoWriter local = ProtoWriter.borrow()) {
                    check(local != owner, "scratch writers are unique to the owning thread");
                    expectFailure(IllegalStateException.class, owner::close);
                    local.bytes(2, new byte[] {1, 2, 3});
                } catch (Throwable failure) {
                    result.set(failure);
                }
            });
            other.join(5_000L);
            check(!other.isAlive() && result.get() == null, "cross-thread isolation and rejected close");
            check(Arrays.equals(owner.bodyBytes(), new byte[] {8, (byte) 0xAC, 2}),
                    "foreign thread cannot release or overwrite owner's body");
        }
    }

    private static void expectFailure(Class<? extends RuntimeException> expected, Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) {
            if (expected.isInstance(failure)) return;
            throw failure;
        }
        throw new AssertionError("expected " + expected.getSimpleName());
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
