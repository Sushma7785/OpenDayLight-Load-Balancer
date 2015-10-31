package org.opendaylight.controller.samples.loadbalancer.internal;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.controller.sal.core.Edge;



public class GlobalUpdate implements Runnable{
	ConcurrentHashMap<String, IP> serverObj = new ConcurrentHashMap<String, IP>();
	/*
	 * Global evap rate
	 */
	final float globalEvap = (float) 0.5;
	/*
	 * Value of Q
	 */
	final int Q = 1;
	
	GlobalUpdate(ConcurrentHashMap<String, IP> serverObj) {
		this.serverObj = serverObj;
	}
	

	@Override
	public void run() {
		
		Iterator<String> iter = serverObj.keySet().iterator();
		while(iter.hasNext()) {
			IP obj = serverObj.get(iter.next());
			Iterator<Integer> iter2 = obj.pheromoneMatrix.keySet().iterator();
			int minHop = 100;
			int minPathID = 1;
			while(iter2.hasNext()) {
				int pathID = iter2.next();
				List<Edge> path = obj.pheromoneMatrix.get(pathID).path;
				if(path.size() < minHop) {
					minHop = path.size();
					minPathID = pathID;
				}
			}
			
			performUpdate(obj, minPathID, minHop);
		}	
	}


	private void performUpdate(IP obj, int minPathID, int minHop) {
		float updateVal = ((1 - globalEvap) * obj.pheromoneMatrix.get(minPathID).pheromoneValue) + (globalEvap * (Q/minHop));
		obj.pheromoneMatrix.get(minPathID).pheromoneValue = updateVal;
	}

}
