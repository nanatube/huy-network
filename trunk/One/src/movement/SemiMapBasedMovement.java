package movement;

import java.util.List;
import java.util.Vector;

import movement.map.MapNode;
import core.Coord;
import core.Settings;

public class SemiMapBasedMovement extends MapBasedMovement {
	/** Range of the cluster */
	public static final String CLUSTER_RANGE = "clusterRange";
	/** Center point of the cluster */
	public static final String CLUSTER_CENTER = "clusterCenter";

	private int p_x_center = 100, p_y_center = 100;
	private double p_range = 100.0;
	private Coord lastWaypoint;
	private static final int PATH_LENGTH = 1;

	public SemiMapBasedMovement(Settings s) {
		super(s);
		if (s.contains(CLUSTER_RANGE)) {
			this.p_range = s.getDouble(CLUSTER_RANGE);
		}
		if (s.contains(CLUSTER_CENTER)) {
			int[] center = s.getCsvInts(CLUSTER_CENTER, 2);
			this.p_x_center = center[0];
			this.p_y_center = center[1];
		}
	}

	public SemiMapBasedMovement(SemiMapBasedMovement semiMapBasedMovement) {
		super(semiMapBasedMovement);
		this.p_range = semiMapBasedMovement.p_range;
		this.p_x_center = semiMapBasedMovement.p_x_center;
		this.p_y_center = semiMapBasedMovement.p_y_center;
	}

	@Override
	public SemiMapBasedMovement replicate() {
		return new SemiMapBasedMovement(this);
	}

	protected Coord randomCoord() {
		double x = (rng.nextDouble() * 2 - 1) * this.p_range;
		double y = (rng.nextDouble() * 2 - 1) * this.p_range;
		while (x * x + y * y > this.p_range * this.p_range) {
			x = (rng.nextDouble() * 2 - 1) * this.p_range;
			y = (rng.nextDouble() * 2 - 1) * this.p_range;
		}
		x += this.p_x_center;
		y += this.p_y_center;
		return new Coord(x, y);
	}
	
	@Override
	public Path getPath() {
		double d = rng.nextDouble();
		Path p;
		if (d > 0.1) {
			p = new Path(generateSpeed());
			p.addWaypoint(lastWaypoint.clone());
			Coord c = lastWaypoint;

			for (int i = 0; i < PATH_LENGTH; i++) {
				c = randomCoord();
				p.addWaypoint(c);
			}

			this.lastWaypoint = c;
		} else {
			p = new Path(generateSpeed());
			MapNode curNode = lastMapNode;
			MapNode prevNode = lastMapNode;
			MapNode nextNode = null;
			List<MapNode> neighbors;
			Coord nextCoord;

			assert lastMapNode != null : "Tried to get a path before placement";

			// start paths from current node
			p.addWaypoint(curNode.getLocation());

			int pathLength = 1;

			for (int i = 0; i < pathLength; i++) {
				neighbors = curNode.getNeighbors();
				Vector<MapNode> n2 = new Vector<MapNode>(neighbors);
				if (!this.backAllowed) {
					n2.remove(prevNode); // to prevent going back
				}

				if (n2.size() == 0) { // only option is to go back
					nextNode = prevNode;
				} else { // choose a random node from remaining neighbors
					nextNode = n2.get(rng.nextInt(n2.size()));
				}

				prevNode = curNode;

				nextCoord = nextNode.getLocation();
				curNode = nextNode;

				p.addWaypoint(nextCoord);
				this.lastWaypoint = nextCoord;
			}

			lastMapNode = curNode;
		}
		return p;
	}

	@Override
	public Coord getInitialLocation() {
		super.getInitialLocation();
		assert rng != null : "MovementModel not initialized!";
		Coord c = randomCoord();

		this.lastWaypoint = c;

		return c;
	}
}
