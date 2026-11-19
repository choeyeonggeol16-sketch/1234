package l1j.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.utils.FileUtil;

/**
 * 텍스트 맵을 캐싱 해 read 시간을 단축한다
 */
public class CachedMapReader extends MapReader {

    private static final String MAP_DIR = "./maps/";
    private static final String CACHE_DIR = "./data/mapcache/";
    private static final String CACHE_EXT = ".map";

    private static final int CACHE_MAGIC = 0x4C314D50; // "L1MP"
    private static final int CACHE_VER   = 1;
    private static final int IO_BUF = 256 * 1024;

    private static final TextMapReader TEXT = new TextMapReader();

    private static File textFile(int mapId) {
        return new File(MAP_DIR + mapId + ".txt");
    }

    private static File cacheFile(int mapId) {
        return new File(CACHE_DIR + mapId + CACHE_EXT);
    }

    private static File cacheTempFile(int mapId) {
        return new File(CACHE_DIR + mapId + CACHE_EXT + ".tmp");
    }

    private static void ensureCacheDir() throws IOException {
        final File dir = new File(CACHE_DIR);
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                throw new IOException("CACHE_DIR is not a directory: " + CACHE_DIR);
            }
            return;
        }
        if (!dir.mkdirs()) {
            if (!dir.exists() || !dir.isDirectory()) {
                throw new IOException("Failed to create cache dir: " + CACHE_DIR);
            }
        }
    }

    private ArrayList<Integer> listMapIds() {
        final File mapDir = new File(MAP_DIR);
        final String[] names = mapDir.list();
        if (names == null || names.length == 0) {
            return new ArrayList<>(0);
        }

        final ArrayList<Integer> ids = new ArrayList<>(names.length);
        for (String name : names) {
            if (name == null) continue;

            final File mapFile = new File(mapDir, name);
            if (!mapFile.isFile()) continue;

            final String ext = FileUtil.getExtension(mapFile);
            if (ext == null || !ext.equalsIgnoreCase("txt")) continue;

            try {
                final String idStr = FileUtil.getNameWithoutExtension(mapFile);
                final int id = Integer.parseInt(idStr);
                ids.add(id);
            } catch (NumberFormatException ignore) {
                // skip
            }
        }

        ids.sort(Integer::compareTo);
        return ids;
    }

    private static void moveTmpToDst(File tmp, File dst) throws IOException {
        try {
            Files.move(tmp.toPath(), dst.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), dst.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private L1V1Map cacheMap(final int mapId) throws IOException {
        ensureCacheDir();

        final File src = textFile(mapId);
        if (!src.exists()) {
            throw new FileNotFoundException("Map txt not found: " + src.getPath());
        }
        final long srcMtime = src.lastModified();
        final L1V1Map map = (L1V1Map) TEXT.read(mapId);
        if (isGludioTerrain(mapId) && !hasGludioEntry(map)) {
            throw new IOException("Gludio source map " + mapId
                    + " has no traversable entry near (32801,32862): " + src.getPath());
        }

        final int w = map.getWidth();
        final int h = map.getHeight();
        if (w <= 0 || h <= 0 || w > 32768 || h > 32768) {
            throw new IOException("Invalid map size: id=" + mapId + " w=" + w + " h=" + h);
        }

        final File tmp = cacheTempFile(mapId);
        final File dst = cacheFile(mapId);

        if (tmp.exists()) tmp.delete();

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(tmp), IO_BUF))) {

            out.writeInt(CACHE_MAGIC);
            out.writeInt(CACHE_VER);
            out.writeLong(srcMtime);

            out.writeInt(map.getId());
            out.writeInt(map.getX());
            out.writeInt(map.getY());
            out.writeInt(w);
            out.writeInt(h);

            final int total = Math.multiplyExact(w, h);
            final byte[] flat = new byte[total];

            // Persist actual terrain bytes; the previous commented copy wrote zero-only cache files.
            map.copyRawTilesTo(flat);

            out.write(flat);
            out.flush();
        }

        moveTmpToDst(tmp, dst);
        return map;
    }

    private L1V1Map rebuildCache(final int mapId) throws IOException {
        final File f = cacheFile(mapId);
        if (f.exists()) f.delete();
        final File tmp = cacheTempFile(mapId);
        if (tmp.exists()) tmp.delete();
        return cacheMap(mapId);
    }

    private static boolean isGludioTerrain(int mapId) {
        return mapId == 730 || mapId == 731;
    }

    private static boolean hasGludioEntry(L1Map map) {
        for (int dy = -5; dy <= 5; dy++) {
            for (int dx = -5; dx <= 5; dx++) {
                final int x = 32801 + dx;
                final int y = 32862 + dy;
                if (map.isInMap(x, y) && map.isPassable(x, y)) return true;
            }
        }
        return false;
    }

    @Override
    public L1Map read(final int mapId) throws IOException {
        final File file = cacheFile(mapId);
        if (!file.exists()) return cacheMap(mapId);

        // Do not rebuild inside the open DataInputStream: Windows cannot reliably
        // replace/delete an open map cache file.
        try {
            final int id, xLoc, yLoc, width, height;
            final byte[] flat;
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(file), IO_BUF))) {
                final int magic = in.readInt();
                final int ver = in.readInt();
                final long cachedSrcMtime = in.readLong();
                if (magic != CACHE_MAGIC || ver != CACHE_VER)
                    throw new IOException("Map cache header/version mismatch " + mapId);
                final File src = textFile(mapId);
                if (!src.isFile() || src.lastModified() != cachedSrcMtime)
                    throw new IOException("Map cache source missing/modified " + mapId);

                id = in.readInt();
                if (mapId != id) throw new IOException("Map cache id mismatch " + mapId);
                xLoc = in.readInt();
                yLoc = in.readInt();
                width = in.readInt();
                height = in.readInt();
                if (width <= 0 || height <= 0 || width > 32768 || height > 32768)
                    throw new IOException("Map cache dimensions invalid " + mapId);
                final int total = Math.multiplyExact(width, height);
                flat = new byte[total];
                in.readFully(flat);
            }
            // Earlier versions wrote a fully zero-filled byte array to cache.
            // Source mtime can still match; never accept those broken files.
            boolean allZero = true;
            for (byte tile : flat) {
                if (tile != 0) { allZero = false; break; }
            }
            if (allZero) throw new IOException("Map cache has zero-only terrain " + mapId);

            final MapsTable mt = MapsTable.getInstance();
            final L1V1Map cached = new L1V1Map(id, flat, width, height, xLoc, yLoc,
                    mt.isUnderwater(mapId), mt.isMarkable(mapId), mt.isTeleportable(mapId),
                    mt.isEscapable(mapId), mt.isUseResurrection(mapId), mt.isUsePainwand(mapId),
                    mt.isEnabledDeathPenalty(mapId), mt.isTakePets(mapId), mt.isRecallPets(mapId),
                    mt.isUsableItem(mapId), mt.isUsableSkill(mapId), mt.isRuler(mapId));
            if (isGludioTerrain(mapId) && !hasGludioEntry(cached))
                throw new IOException("Gludio cached entry terrain is not traversable " + mapId);
            return cached;
        } catch (IOException corruptCache) {
            // The source TXT is authoritative. Rebuild only after the cache
            // input stream has been closed. If TXT is invalid, fail explicitly.
            return rebuildCache(mapId);
        }
    }

    @Override
    public Map<Integer, L1Map> read() throws IOException {
        final ArrayList<Integer> ids = listMapIds();
        final int n = ids.size();
        final Map<Integer, L1Map> maps = new HashMap<>(Math.max(16, (int) (n / 0.75f) + 1));

        if (n == 0) return maps;

        int threads = Math.min(8, Runtime.getRuntime().availableProcessors());
        threads = Math.min(threads, n);

        // 💡 단일 스레드 모드일 때의 에러 방어막
        if (threads <= 1) {
            for (int id : ids) {
                try {
                    maps.put(id, read(id));
                } catch (Exception e) {
                    if (isGludioTerrain(id))
                        System.err.println("[Gludio-Indun] Terrain load failed map=" + id + " reason=" + e);
                    else
                        System.err.println("[맵 로딩 스킵] " + id + "번 맵 파일에 오류가 있습니다. (서버 부팅 강행)");
                }
            }
            return maps;
        }

        final ExecutorService es = Executors.newFixedThreadPool(threads);
        final CompletionService<Map.Entry<Integer, L1Map>> cs = new ExecutorCompletionService<>(es);

        try {
            for (int id : ids) {
                cs.submit(() -> {
                    try {
                        return new AbstractMap.SimpleEntry<>(id, read(id));
                    } catch (Exception e) {
                        if (isGludioTerrain(id))
                            System.err.println("[Gludio-Indun] Terrain load failed map=" + id + " reason=" + e);
                        else
                            System.err.println("[맵 로딩 스킵] " + id + "번 맵 파일(.txt)읽기 실패 TextMapReader.java 맵 추가바랍니다.");
                        return null;
                    }
                });
            }

            for (int i = 0; i < n; i++) {
                try {
                    Map.Entry<Integer, L1Map> e = cs.take().get();
                    if (e != null) {
                        maps.put(e.getKey(), e.getValue());
                    }
                } catch (Exception ex) {
                    System.err.println("[시스템] 맵 스레드 로딩 중 무시 가능한 에러 발생.");
                }
            }

            return maps;
        } finally {
            es.shutdown();
        }
    }
}