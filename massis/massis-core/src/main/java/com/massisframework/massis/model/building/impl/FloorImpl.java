/**
 *
 */
package com.massisframework.massis.model.building.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.massisframework.massis.model.agents.DefaultAgent;
import com.massisframework.massis.model.agents.HighLevelController;
import com.massisframework.massis.model.agents.LowLevelAgent;
import com.massisframework.massis.model.building.Building;
import com.massisframework.massis.model.building.Floor;
import com.massisframework.massis.model.building.RoomConnector;
import com.massisframework.massis.model.building.SimDoor;
import com.massisframework.massis.model.building.SimRoom;
import com.massisframework.massis.model.building.SimWall;
import com.massisframework.massis.model.building.Teleport;
import com.massisframework.massis.model.components.DoorComponent;
import com.massisframework.massis.model.components.FloorReference;
import com.massisframework.massis.model.components.Metadata;
import com.massisframework.massis.model.components.Position2D;
import com.massisframework.massis.model.components.RoomComponent;
import com.massisframework.massis.model.components.ShapeComponent;
import com.massisframework.massis.model.components.SteeringComponent;
import com.massisframework.massis.model.components.TeleportComponent;
import com.massisframework.massis.model.components.Velocity;
import com.massisframework.massis.model.components.VisionArea;
import com.massisframework.massis.model.components.WindowComponent;
import com.massisframework.massis.model.components.impl.MetadataComponentImpl;
import com.massisframework.massis.pathfinding.straightedge.FindPathResult;
import com.massisframework.massis.pathfinding.straightedge.SEPathFinder;
import com.massisframework.massis.sim.ecs.SimulationEngine;
import com.massisframework.massis.sim.ecs.SimulationEntity;
import com.massisframework.massis.util.Indexable;
import com.massisframework.massis.util.SH3DUtils;
import com.massisframework.massis.util.SimObjectProperty;
import com.massisframework.massis.util.field.grid.quadtree.array.ArrayQuadTree;
import com.massisframework.massis.util.field.grid.quadtree.array.ArrayQuadTreeCallback;
import com.massisframework.massis.util.geom.ContainmentPolygon;
import com.massisframework.massis.util.geom.CoordinateHolder;
import com.massisframework.massis.util.geom.KPolygonUtils;
import com.massisframework.massis.util.io.Restorable;

import straightedge.geom.KPoint;
import straightedge.geom.KPolygon;
import straightedge.geom.PolygonBufferer;
import straightedge.geom.path.PathBlockingObstacleImpl;

/**
 * Represents a level/Floor in the building
 *
 * @author rpax
 *
 */
public class FloorImpl implements Floor {

	/**
	 * The ID of this floor. Useful for hashcodes.
	 */
	private final int id;
	// UID "generator"
	private static final AtomicInteger CURRENT_FLOOR_MAX_ID = new AtomicInteger(
			0);

	private static int getNewUID()
	{
		return CURRENT_FLOOR_MAX_ID.getAndIncrement();
	}

	/*
	 * SH3D objects
	 */
	private final com.eteks.sweethome3d.model.Level level3D;
	private final ArrayList<Room> rooms3D;
	private final ArrayList<Wall> walls3D;
	private final ArrayList<HomePieceOfFurniture> furniture3D;
	private final Building building;
	/*
	 * Bounds
	 */
	public final int minX, maxX, minY, maxY, xlength, ylength;

	/*
	 * Rooms & doors
	 */
	private final List<SimDoor> doors = new ArrayList<>();
	private final ArrayList<RoomConnector> roomConnectors = new ArrayList<>();
	/**
	 * Teleports linking to other floors
	 */
	private final HashMap<Floor, List<Teleport>> teleportConnectingFloors = new HashMap<>();
	/**
	 * MASSIS Walls
	 */
	private final ArrayList<SimulationEntity> walls;
	/**
	 * MASSIS Rooms
	 */
	private final ArrayList<SimRoom> rooms;
	/**
	 * Polygons for using the containment behavior
	 */
	private ArrayList<ContainmentPolygon> containmentPolygons;
	/**
	 * Teleports in this Floor
	 */
	private final ArrayList<TeleportImpl> teleports;
	/**
	 * The proper pathfinder
	 */
	private final SEPathFinder pathFinder;
	/**
	 * QuadTree
	 */
	private final ArrayQuadTree<ArrayQuadTreeComponent> quadPilu;
	private SimulationEngine engine;

