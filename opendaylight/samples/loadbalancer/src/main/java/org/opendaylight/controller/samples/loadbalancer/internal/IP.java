package org.opendaylight.controller.samples.loadbalancer.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IP {
	
	String IPaddress;
	ConcurrentHashMap<Integer, PathObject> pheromoneMatrix = new ConcurrentHashMap<Integer, PathObject>(); 
	
	IP (String IPaddress) {
		this.IPaddress = IPaddress;
		}

}
