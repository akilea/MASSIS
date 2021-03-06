package com.massisframework.massis.ecs.system.sweethome3d.loader;

import java.util.ArrayList;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.BaseSystem;
import com.artemis.Entity;
import com.eteks.sweethome3d.model.Elevatable;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;
import com.massisframework.massis.ecs.components.BuildingLocation;
import com.massisframework.massis.ecs.components.DoorOrWindowComponent;
import com.massisframework.massis.ecs.components.DynamicObstacle;
import com.massisframework.massis.ecs.components.FurnitureComponent;
import com.massisframework.massis.ecs.components.PolygonComponent;
import com.massisframework.massis.ecs.components.RoomComponent;
import com.massisframework.massis.ecs.components.Rotation;
import com.massisframework.massis.ecs.components.StaticObstacle;
import com.massisframework.massis.ecs.components.SweetHome3DComponent;
import com.massisframework.massis.ecs.components.SweetHome3DLevelComponent;
import com.massisframework.massis.ecs.components.WallComponent;

import straightedge.geom.KPoint;
import straightedge.geom.KPolygon;

public class SweetHome3DSystem extends BaseSystem {

	// Load home?

	private Home home;
	private Archetype wallArchetype;

	public SweetHome3DSystem(Home home)
	{
		this.home = home;
	}

	@Override
	public void initialize()
	{
		this.wallArchetype = this.createHomeObjectArchetype();
		this.createEntities();
	}

	@Override
	protected void processSystem()
	{

	}

	private Archetype createHomeObjectArchetype()
	{
		return new ArchetypeBuilder()
				.add(BuildingLocation.class)
				.add(Rotation.class)
				.add(SweetHome3DComponent.class)
				.add(SweetHome3DLevelComponent.class)
				// Depends on location and rotation
				.add(PolygonComponent.class)
				.build(this.world);
	}

	private void createEntities()
	{

		this.home.getWalls().forEach(this::createHomeObjectEntity);
		this.home.getFurniture().stream()
				.filter(f -> !(f instanceof HomeLight))
				.forEach(this::createHomeObjectEntity);
		this.home.getRooms().forEach(this::createHomeObjectEntity);
	}

	private <HO extends HomeObject> void createHomeObjectEntity(
			HO ho)
	{
		if (ho instanceof HomeFurnitureGroup)
		{
			for (HomePieceOfFurniture h : ((HomeFurnitureGroup) ho)
					.getAllFurniture())
			{
				createHomeObjectEntity(h);
			}
			return;
		}
		Entity e = this.world.createEntity(this.wallArchetype);
		KPolygon poly = toKPolygon((Selectable)ho);
		KPoint center = poly.getCenter().copy();
		poly.translateTo(0, 0);
		e.getComponent(PolygonComponent.class).set(poly);
		e.getComponent(Rotation.class).setAngle(0);
		e.getComponent(BuildingLocation.class).set(center);
		e.getComponent(SweetHome3DComponent.class).set(ho);
		e.getComponent(SweetHome3DLevelComponent.class).set(((Elevatable)ho).getLevel());

		// TODO better code
		if (ho instanceof Room)
		{
			e.edit().add(new StaticObstacle());
			e.edit().add(new RoomComponent());
		}
		if (ho instanceof Wall)
		{
			e.edit().add(new StaticObstacle());
			e.edit().add(new WallComponent());
		}
		if (ho instanceof HomePieceOfFurniture)
		{
			e.edit().add(new FurnitureComponent());
			boolean isDynamic = "dyn"
					.equals(((HomePieceOfFurniture) ho).getName());
			if (!isDynamic)
			{
				e.edit().add(new StaticObstacle());
			} else
			{
				e.edit().add(new DynamicObstacle());
			}
			if (((HomePieceOfFurniture) ho).isDoorOrWindow())
			{
				e.edit().add(new DoorOrWindowComponent());
			} else if (isDynamic)
			{

			}
		}
		// Check if is Dynamic...etc
	}

	private static KPolygon toKPolygon(Selectable homePieceOfFurniture)
	{
		final float[][] hpofPoints = homePieceOfFurniture.getPoints();
		// generamos el poligono a partir de los puntos del furniture
		final ArrayList<KPoint> points = new ArrayList<>();
		for (final float[] point : hpofPoints)
		{
			points.add(new KPoint(point[0], point[1]));
		}
		return new KPolygon(points);
	}

}