	/**
	 * Creates a floor with all the elements of a Level
	 *
	 * @param level3D
	 *            the level (1,2,3)
	 * @param rooms3D
	 *            the rooms in that level
	 * @param walls3D
	 *            the walls in that level
	 * @param furniture3D
	 *            the furniture in that level
	 */
	public FloorImpl(com.eteks.sweethome3d.model.Level level3D,
			ArrayList<Room> rooms3D, ArrayList<Wall> walls3D,
			ArrayList<HomePieceOfFurniture> furniture3D, BuildingImpl building)
	{

		this.engine = building.getSimulationEngine();
		this.id = getNewUID();
		this.building = building;
		this.level3D = level3D;

		this.rooms3D = rooms3D;
		this.walls3D = walls3D;
		this.furniture3D = furniture3D;

		/*
		 * Rooms & Walls initialization
		 */

		this.walls = new ArrayList<>(this.walls3D.size());
		this.rooms = new ArrayList<>(this.rooms3D.size());
		this.teleports = new ArrayList<>();
		/**
		 * TODO Shouldn't be in a Logger or similar?
		 *
		 * @formatter:off
		 */
		System.err.println("======================================================");
		System.err.println("Creating floor from level [" + SH3DUtils.getLevelName(this.level3D) + "]");
		final int[] bounds = this.configureBounds();
		this.minX = bounds[0];
		this.minY = bounds[1];
		this.maxX = bounds[2];
		this.maxY = bounds[3];
		this.xlength = this.maxX - this.minX;
		this.ylength = this.maxY - this.minY;
		this.quadPilu = new ArrayQuadTree<>(7, this.minX, this.maxX, this.minY, this.maxY);
		System.err.println("Initializing simulation objects..");
		this.initializeSimObjects();
		System.err.println("# of SimulationObjects: " + this.quadPilu.countElements());
		System.err.println("# of rooms: " + this.getRooms().size());
		System.err.println("======================================================");
		/**
		 * @formatter:on
		 */
		this.pathFinder = new SEPathFinder(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.massisframework.massis.model.building.IFloor#initializePathFinder()
	 */
	@Override
	public void initializePathFinder()
	{
		this.pathFinder.initialize();

	}

	private void initializeSimObjects()
	{
		// First rooms
		initializeRooms();
		// Then walls
		initializeWalls();
		// Once the walls are built, the containment polygons
		// initializeContainmentPolygons();
		// finally the furniture
		initializeSimFurniture();
	}

	/**
	 * Initializes the furniture in this floor. Takes every piece of furniture
	 * in this floor and makes its MASSIS equivalent
	 */
	private void initializeSimFurniture()
	{
		for (final HomePieceOfFurniture f : this.furniture3D)
		{
			/*
			 * Creation of the location of the new element
			 */
			// final SimLocation location = new SimLocation(f, this);

			int entityId = this.engine.createEntity();
			SimulationEntity entity = this.engine.asSimulationEntity(entityId);
			/*
			 * Has metadata?
			 */
			Map<String, String> metadata = getMetadata(f);

			entity.addComponent(Metadata.class).set(metadata);
			entity.addComponent(Position2D.class).set(f.getX(), f.getY());
			entity.addComponent(Metadata.class);
			entity.addComponent(ShapeComponent.class)
					.setShape(SH3DUtils.createKPolygonFromSH3DObj(f));
			entity.addComponent(FloorReference.class).setFloorId(this.id);
			/*
			 * Special case: teleports
			 */
			if (metadata.containsKey(SimObjectProperty.TELEPORT.toString()))
			{

				// entity.addComponent(TeleportComponent.class);
				// final TeleportImpl teleport = new TeleportImpl(metadata,
				// location,
				// this.building.getAnimationManager(),
				// this.building.getEnvironmentManager(),
				// this.building.getPathManager());
				// this.building.addTeleport(teleport);
				// this.teleports.add(teleport);
				// this.roomConnectors.add(teleport);
				// f.setVisible(true);

			} else
			{
				if (metadata.containsKey(
						SimObjectProperty.POINT_OF_INTEREST.toString()))
				{
					// /*
					// * Is it an special place?
					// */
					// this.building.addNamedLocation(
					// metadata.get(SimObjectProperty.NAME.toString()),
					// new Location(f.getX(), f.getY(), this));
				} else
				{
					if (f instanceof HomeDoorOrWindow)
					{
						/*
						 * Windows & doors are the same in SH3D but not in
						 * MASSIS.
						 */
						// Comprobar si es ventana o no
						if (f.getName() != null
								&& f.getName().toUpperCase().contains(
										SimObjectProperty.WINDOW.toString()))
						{
							entity.addComponent(WindowComponent.class);

						} else
						{
							entity.addComponent(DoorComponent.class);
						}
					} else /* Should be an agent then */

					{
						/*
						 * Tries to build an agent, by its metadata.
						 */
						//////////////////////////////

						// final LowLevelAgent person = new
						// DefaultAgent(metadata,
						// location,
						// this.building.getAnimationManager(),
						// this.building.getEnvironmentManager(),
						// this.building.getPathManager());

						entity.addComponent(Velocity.class);
						entity.addComponent(SteeringComponent.class);
						entity.addComponent(VisionArea.class);

						//////////////////////////////
						// final HighLevelController hlc = createHLController(
						// person,
						// metadata, resourcesFolder);
						//
						// this.addPerson(person);

					}
				}
			}

		}
	}

	private Map<String, String> getMetadata(HomeObject f)
	{
		// TODO temporary
		Map<String, String> metadata = new HashMap<>();
		JsonObject[] json = new Gson().fromJson(
				f.getProperty("__MASSIS_METADATA_v1000"), JsonObject[].class);
		if (json != null)
		{

			for (int i = 0; i < json.length; i++)
			{
				json[i].get("attributes").getAsJsonArray().forEach(item -> {
					metadata.put(
							item.getAsJsonObject().get("key").getAsString(),
							item.getAsJsonObject().get("value").getAsString());
				});
			}
		}
		return metadata;
	}

	/**
	 * Wall initialization
	 */
	private void initializeWalls()
	{
		for (final Wall w : this.walls3D)
		{
			// final SimLocation location = new SimLocation(w, this);
			// final Map<String, String> metadata = getMetadata(w);
			// final SimWallImpl simWall = new SimWallImpl(metadata, location,
			//
			// this.building.getAnimationManager(),
			// this.building.getEnvironmentManager(),
			// this.building.getPathManager());
			//
			// this.walls.add(simWall);

			int entityId = engine.createEntity();
			SimulationEntity e = engine.asSimulationEntity(entityId);
			e.addComponent(Metadata.class);
			e.addComponent(Position2D.class);
			e.addComponent(ShapeComponent.class)
					.setShape(SH3DUtils.createKPolygonFromSH3DObj(w));
			this.walls.add(e);
		}

	}

	/**
	 * Room initialization
	 */
	private void initializeRooms()
	{
		for (final Room r : this.rooms3D)
		{
			// final SimLocation location = new SimLocation(r, this);
			//
			// final Map<String, String> metadata = getMetadata(r);
			//
			// final SimRoomImpl simRoom = new SimRoomImpl(metadata, location,
			// this.building.getAnimationManager(),
			// this.building.getEnvironmentManager(),
			// this.building.getPathManager());
			// /*
			// * If has a name, must be added to the named rooms section
			// */
			// if (r.getName() != null)
			// {
			// this.building.addNamedRoom(r.getName(), simRoom);
			// }
			// this.rooms.add(simRoom);

			int entityId = engine.createEntity();
			SimulationEntity e = engine.asSimulationEntity(entityId);
			e.addComponent(Metadata.class);
			e.addComponent(Position2D.class).set(r.getXCenter(),
					r.getYCenter());
			e.addComponent(RoomComponent.class);
			e.addComponent(ShapeComponent.class)
					.setShape(SH3DUtils.createKPolygonFromSH3DObj(r));
			this.walls.add(e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.massisframework.massis.model.building.IFloor#getContainmentPolygons()
	 */
	@Override
	public ArrayList<ContainmentPolygon> getContainmentPolygons()
	{
		return this.containmentPolygons;
	}

	/**
	 * Sets the maximum and minimum bounds
	 */
	private int[] configureBounds()
	{
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (final Room r : this.rooms3D)
		{
			final float[][] points = r.getPoints();
			for (int i = 0; i < points.length; i++)
			{
				minX = (int) Math.min(minX, Math.floor(points[i][0]));
				minY = (int) Math.min(minY, Math.floor(points[i][1]));
				maxX = (int) Math.max(maxX, Math.ceil(points[i][0]));
				maxY = (int) Math.max(maxY, Math.ceil(points[i][1]));
			}
		}
		for (final Wall w : this.walls3D)
		{
			final float[][] points = w.getPoints();
			for (int i = 0; i < points.length; i++)
			{
				minX = (int) Math.min(minX, Math.floor(points[i][0]));
				minY = (int) Math.min(minY, Math.floor(points[i][1]));
				maxX = (int) Math.max(maxX, Math.ceil(points[i][0]));
				maxY = (int) Math.max(maxY, Math.ceil(points[i][1]));
			}
		}
		for (final HomePieceOfFurniture f : this.furniture3D)
		{
			final float[][] points = f.getPoints();
			for (int i = 0; i < points.length; i++)
			{
				minX = (int) Math.min(minX, Math.floor(points[i][0]));
				minY = (int) Math.min(minY, Math.floor(points[i][1]));
				maxX = (int) Math.max(maxX, Math.ceil(points[i][0]));
				maxY = (int) Math.max(maxY, Math.ceil(points[i][1]));
			}
		}
		minX -= 1;
		minY -= 1;
		maxX += 1;
		maxY += 1;
		/*
		 * Prevent zero length bounds
		 */
		if (maxX - minX <= 0)
		{
			minX = 0;
			maxX = 1;
		}
		if (maxY - minY <= 0)
		{
			minY = 0;
			maxY = 1;
		}
		return new int[] { minX, minY, maxX, maxY };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getRandomRoom()
	 */
	@Override
	public CoordinateHolder getRandomRoom()
	{

		final CoordinateHolder room = this.rooms
				.get(ThreadLocalRandom.current().nextInt(this.rooms.size()));
		return room;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getMinX()
	 */
	@Override
	public int getMinX()
	{
		return this.minX;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getMaxX()
	 */
	@Override
	public int getMaxX()
	{
		return this.maxX;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getMinY()
	 */
	@Override
	public int getMinY()
	{
		return this.minY;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getMaxY()
	 */
	@Override
	public int getMaxY()
	{
		return this.maxY;
	}

	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getAgents()
	 */
	@Override
	public Iterable<SimulationEntity> getAgents()
	{
		return toEntities(this.quadPilu.getElementsIn());
	}

	private Iterable<SimulationEntity> toEntities(
			Iterable<ArrayQuadTreeComponent> qts)
	{
		return StreamSupport
				.stream(qts.spliterator(), false)
				.map(c -> this.engine
						.asSimulationEntity(c.getEntityId()))::iterator;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#hashCode()
	 */
	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + this.id;
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.massisframework.massis.model.building.IFloor#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (!(obj instanceof Indexable))
		{
			return false;
		}
		final Indexable other = (Indexable) obj;
		if (this.id != other.getID())
		{
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.massisframework.massis.model.building.IFloor#getStationaryObstacles()
	 */
	@Override
	public Iterable<PathBlockingObstacleImpl> getStationaryObstacles()
	{
		return this.pathFinder.getStationaryObstacles();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getWalkableAreas()
	 */
	@Override
	public Iterable<KPolygon> getWalkableAreas()
	{
		return this.pathFinder.getWalkableAreas();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getName()
	 */
	@Override
	public String getName()
	{
		return SH3DUtils.getLevelName(this.level3D);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getLevel()
	 */
	@Override
	public Level getLevel()
	{
		return this.level3D;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#remove(com.
	 * massisframework.massis.model.building.SimulationObject)
	 */
	@Override
	public void remove(Restorable simObj)
	{
		this.quadPilu.remove(simObj);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#addPerson(com.
	 * massisframework.massis.model.building.SimulationObject)
	 */
	@Override
	public void addPerson(SimulationEntity simObj)
	{
		if (simObj.getComponent(ArrayQuadTreeComponent.class) == null)
		{
			simObj.addComponent(ArrayQuadTreeComponent.class);
		}
		this.quadPilu.insert(simObj.getComponent(ArrayQuadTreeComponent.class));

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#findPath(com.
	 * massisframework.massis.model.location.Location,
	 * com.massisframework.massis.model.location.Location,
	 * com.massisframework.massis.pathfinding.straightedge.FindPathResult)
	 */
	@Override
	public void findPath(final Location fromLoc, Location to,
			FindPathResult callback)
	{
		/*
		 * If the target location has a different floor that the current
		 * location, the path is generated to the nearest teleport.
		 */
		if (fromLoc.getFloor() != to.getFloor())
		{

			final List<Teleport> teleportsConnecting = getTeleportsConnectingFloor(
					to.getFloor());
			if (teleportsConnecting == null)
			{
				logInfo("No teleports connecting {0} with {1} ",
						new Object[] { fromLoc, to });
				callback.onError(
						FindPathResult.PathFinderErrorReason.UNREACHABLE_TARGET);
				// return null;
			}
			final Teleport targetTeleport = teleportsConnecting.get(0);
			final Location targetLocation = targetTeleport.getLocation();
			this.pathFinder.findPath(fromLoc, targetLocation, targetTeleport,
					callback);

		} else
		{
			this.pathFinder.findPath(fromLoc, to, null, callback);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#
	 * getTeleportsConnectingFloor(com.massisframework.massis.model.building.
	 * Floor)
	 */
	@Override
	public List<Teleport> getTeleportsConnectingFloor(final Floor other)
	{

		if (!this.teleportConnectingFloors.containsKey(other))
		{
			final ArrayList<Teleport> teleportsConnecting = new ArrayList<>();
			for (final Teleport teleport : this.teleports)
			{
				if (teleport.getType() == Teleport.START && teleport
						.getDistanceToFloor(other) < Integer.MAX_VALUE)
				{
					teleportsConnecting.add(teleport);
				}
			}
			Collections.sort(teleportsConnecting, new Comparator<Teleport>() {
				@Override
				public int compare(Teleport o1, Teleport o2)
				{
					return Integer.compare(o1.getDistanceToFloor(other),
							o2.getDistanceToFloor(other));
				}
			});
			this.teleportConnectingFloors.put(other,
					Collections.unmodifiableList(teleportsConnecting));
		}
		return this.teleportConnectingFloors.get(other);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getTeleports()
	 */
	@Override
	public List<Teleport> getTeleports()
	{
		return Collections.unmodifiableList(this.teleports);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getRoomConnectors()
	 */
	@Override
	public List<RoomConnector> getRoomConnectors()
	{
		return Collections.unmodifiableList(this.roomConnectors);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getID()
	 */
	@Override
	public int getID()
	{
		return this.id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#
	 * getNearestPointOutsideOfObstacles(double, double)
	 */
	@Override
	public KPoint getNearestPointOutsideOfObstacles(double x, double y)
	{
		return this.pathFinder
				.getNearestPointOutsideOfObstacles(new KPoint(x, y));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#
	 * getNearestPointOutsideOfObstacles(straightedge.geom.KPoint)
	 */
	@Override
	public KPoint getNearestPointOutsideOfObstacles(KPoint p)
	{
		return this.pathFinder.getNearestPointOutsideOfObstacles(p);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.massisframework.massis.model.building.IFloor#getQTRectangles()
	 */
	@Override
	public Iterable<KPolygon> getQTRectangles()
	{

		return this.quadPilu.getRectangles();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.massisframework.massis.model.building.IFloor#getAgentsInRange(int,
	 * int, int, int)
	 */
	@Override
	public Iterable<SimulationEntity> getAgentsInRange(int xmin, int ymin,
			int xmax,
			int ymax)
	{

		final SearchRangeCallback rangeCallback = new SearchRangeCallback();
		this.quadPilu.searchInRange(xmin, ymin, xmax, ymax, rangeCallback);
		return toEntities(rangeCallback.agents);

	}

	@SuppressWarnings("unchecked")
	private HighLevelController createHLController(LowLevelAgent agent,
			Map<String, String> metadata, String resourcesFolder)
	{

		String absResFolder = null;
		/*
		 * Avoid relative paths issues
		 */
		if (resourcesFolder != null)
		{
			absResFolder = new File(resourcesFolder).getAbsolutePath();
		}
		final String className = metadata
				.get(SimObjectProperty.CLASSNAME.toString());

		HighLevelController hlc = HighLevelController.getDummyController();
		if (className != null)
		{
			try
			{
				@SuppressWarnings("rawtypes")
				final Class agentClass = Class.forName(className);

				hlc = HighLevelController.newInstance(agentClass, agent,
						metadata, absResFolder);

			} catch (final ClassNotFoundException ex)
			{
				Logger.getLogger(FloorImpl.class.getName())
						.log(java.util.logging.Level.SEVERE, null, ex);
			}

		}
		return hlc;

	}

	/**
	 * Utility class for searching in ranges and returning the results in a list
	 *
	 * @author rpax
	 *
	 */
	private static class SearchRangeCallback
			implements ArrayQuadTreeCallback<ArrayQuadTreeComponent> {

		private final ArrayList<ArrayQuadTreeComponent> agents = new ArrayList<>();

		@Override
		public void query(ArrayQuadTreeComponent element)
		{
			this.agents.add(element);
		}

		@Override
		public boolean shouldStop()
		{
			return false;
		}
	}

	private static void logInfo(String str, Object[] data)
	{

		Logger.getLogger(FloorImpl.class.getName())
				.log(java.util.logging.Level.INFO, str, data);
	}

	public int getXlength()
	{
		return xlength;
	}

	public int getYlength()
	{
		return ylength;
	}
}
