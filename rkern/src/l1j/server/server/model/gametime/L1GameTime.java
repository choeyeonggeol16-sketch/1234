/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model.gametime;

public class L1GameTime extends BaseTime {
    // 2003년 7월 3일 12:00(UTC)이 1월 1일 00:00
    private static final long BASE_TIME_IN_MILLIS_REAL = 1057233600000L;

    public L1GameTime(long timeMillis) {
        super(timeMillis);
    }

    public L1GameTime() {
        super();
    }

    @Override
    protected int makeTime(long timeMillis) {
        final long t1 = timeMillis - BASE_TIME_IN_MILLIS_REAL;
        if (t1 < 0) {
            throw new IllegalArgumentException();
        }
        final long t2 = (t1 * 6) / 1000L;
        final long t3 = t2 % 3; // 시간이 3의 배수가 되도록 조정
        return (int) (t2 - t3);
    }

    @Override
    protected long getBaseTimeInMil() {
        return BASE_TIME_IN_MILLIS_REAL;
    }
}
