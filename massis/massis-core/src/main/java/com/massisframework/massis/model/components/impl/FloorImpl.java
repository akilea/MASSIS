package com.massisframework.massis.model.components.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.inject.Inject;
import com.massisframework.massis.model.components.Floor;
import com.massisframework.massis.model.components.FloorReference;
import com.massisframework.massis.sim.FilterParams;
import com.massisframework.massis.sim.SimulationScheduler;
import com.massisframework.massis.sim.SimulationSteppable;
import com.massisframework.massis.sim.ecs.ComponentFilter;
import com.massisframework.massis.sim.ecs.SimulationEngine;
import com.massisframework.massis.sim.ecs.SimulationEntity;
import com.massisframework.massis.sim.ecs.injection.components.EntityReference;

public class FloorImpl implements Floor, SimulationSteppable {

	@Inject
	private SimulationEngine engine;
	@FilterParams(all = { FloorReference.class })
	private ComponentFilter referenceFilter;

	@EntityReference
	SimulationEntity entity;

	private List<SimulationEntity> entitiesIn = new ArrayList<>();
	/*
	 * Bounds
	 */
	public int minX, maxX, minY, maxY, xlength, ylength;

	public void setEntitiesIn(Collection<SimulationEntity> entities)
	{
		this.entitiesIn.clear();
		this.entitiesIn.addAll(entities);
	}

	@Override
	public Iterable<SimulationEntity> getEntitiesIn()
	{
		// return this.entitiesIn;
		return this.entitiesIn;
	}

	public int getMinX()
	{
		return minX;
	}

	public void setMinX(int minX)
	{
		this.minX = minX;
	}

	public int getMaxX()
	{
		return maxX;
	}

	public void setMaxX(int maxX)
	{
		this.maxX = maxX;
	}

	public int getMinY()
	{
		return minY;
	}

	public void setMinY(int minY)
	{
		this.minY = minY;
	}

	public int getMaxY()
	{
		return maxY;
	}

	public void setMaxY(int maxY)
	{
		this.maxY = maxY;
	}

	public int getXlength()
	{
		return xlength;
	}

	public void setXlength(int xlength)
	{
		this.xlength = xlength;
	}

	public int getYlength()
	{
		return ylength;
	}

	public void setYlength(int ylength)
	{
		this.ylength = ylength;
	}

	@Override
	public void step(SimulationScheduler scheduler, float deltaTime)
	{
		this.engine.getEntitiesFor(this.referenceFilter, this.entitiesIn);
		this.entitiesIn.removeIf(c -> c.get(FloorReference.class)
				.getFloorId() != this.entity.getId());
	}

}
