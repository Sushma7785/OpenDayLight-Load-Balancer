package org.opendaylight.controller.samples.loadbalancer.internal;

import java.util.List;

import org.opendaylight.controller.sal.core.Edge;

public class PathList {
	
	List<Edge> path;
	float pheromoneValue;
	
	PathList(List<Edge> path, float pheromoneValue) {
		this.path = path;
		this.pheromoneValue = pheromoneValue;
	}

}
