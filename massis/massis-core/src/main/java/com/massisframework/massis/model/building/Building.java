package com.massisframework.massis.model.building;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Selectable;
import com.massisframework.massis.displays.SimulationDisplay;
import com.massisframework.massis.model.agents.HighLevelController;
import com.massisframework.massis.model.location.Location;
import com.massisframework.massis.model.managers.AnimationManager;
import com.massisframework.massis.model.managers.EnvironmentManager;
import com.massisframework.massis.model.managers.movement.MovementManager;
import com.massisframework.massis.model.managers.pathfinding.PathFindingManager;

public interface Building {

	/**
	 * Links a simulationObject with its corresponding sweethome3d furniture
	 * element
	 *
	 * @param simulationObject
	 *            the simulation object to be linked
	 * @param representation
	 *            the furniture element of sweethome3d that represents it
	 */
	void addSH3DRepresentation(SimulationObject simulationObject,
			HomePieceOfFurniture representation);

	/**
	 * Adds a teleport element to the building
	 *
	 * @param teleport
	 *            the teleport element
	 */
	void addTeleport(Teleport teleport);

	HomePieceOfFurniture getSH3DRepresentation(SimulationObject obj);

	List<Floor> getFloors();

	Home getHome();

	HashMap<Level, Floor> getLevelsFloors();

	Floor getFloorOf(Level lvl);

	Floor getFloorById(int floorId);

	/**
	 * Only used for recovering the state. <i>Do not use</i>
	 *
	 * @param simObjId
	 * @return
	 */
	SimulationObject getSimulationObject(int simObjId);

	ISimRoom getRandomRoom();

	AnimationManager getAnimationManager();

	EnvironmentManager getEnvironmentManager();

	Location getNamedLocation(String name);

	void addNamedLocation(String name, Location location);

	MovementManager getMovementManager();

	void addNamedRoom(String name, ISimRoom simRoom);

	void registerDisplays(SimulationDisplay... displays);

	String getResourcesFolder();

	Map<String, String> getMetadata(Selectable f);

	Collection<HighLevelController> getScheduledControllers();

	PathFindingManager getPathManager();

	public void addToSchedule(HighLevelController hlc);
}