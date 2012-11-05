package movement;

import input.WKTMapReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;

import movement.map.MapNode;
import movement.map.SimMap;
import core.Coord;
import core.Settings;
import core.SettingsError;
import core.SimError;

public class SemiClusterMovement extends RandomWaypoint {

	/** Range of the cluster */
	public static final String CLUSTER_RANGE = "clusterRange";
	/** Center point of the cluster */
	public static final String CLUSTER_CENTER = "clusterCenter";

	private double p_x_center = 100, p_y_center = 100;
	private double p_range = 100.0;
	protected MapNode lastMapNode;
	private SimMap map = null;
	/** map based movement model's settings namespace ({@value} ) */
	public static final String MAP_BASE_MOVEMENT_NS = "MapBasedMovement";
	/** number of map files -setting id ({@value} ) */
	public static final String NROF_FILES_S = "nrofMapFiles";
	/** map file -setting id ({@value} ) */
	public static final String FILE_S = "mapFile";
	/** map cache -- in case last mm read the same map, use it without loading */
	private static SimMap cachedMap = null;
	/** names of the previously cached map's files (for hit comparison) */
	private static List<String> cachedMapFiles = null;
	/** how many map files are read */
	private int nrofMapFilesRead = 0;
	/** max nrof map nodes to travel/path */
	protected int maxPathLength = 100;
	/** min nrof map nodes to travel/path */
	protected int minPathLength = 10;
	/** May a node choose to move back the same way it came at a crossing */
	protected boolean backAllowed;

	public void setCenter(Coord coord) {
		this.p_x_center = coord.getX();
		this.p_y_center = coord.getY();
	}

	public SemiClusterMovement(Settings s) {
		super(s);
		if (s.contains(CLUSTER_RANGE)) {
			this.p_range = s.getDouble(CLUSTER_RANGE);
		}
		if (s.contains(CLUSTER_CENTER)) {
			int[] center = s.getCsvInts(CLUSTER_CENTER, 2);
			this.p_x_center = center[0];
			this.p_y_center = center[1];
		}
		map = readMap();
		maxPathLength = 100;
		minPathLength = 10;
		backAllowed = false;
	}

	public SemiClusterMovement(SemiClusterMovement semiClusterMovement) {
		super(semiClusterMovement);
		this.p_range = semiClusterMovement.p_range;
		this.p_x_center = semiClusterMovement.p_x_center;
		this.p_y_center = semiClusterMovement.p_y_center;
		this.map = semiClusterMovement.map;
		this.minPathLength = semiClusterMovement.minPathLength;
		this.maxPathLength = semiClusterMovement.maxPathLength;
		this.backAllowed = semiClusterMovement.backAllowed;
	}

	@Override
	public Coord getInitialLocation() {
		List<MapNode> nodes = map.getNodes();
		MapNode n, n2;
		Coord n2Location, nLocation, placement;
		double dx, dy;
		double rnd = rng.nextDouble();
		n = nodes.get(rng.nextInt(nodes.size()));

		// choose a random neighbor of the selected node
		n2 = n.getNeighbors().get(rng.nextInt(n.getNeighbors().size()));

		nLocation = n.getLocation();
		n2Location = n2.getLocation();

		placement = n.getLocation().clone();

		dx = rnd * (n2Location.getX() - nLocation.getX());
		dy = rnd * (n2Location.getY() - nLocation.getY());

		placement.translate(dx, dy); // move coord from n towards n2

		this.lastMapNode = n;
		this.setCenter(n.getLocation());
		this.lastWaypoint = n.getLocation();
		return placement;
	}

