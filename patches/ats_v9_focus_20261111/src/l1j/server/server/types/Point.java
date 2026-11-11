/* This program is free software; you can redistribute it and/or modify
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
package l1j.server.server.types;

public class Point {

	protected int _x = 0;
	protected int _y = 0;

	public Point() {
	}

	public Point(int x, int y) {
		_x = x;
		_y = y;
	}

	public Point(Point pt) {
		_x = pt._x;
		_y = pt._y;
	}

	public int getX() {
		return _x;
	}

	public void setX(int x) {
		_x = x;
	}

	public int getY() {
		return _y;
	}

	public void setY(int y) {
		_y = y;
	}

	public void set(Point pt) {
		_x = pt._x;
		_y = pt._y;
	}

	public void set(int x, int y) {
		_x = x;
		_y = y;
	}

	private static final int HEADING_TABLE_X[] = { 0, 1, 1, 1, 0, -1, -1, -1 };
	private static final int HEADING_TABLE_Y[] = { -1, -1, 0, 1, 1, 1, 0, -1 };

	public void forward(int heading) {
		_x += HEADING_TABLE_X[heading];
		_y += HEADING_TABLE_Y[heading];
	}

	public void backward(int heading) {
		_x -= HEADING_TABLE_X[heading];
		_y -= HEADING_TABLE_Y[heading];
	}
	
	public int getHeading(Point target) {
		int xDiff = target.getX() - _x;
		int yDiff = target.getY() - _y;
		
		for (int i = 0; i < 8; ++i) {
			if (HEADING_TABLE_X[i] == xDiff
					&& HEADING_TABLE_Y[i] == yDiff) {
				return i;
			}
		}
		
		return -1;
	}
	public double getAstarDistance(Point pt) {
		long diffX = pt.getX() - this.getX();
		long diffY = pt.getY() - this.getY();
		return (2 * Math.sqrt((diffX * diffX) + (diffY * diffY)));
	}
	public double getLineDistance(Point pt) {
		long diffX = pt.getX() - this.getX();
		long diffY = pt.getY() - this.getY();
		return Math.sqrt((diffX * diffX) + (diffY * diffY));
	}
	
	public int getTileLineDistance(Point pt) {
		return Math.max(Math.abs(pt.getX() - getX()), Math.abs(pt.getY() - getY()));
	}

	public int getTileDistance(Point pt) {
		return Math.abs(pt.getX() - getX()) + Math.abs(pt.getY() - getY());
	}
	
	private static int ccw(Point pt1, Point pt2, Point pt3)
	{
		return (pt2.getX() - pt1.getX()) * (pt3.getY() - pt1.getY()) - (pt2.getY() - pt1.getY()) * (pt3.getX() - pt1.getX());
	}
	
	public boolean isInScreen(Point pt) {
		int max_point = 15;
		Point pt1 = new Point(getX() + 2, getY() - max_point);
		Point pt2 = new Point(getX() - max_point, getY() + 2);
		Point pt3 = new Point(getX() - 2, getY() + max_point);
		Point pt4 = new Point(getX() + max_point, getY() - 2);
		
		if (ccw(pt1, pt2, pt) > 0
				|| ccw(pt2, pt3, pt) > 0
				|| ccw(pt3, pt4, pt) > 0
				|| ccw(pt4, pt1, pt) > 0
				)
		{
			return false;
		}
		
		return true; 
	}

	public boolean isSamePoint(Point pt) {
		return (pt.getX() == getX() && pt.getY() == getY());
	}

	@Override
	public int hashCode() {
		return 7 * getX() + getY();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Point)) {
			return false;
		}
		Point pt = (Point) obj;
		return (this.getX() == pt.getX()) && (this.getY() == pt.getY());
	}

	@Override
	public String toString() {
		return String.format("(%d, %d)", _x, _y);
	}
}
