package l1j.server.server.model.character.actionValidator;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;

public class WalkDelayValidator {

    public static boolean validate(L1PcInstance owner, int x, int y, long interval) {
        if (!Config.WALK_POSITION_CHECK) {
            return true;
        }
        long lastMillis = owner.getLastMoveActionMillis();
        if (lastMillis > 0) {
            long currentMillis = System.currentTimeMillis();
            long diffTime = currentMillis - lastMillis;

            long allowedInterval = (long) interval - Config.WALK_NETWORK_TOLERANCE;

            if (diffTime < allowedInterval) {       
                // 첫 적발 시 앵커 좌표 기록
                if (owner.getSpeedHackStrikes() == 0) {
                    owner.setSpeedHackAnchorX(owner.getX());
                    owner.setSpeedHackAnchorY(owner.getY());
                    owner.setSpeedHackAnchorH(owner.getHeading());
                }
                
                owner.addSpeedHackStrike(); // 스트라이크 누적
                if (owner.getSpeedHackStrikes() >= Config.WALK_DELAY_OVER_PENDING) {
                	owner.start_teleport(
                            owner.getSpeedHackAnchorX(), 
                            owner.getSpeedHackAnchorY(), 
                            owner.getMapId(),
                            owner.getSpeedHackAnchorH(), 
                            169, false, false);
                    owner.setLastMoveActionMillis(currentMillis);            
                    if (owner.isGm()) {
                        owner.sendPackets("\\fY■■ [" + owner.getName() + "]님 스피드핵 감지 (위치 롤백) ■■");
                    }             
                    // 처벌 후 초기화
                    owner.setSpeedHackStrikes(0);
                    return false; // 패킷 처리 거부
                }
            } else {
                // 정상적인 딜레이로 이동하면 선량한 유저로 간주하고 스트라이크 차감
                owner.reduceSpeedHackStrike();
            }
        }
        
        return true;
    }
}