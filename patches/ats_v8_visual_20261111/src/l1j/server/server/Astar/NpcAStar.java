package l1j.server.server.Astar;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.types.Point;

public class NpcAStar {
	private class Path implements Comparable<Path> {
		public Point point;
		public int f;
		public int g;
		public int depth;
		public Path parent;

		/** * Default c'tor. */
		public Path() {
			parent = null;
			point = null;
			g = f = 0;
			depth = 0;
		}

		/**
		 * * C'tor by copy another object. * * @param p The path object to
		 * clone.
		 */
		public Path(Path p) {
			this();
			parent = p;
			g = p.g;
			f = p.f;
			depth = p.depth;
		}

		/**
		 * * Compare to another object using the total cost f. * * @param o The
		 * object to compare to. * @see Comparable#compareTo() * @return
		 * <code>less than 0</code> This object is smaller * than <code>0</code>
		 * ; * <code>0</code> Object are the same.
		 * <code>bigger than 0</code>
		 * This object is bigger * than o.
		 */
		public int compareTo(Path p) {
			try {
				return (int) (f - p.f);
			} catch (NullPointerException e) {
				return 0;
			}
		}

		/**
		 * * Get the last point on the path. * * @return The last point visited
		 * by the path.
		 */
		public Point getPoint() {
			return point;
		}

		/** * Set the */
		public void setPoint(Point p) {
			point = p;
		}
	}

	/**
	 * * Check if the current node is a goal for the problem. * * @param node
	 * The node to check. * @return <code>true</code> if it is a goal,
	 * <code>false</else> otherwise.
	 */
	protected boolean isGoal(Point node)
	{
		return _target.isSamePoint(node);
	}

	/**
	 * * Cost for the operation to go to <code>to</code> from *
	 * <code>from</from>.                   *                   * @param from The node we are leaving.                   * @param to The node we are reaching.                   * @return The cost of the operation.
	 */
	protected int g(Point from, Point to)
	{
		int result = 0;
		L1Location loc = new L1Location();
		loc.setX(to.getX());
		loc.setY(to.getY());
		loc.setMap(_map);

		if (!_map.isPassable(to)) {
			result += 10;
		}

		if (from.getX() == to.getX()
				&& from.getY() == to.getY()) {
			result += 0;
		} 
		else {
			result += 10;
		}

		return result;
	}

	/**
	 * * Estimated cost to reach a goal node. * An admissible heuristic never
	 * gives a cost bigger than the real * one. *
	 * <code>from</from>.                   *                   * @param from The node we are leaving.                   * @param to The node we are reaching.                   * @return The estimated cost to reach an object.
	 */
	protected int h(Point from, Point to)
	{
		return  (int) to.getAstarDistance(_target);
	}

	/**
	 * * Generate the successors for a given node. * * @param node The node we
	 * want to expand. * @return A list of possible next steps.
	 */
	protected List<Point> generateSuccessors(Point node)
	{
		List<Point> result = new LinkedList<Point>();

		if (node.isSamePoint(_start)) {
			if (node.getTileLineDistance(_target) == 1) {
				result.add(_target);
			}
			if (_map.isPassable(node.getX(), node.getY(), 0)) {
				result.add(new Point(node.getX(), node.getY() - 1));
			}
			if (_map.isPassable(node.getX(), node.getY(), 1)) {
				result.add(new Point(node.getX() + 1, node.getY() - 1));
			}
			if (_map.isPassable(node.getX(), node.getY(), 2)) {
				result.add(new Point(node.getX() + 1, node.getY()));
			}
			if (_map.isPassable(node.getX(), node.getY(), 3)) {
				result.add(new Point(node.getX() + 1, node.getY() + 1));
			}
			if (_map.isPassable(node.getX(), node.getY(), 4)) {
				result.add(new Point(node.getX(), node.getY() + 1));
			}
			if (_map.isPassable(node.getX(), node.getY(), 5)) {
				result.add(new Point(node.getX() - 1, node.getY() + 1));
			}
			if (_map.isPassable(node.getX(), node.getY(), 6)) {
				result.add(new Point(node.getX() - 1, node.getY()));
			}
			if (_map.isPassable(node.getX(), node.getY(), 7)) {
				result.add(new Point(node.getX() - 1, node.getY() - 1));
			}
		} else {
			if (node.getTileLineDistance(_target) == 1) {
				result.add(_target);
			}
			if (_map.isUserPassable(node.getX(), node.getY(), 0)) {
				result.add(new Point(node.getX(), node.getY() - 1));
			}
			if (_map.isUserPassable(node.getX(), node.getY(), 1)) {
				result.add(new Point(node.getX() + 1, node.getY() - 1));
			}
			if (_map.isUserPassable(node.getX(), node.getY(), 2)) {
				result.add(new Point(node.getX() + 1, node.getY()));
			}
			if (_map.isUserPassable(node.getX(), node.getY(), 3)) {
				result.add(new Point(node.getX() + 1, node.getY() + 1));
			}
			if (_map.isUserPassable(node.getX(), node.getY(), 4)) {
				result.add(new Point(node.getX(), node.getY() + 1));
			}
			if (_map.isUserPassable(node.getX(), node.getY(), 5)) {
				result.add(new Point(node.getX() - 1, node.getY() + 1));
			}
			if (_map.isUserPassable(node.getX(), node.getY(), 6)) {
				result.add(new Point(node.getX() - 1, node.getY()));
			}
			if (_map.isUserPassable(node.getX(), node.getY(), 7)) {
				result.add(new Point(node.getX() - 1, node.getY() - 1));
			}
		}
		return result;
	}

