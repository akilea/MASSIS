package com.massisframework.massis.ecs.components;

import com.artemis.Component;

public class Rotation extends Component {
	/**
	 * @treatAsPrivate
	 */
	public float angle;

	public void setAngle(float angle)
	{
		this.angle = angle;
	}
}
