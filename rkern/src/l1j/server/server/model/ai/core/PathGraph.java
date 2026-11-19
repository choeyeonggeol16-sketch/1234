package l1j.server.server.model.ai.core;

import java.util.Arrays;

/**
 * PathMaker가 미리 컴파일한 정적 PathNode 그래프.
 * 런타임 hot path에서는 객체 노드/컬렉션을 사용하지 않고 flat array만 읽는다.
 */
final class PathGraph {
    final int mapId;
    final int baseX;
    final int baseY;
    final int width;
    final int height;

    final int[] nodeX;
    final int[] nodeY;
    final int[] edgeStart;
    final byte[] edgeCount;
    final int[] edgeTarget;

    final int sectionBaseX;
    final int sectionBaseY;
    final int sectionWidth;
    final int sectionHeight;
    final int[] sectionHead;
    final int[] nodeNextInSection;

    PathGraph(
            int mapId,
            int baseX,
            int baseY,
            int width,
            int height,
            int[] nodeX,
            int[] nodeY,
            int[] edgeStart,
            byte[] edgeCount,
            int[] edgeTarget) {
        this.mapId = mapId;
        this.baseX = baseX;
        this.baseY = baseY;
        this.width = width;
        this.height = height;
        this.nodeX = nodeX;
        this.nodeY = nodeY;
        this.edgeStart = edgeStart;
        this.edgeCount = edgeCount;
        this.edgeTarget = edgeTarget;

        sectionBaseX = baseX >> 4;
        sectionBaseY = baseY >> 4;
        final int maxX = (baseX + Math.max(0, width - 1)) >> 4;
        final int maxY = (baseY + Math.max(0, height - 1)) >> 4;
        sectionWidth = Math.max(1, maxX - sectionBaseX + 1);
        sectionHeight = Math.max(1, maxY - sectionBaseY + 1);
        sectionHead = new int[sectionWidth * sectionHeight];
        Arrays.fill(sectionHead, -1);
        nodeNextInSection = new int[nodeX.length];
        Arrays.fill(nodeNextInSection, -1);

        for (int i = 0; i < nodeX.length; i++) {
            final int sx = (nodeX[i] >> 4) - sectionBaseX;
            final int sy = (nodeY[i] >> 4) - sectionBaseY;
            if ((sx | sy) < 0 || sx >= sectionWidth || sy >= sectionHeight) {
                continue;
            }
            final int section = sx * sectionHeight + sy;
            nodeNextInSection[i] = sectionHead[section];
            sectionHead[section] = i;
        }
    }

    int nodeCount() {
        return nodeX.length;
    }
}