	//	private PriorityQueue<Path> paths;
	private Queue<Path> paths;
	private HashMap<Point, Integer> mindists;
	private int lastCost;
	private int expandedCounter;
	private L1Map _map;
	private Point _start;
	private Point _target;
	private List<Point> _finalPath;
	private int _tryCount;

	/**
	 * * Check how many times a node was expanded. * * @return A counter of how
	 * many times a node was expanded.
	 */
	public int getExpandedCounter() {
		return expandedCounter;
	}

	public Point getTarget() {
		return _target;
	}

	public List<Point> getFinalPath() {
		return _finalPath;
	}

	public void eraseOneNodeFromFinalPath() {
		if (_finalPath.size() > 0)
			_finalPath.remove(0);
	}

	public void resetTryCount() {
		_tryCount = 0;
	}

	public int getTryCount() {
		return _tryCount;
	}
	/** * Default c'tor. */
	public NpcAStar(L1Map map, Point target) {
		paths = new PriorityQueue<Path>();
		mindists = new HashMap<Point, Integer>();
		expandedCounter = 0;
		lastCost = 0;
		_map = map;
		_target = target;
		_tryCount = 0;
	}

	public void setTarget(L1Map map, Point target) {
		_map = map;
		_target = target;
		_tryCount = 0;
	}

	/**
	 * * Total cost function to reach the node <code>to</code> from *
	 * <code>from</code>. * * The total cost is defined as: f(x) = g(x) + h(x).
	 * * @param from The node we are leaving. * @param to The node we are
	 * reaching. * @return The total cost.
	 */
	protected void f(Path p, Point from, Point to) {
		int g = g(from, to) + ((p.parent != null) ? p.parent.g : 0);
		int h = h(from, to);
		p.g = g;
		p.f = g + h;
	}

	/** * Expand a path. * * @param path The path to expand. */
	private void expand(Path path) {
		Point p = path.getPoint();
		Integer min = mindists.get(path.getPoint());
		if (min == null || min > path.f)
			mindists.put(path.getPoint(), path.f);
		else
			return;
		List<Point> successors = generateSuccessors(p);
		for (Point t : successors) {
			Path newPath = new Path(path);
			newPath.setPoint(t);
			++newPath.depth;
			if (newPath.depth < 30) {
				f(newPath, path.getPoint(), t);
				if (newPath.f < 400) {
					synchronized (paths) {
						paths.offer(newPath);
					}
				}
			}
		}
		expandedCounter++;
	}

	/**
	 * * Get the cost to reach the last node in the path. * * @return The cost
	 * for the found path.
	 */
	public int getCost() {
		return lastCost;
	}

	/**
	 * * Find the shortest path to a goal starting from * <code>start</code>. *
	 * * @param start The initial node. * @return A list of nodes from the
	 * initial point to a goal, * <code>null</code> if a path doesn't exist.
	 */
	public List<Point> compute(Point start) {
		try {
			synchronized (paths) {
				paths.clear();
			}
			mindists.clear();
			expandedCounter = 0;
			lastCost = 0;
			_start = start;
			++_tryCount;

			Path root = new Path();
			root.setPoint(start); /* Needed if the initial point has a cost. */

			f(root, start, start);
			expand(root);
			for (;;) {
				Path p = null;
				synchronized (paths) {
					p = paths.poll();
				}
				if (p == null) {
					lastCost = Integer.MAX_VALUE;
					_finalPath = null;
					return null;
				}
				Point last = p.getPoint();
				lastCost = p.g;
				if (isGoal(last)) {
					LinkedList<Point> retPath = new LinkedList<Point>();

					boolean exist = false;

					for (Path i = p; i != null; i = i.parent) {
						if ((i.getPoint().getX() != last.getX() || i.getPoint().getY() != last.getY())
								&& (i.getPoint().getX() != start.getX() || i.getPoint().getY() != start.getY())) {
							L1Location loc = new L1Location(i.getPoint().getX(), i.getPoint().getY(), _map);

							for (L1Object object : L1World.getInstance().getVisiblePoint(loc, 1)) {
								if (object instanceof L1Character && 
										(object instanceof L1PcInstance 
												|| object instanceof SummonInstance 
												|| object instanceof PetInstance)) {
									if (!((L1Character)object).isDead()) {
										exist = true;
									}
								}
							}
						}
						retPath.addFirst(i.getPoint());
					}
					_finalPath = retPath; 
					if (!exist) {
						resetTryCount();
					}
					return retPath;
				}
				expand(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}    
