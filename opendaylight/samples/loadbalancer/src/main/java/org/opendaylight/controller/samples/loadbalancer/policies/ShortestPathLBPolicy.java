package org.opendaylight.controller.samples.loadbalancer.policies;

/*
 * Copyright IBM Corporation, 2013.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.controller.samples.loadbalancer.ConfigManager;
import org.opendaylight.controller.samples.loadbalancer.entities.Client;
import org.opendaylight.controller.samples.loadbalancer.entities.Pool;
import org.opendaylight.controller.samples.loadbalancer.entities.PoolMember;
import org.opendaylight.controller.samples.loadbalancer.entities.VIP;
import org.opendaylight.controller.samples.loadbalancer.internal.AntLBPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the round robin load balancing policy.
 *
 */
public class ShortestPathLBPolicy implements ILoadBalancingPolicy{

    /*
     * Logger instance
     */
    private static final Logger spLogger = LoggerFactory.getLogger(ShortestPathLBPolicy.class);

    /*
     * Reference to the configuration manager. This reference is passed from load balancer
     * class.
     */
    private ConfigManager cmgr;

    /*
     * Mapping between the client and the pool member that serves all traffic for that client.
     */
    private HashMap<Client, PoolMember> clientMemberMap;
    
    public static ConcurrentHashMap<String, Integer> serverUsage = new ConcurrentHashMap<String, Integer>();

    /*
     * Maintains the next pool member counter for the VIPs.
     * More than one VIP can be attached to one pool, so each VIP
     * will have its own counter for the next pool member from
     * the same pool.
     */
    private HashMap<VIP,Integer> nextItemFromPool;

    @SuppressWarnings("unused")
    private ShortestPathLBPolicy(){}

    public ShortestPathLBPolicy(ConfigManager cmgr){
        this.cmgr = cmgr;
        this.clientMemberMap = new HashMap<Client, PoolMember>();
        this.nextItemFromPool = new HashMap<VIP, Integer>();
    }

    @Override
    public String getPoolMemberForClient(Client source, VIP dest){

        spLogger.trace("Received traffic from client : {} for VIP : {} ",source, dest);

        syncWithLoadBalancerData();

        PoolMember pm= null;      
        String minServer = getMinLoadServer();        
        spLogger.info("Server Chosen: " + minServer);
        return minServer;
    }
    
    public String getMinLoadServer() {
		int min = 100;
		String minServer = null;
		Iterator<String> iter = serverUsage.keySet().iterator();
		while(iter.hasNext()) {
			String serverKey = iter.next();
			int usage = serverUsage.get(serverKey);
			if(usage < min) {
				min = usage;
				minServer = serverKey;
			}
		}
		
		return minServer;
	}

    /*
     * This method does the clean up. Whenever a new client packet arrives with a given VIP,
     * this method checks the current configuration to see if any pool members have been deleted and
     * cleans up the metadata stored by this loadbalancing algorithm.
     */
    private void syncWithLoadBalancerData(){
        spLogger.debug("[Client - PoolMember] table before cleanup : {}",this.clientMemberMap.toString());
        ArrayList<Client> removeClient = new ArrayList<Client>();

        if(this.clientMemberMap.size() != 0){
            for(Client client : this.clientMemberMap.keySet()){
                if(!this.cmgr.memberExists(this.clientMemberMap.get(client).getName(),
                                            this.clientMemberMap.get(client).getPoolName())){

                    removeClient.add(client);
                }
            }
        }

        for(Client client : removeClient){
            this.clientMemberMap.remove(client);

            spLogger.debug("Removed client : {} ",client);
        }
        spLogger.debug("[Client - PoolMember] table after cleanup : {}",this.clientMemberMap.toString());

        spLogger.debug("[VIP- NextMember] table before cleanup : {}",this.nextItemFromPool.toString());

        ArrayList<VIP> resetVIPPoolMemberCount= new ArrayList<VIP>();

        if(this.nextItemFromPool.size() != 0){

            for(VIP vip:this.nextItemFromPool.keySet()){
                if(this.nextItemFromPool.get(vip).intValue() > this.cmgr.getPool(vip.getPoolName()).getAllMembers().size()-1){

                    resetVIPPoolMemberCount.add(vip);
                }
            }
        }

        for(VIP vip:resetVIPPoolMemberCount){
            spLogger.debug("VIP next pool member counter reset to 0");
            this.nextItemFromPool.put(vip, 0);
        }

        spLogger.debug("[VIP- NextMember] table after cleanup : {}",this.nextItemFromPool.toString());
    }
}

