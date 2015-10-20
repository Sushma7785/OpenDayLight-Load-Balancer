package org.opendaylight.controller.samples.loadbalancer.internal;

import java.util.HashMap;
import java.util.Map;

public class IP {
	
	String IPaddress;
	Map<Integer, PathList> pheromoneMatrix = new HashMap<Integer, PathList>(); 
	
	IP (String IPaddress) {
		this.IPaddress = IPaddress;
		}

}