	private SimMap readMap() {
		SimMap simMap;
		Settings settings = new Settings(MAP_BASE_MOVEMENT_NS);
		WKTMapReader r = new WKTMapReader(true);

		if (cachedMap == null) {
			cachedMapFiles = new ArrayList<String>(); // no cache present
		} else { // something in cache
			// check out if previously asked map was asked again
			SimMap cached = checkCache(settings);
			if (cached != null) {
				nrofMapFilesRead = cachedMapFiles.size();
				return cached; // we had right map cached -> return it
			} else { // no hit -> reset cache
				cachedMapFiles = new ArrayList<String>();
				cachedMap = null;
			}
		}

		try {
			int nrofMapFiles = settings.getInt(NROF_FILES_S);

			for (int i = 1; i <= nrofMapFiles; i++) {
				String pathFile = settings.getSetting(FILE_S + i);
				cachedMapFiles.add(pathFile);
				r.addPaths(new File(pathFile), i);
			}

			nrofMapFilesRead = nrofMapFiles;
		} catch (IOException e) {
			throw new SimError(e.toString(), e);
		}

		simMap = r.getMap();
		checkMapConnectedness(simMap.getNodes());
		// mirrors the map (y' = -y) and moves its upper left corner to origo
		simMap.mirror();
		Coord offset = simMap.getMinBound().clone();
		simMap.translate(-offset.getX(), -offset.getY());
		checkCoordValidity(simMap.getNodes());

		cachedMap = simMap;
		return simMap;
	}

	private void checkCoordValidity(List<MapNode> nodes) {
		// Check that all map nodes are within world limits
		for (MapNode n : nodes) {
			double x = n.getLocation().getX();
			double y = n.getLocation().getY();
		}
	}

	private void checkMapConnectedness(List<MapNode> nodes) {
		Set<MapNode> visited = new HashSet<MapNode>();
		Queue<MapNode> unvisited = new LinkedList<MapNode>();
		MapNode firstNode;
		MapNode next = null;

		if (nodes.size() == 0) {
			throw new SimError("No map nodes in the given map");
		}

		firstNode = nodes.get(0);

		visited.add(firstNode);
		unvisited.addAll(firstNode.getNeighbors());

		while ((next = unvisited.poll()) != null) {
			visited.add(next);
			for (MapNode n : next.getNeighbors()) {
				if (!visited.contains(n) && !unvisited.contains(n)) {
					unvisited.add(n);
				}
			}
		}

		if (visited.size() != nodes.size()) { // some node couldn't be reached
			MapNode disconnected = null;
			for (MapNode n : nodes) { // find an example node
				if (!visited.contains(n)) {
					disconnected = n;
					break;
				}
			}
			throw new SettingsError("SimMap is not fully connected. Only "
					+ visited.size() + " out of " + nodes.size()
					+ " map nodes " + "can be reached from " + firstNode
					+ ". E.g. " + disconnected + " can't be reached");
		}
	}

	private SimMap checkCache(Settings settings) {
		int nrofMapFiles = settings.getInt(NROF_FILES_S);

		if (nrofMapFiles != cachedMapFiles.size() || cachedMap == null) {
			return null; // wrong number of files
		}

		for (int i = 1; i <= nrofMapFiles; i++) {
			String pathFile = settings.getSetting(FILE_S + i);
			if (!pathFile.equals(cachedMapFiles.get(i - 1))) {
				return null; // found wrong file name
			}
		}

		// all files matched -> return cached map
		return cachedMap;
	}

	protected Coord randomCoordFromCenter(){
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
	protected Coord randomCoord() {
		double d = rng.nextDouble();
		if (d > 0.1) {
			return randomCoordFromCenter();
		} else {
			MapNode curNode = this.lastMapNode;
			MapNode prevNode = lastMapNode;
			MapNode nextNode = null;
			List<MapNode> neighbors = curNode.getNeighbors();
			Vector<MapNode> n2 = new Vector<MapNode>(neighbors);
			if (n2.size() == 0) { // only option is to go back
				nextNode = prevNode;
			} else { // choose a random node from remaining neighbors
				nextNode = n2.get(rng.nextInt(n2.size()));
			}

			prevNode = curNode;
			curNode = nextNode;
			lastMapNode = curNode;
			this.setCenter(curNode.getLocation());
			return randomCoordFromCenter();
		}
	}

	@Override
	public SemiClusterMovement replicate() {
		// TODO Auto-generated method stub
		return new SemiClusterMovement(this);
	}
}
