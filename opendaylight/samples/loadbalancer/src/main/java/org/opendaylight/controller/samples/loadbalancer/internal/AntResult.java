package org.opendaylight.controller.samples.loadbalancer.internal;

import org.opendaylight.controller.samples.loadbalancer.entities.Client;

import java.util.List;

import org.opendaylight.controller.sal.core.Edge;

public class AntResult {
	
	String serverIP;
	Client src;
	List<Edge> path;
	
	AntResult(String serverIP, Client src, List<Edge> path) {
		this.serverIP = serverIP;
		this.src = src;
		this.path = path;
	}

}
