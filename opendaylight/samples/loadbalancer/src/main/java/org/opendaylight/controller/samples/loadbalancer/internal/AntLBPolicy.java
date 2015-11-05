package org.opendaylight.controller.samples.loadbalancer.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.internal.stats.StatsManager;
import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.hosttracker.hostAware.HostNodeConnector;
import org.opendaylight.controller.sal.core.Edge;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.Path;
import org.opendaylight.controller.sal.utils.ServiceHelper;
import org.opendaylight.controller.samples.loadbalancer.ConfigManager;
import org.opendaylight.controller.samples.loadbalancer.entities.Client;
import org.opendaylight.controller.samples.loadbalancer.entities.PoolMember;
import org.opendaylight.controller.samples.loadbalancer.entities.VIP;
import org.opendaylight.controller.samples.loadbalancer.policies.ILoadBalancingPolicy;
import org.opendaylight.controller.statisticsmanager.IStatisticsManager;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.topologymanager.ITopologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;


//This class implements Ant Colony System Algorithm for load balancing

public class AntLBPolicy implements ILoadBalancingPolicy {
	/*
	 * Instance logger
	 */
	private static final Logger antLogger = LoggerFactory.getLogger(AntLBPolicy.class);

	/*
	 * Reference to the configuration manager. This reference is passed from load balancer
	 * class.
	 */
	private ConfigManager cmgr;

	/*
	 * Mapping between the client and the pool member that serves all traffic for that client.
	 */
	private HashMap<Client, PoolMember> clientMemberMap;

	/*
	 *  network graph
	 */
	private Graph<Node, Edge> networkGraph = new DirectedSparseGraph<Node, Edge>();

	/*
	 * Reference to the host tracker service
	 */
	private IfIptoHost hostTracker;

	/*
	 * Reference for topology service
	 */
	private ITopologyManager topo;

	/*
	 * Reference for Switch service
	 */
	private ISwitchManager switchManager;
	
	/*
	 * Boolean value to store initialization 
	 */
	private static boolean initialized = false;
	/*
	 * Schedule executor for calculating data rates
	 */
	private static ScheduledExecutorService dataRateCalculator;
	/*
	 * Reference for Link Utilization class
	 */
	private LinkUtilization linkUtilCalculate;
	/*
	 * List of server objects
	 */
	private static ConcurrentHashMap<String, IP> serverObj = new ConcurrentHashMap<String, IP>();
	/*
	 * Local evaporation rate
	 */
	final float localEvap = (float) 0.1;
	/*
	 * Initial Local update value
	 */
	final float localInit = (float) 0.1;
	/*
	 * value of alpha
	 */
	final float alpha = (float) 0.5;
	/*
	 * value of beta
	 */
	final float beta = (float) 0.5;
	/*
	 * Number of iterations for Global Update
	 */
	static int i = 0; 
	/*
	 * Bandwidth in bytes/sec
	 */
	final static long bandwidth = 100000000000L;
	/*
	 * Initial pheromone value
	 */
	static float initialPheromoneValue ;

	@SuppressWarnings("unused")
	private AntLBPolicy(){}

	public AntLBPolicy(ConfigManager cmgr){
		this.cmgr = cmgr;
	}


	public boolean initialize(ITopologyManager topo, IfIptoHost hostTracker, ISwitchManager switchManager) {
		if(!initialized) {
			synchronized(AntLBPolicy.class){
				if(!initialized) {
					this.topo = topo;
					this.hostTracker = hostTracker;
					this.switchManager = switchManager;
					initialPheromoneValue = 1;
					antLogger.info("initial value" + initialPheromoneValue);
					dataRateCalculator = Executors.newSingleThreadScheduledExecutor();
					linkUtilCalculate = new LinkUtilization();
					linkUtilCalculate.init();
					dataRateCalculator.scheduleAtFixedRate(linkUtilCalculate, 0, linkUtilCalculate.DATARATE_CALCULATOR_INTERVAL,TimeUnit.SECONDS);
					initialized = true;
					antLogger.info("N/w topology created: " + createTopoGraph());
					initMatrix(this.topo, this.hostTracker);
		     	}
			}
		}
		
		return true;
	}

	public boolean createTopoGraph() {
		Set<Edge> edgeSet = this.topo.getEdges().keySet();
		for(Edge e : edgeSet) {
			Node src = e.getTailNodeConnector().getNode();
			Node dst = e.getHeadNodeConnector().getNode();
			networkGraph.addVertex(src);
			networkGraph.addVertex(dst);
			networkGraph.addEdge(e, src, dst);
		}
		return true;
	}
	
	private void initMatrix(ITopologyManager topo, IfIptoHost hostTracker) {
		Set<HostNodeConnector> allHosts = hostTracker.getAllHosts();
		HostNodeConnector srcHost = null;
		Node srcNode = null;
		HostNodeConnector toRemove = null;
		IStatisticsManager statManager = (IStatisticsManager) ServiceHelper
                 .getGlobalInstance(IStatisticsManager.class, this);
		for(HostNodeConnector hc : allHosts) {
			if(hc.getNetworkAddressAsString().equals("10.0.0.1")) {
				srcHost = hc;
				srcNode = hc.getnodeConnector().getNode();
				antLogger.info("checking stats : " + statManager.getNodeConnectorStatistics(hc.getnodeConnector()).getTransmitByteCount());
				toRemove = hc;				
			}
		}
		allHosts.remove(toRemove);
		for(HostNodeConnector hc : allHosts) {
			Node dstNode = hc.getnodeconnectorNode();
			IP obj = new IP(hc.getNetworkAddressAsString());
			Set<Node> allNodes = new HashSet<Node>();
			Set<List<Edge>> paths = getAllPaths(srcNode, dstNode, topo.getEdges().keySet(), allNodes);
			int pathID = 1;
			for (List<Edge> path : paths) {
				obj.pheromoneMatrix.put(pathID++, new PathObject(path,initialPheromoneValue)); 
			}
			serverObj.put(obj.IPaddress, obj);
		}
		
		Iterator<String> i = serverObj.keySet().iterator();
		antLogger.info("Number of server: " + serverObj.size());
		while(i.hasNext()) {
			IP obj = serverObj.get(i.next());
			antLogger.info(obj.IPaddress);
			Map<Integer, PathObject> pheromoneMatrix = obj.pheromoneMatrix;
			Iterator<Integer> i1 = pheromoneMatrix.keySet().iterator();
			while(i1.hasNext()) {
				int pathno = i1.next() ;
				antLogger.info( pathno+ " " + pheromoneMatrix.get(pathno).path.toString());
			}
		} 
	}

	private Set<List<Edge>> getAllPaths(Node srcNode, Node dstNode, Set<Edge> allLinks, Set<Node> allNodes) {
		// The new paths from srcNode to dstNode.
		Set<List<Edge>> allPaths = new HashSet<List<Edge>>();
		// Clone the available links such that we can modify them.
		Set<Edge> availableLinks = new HashSet<Edge>();
		availableLinks.addAll(allLinks);
		// Clone the visited nodes such that we can modify them.
		Set<Node> visitedNodes = new HashSet<Node>();
		visitedNodes.addAll(allNodes);

		//Just to make sure the first node is in the visited nodes list.
		if (!visitedNodes.contains(srcNode)) {
			allNodes.add(srcNode);
			visitedNodes.add(srcNode);
		}

		// For all links that originate at the source node.
		for (Edge link : networkGraph.getOutEdges(srcNode)) {
			List<Edge> currentPath = new ArrayList<Edge>();
			
			if (!availableLinks.contains(link))
				continue;

			Node nextNode = link.getHeadNodeConnector().getNode();

			if(visitedNodes.contains(nextNode)) {
				continue;
			}

			if (nextNode.equals(dstNode) ) {
				currentPath.add(link);
				allPaths.add(currentPath);
			} else {
				availableLinks.remove(link);
				visitedNodes.add(nextNode);
				Set<List<Edge>> nextPaths = getAllPaths(nextNode,
						dstNode, availableLinks, visitedNodes);

				for (List<Edge> path : nextPaths) {
					if (path.isEmpty())
						continue;
					currentPath.add(link);
					currentPath.addAll(path);
					allPaths.add(new ArrayList<Edge>(currentPath));
					currentPath.clear();
				}
			}
		}

		return allPaths;
	}

	
	@Override
	public String getPoolMemberForClient(Client source, VIP dest){
		syncWithLoadBalancerData();
		return null;
	}
	
	public AntResult getFinalPath(Client source) {
		
		antLogger.info("Received traffic from client : called getFinalPath ");
		String serverIP = "10.0.0.8";
		IP obj = serverObj.get(serverIP);
		antLogger.info(obj.toString());
		List<Edge> bestPath = getBestPath(obj);
		
		//syncWithLoadBalancerData();
		AntResult resultObj = new AntResult(serverIP, source, bestPath);
		return resultObj;	
	}
	
	public List<Edge> getBestPath(IP obj) {
		
		Iterator<Integer> iter = obj.pheromoneMatrix.keySet().iterator();
		int maxPathID = 0;
		float max = 0;
		Map<Edge,Double> dataRates = LinkUtilization.getEdgeDataRates();
		Iterator<Edge> i = dataRates.keySet().iterator();
		while(i.hasNext()) {
			Edge e = i.next();
			antLogger.info("edge :" + e + " data rate: " + dataRates.get(e) );
		}
		while(iter.hasNext()) {
			int pathID = iter.next();
			antLogger.info("path ID : " + pathID);
			PathObject path = obj.pheromoneMatrix.get(pathID);
			long pathAvailableBandwidth = Long.MAX_VALUE;
			List<Edge> edges = path.path;
			antLogger.info("path : " + edges);
			for(Edge edge : edges) {
				 long linkBitRate = 0;
				 antLogger.info("edge : " + edge);
				 if(dataRates.containsKey(edge)) {
					 antLogger.info("yess edge is there");
				 }
				 linkBitRate = dataRates.get(edge).longValue() * 8;
				 antLogger.info("link bit rate : " + linkBitRate);
				 long availableBandwidth = bandwidth - linkBitRate;
                 if (availableBandwidth < 0)
                     availableBandwidth = 0;

                 if (availableBandwidth < pathAvailableBandwidth) {
                	 antLogger.info("path avail bandwidth : " + pathAvailableBandwidth);
                     pathAvailableBandwidth = availableBandwidth;
                 }

			}
			float comparVal = (pathAvailableBandwidth * obj.pheromoneMatrix.get(pathID).pheromoneValue);
			antLogger.info("comparVal : " + comparVal);
			if( comparVal > max ) {
				max = comparVal;
				antLogger.info("max val : " + max);
				maxPathID = pathID;
			}
		}
		
		boolean done = doLocalUpdate(obj, maxPathID);
		antLogger.info("local update completed : " + done);
		return obj.pheromoneMatrix.get(maxPathID).path;
		
	}
	
	public boolean doLocalUpdate(IP obj, int maxPathID) {
		float updateVal = ((1 - localEvap) * obj.pheromoneMatrix.get(maxPathID).pheromoneValue) + localEvap * localInit;
		antLogger.info("local updated values : " + updateVal);
		obj.pheromoneMatrix.get(maxPathID).pheromoneValue = updateVal;
		i++;
		if(i >= 20) {
			(new Thread(new GlobalUpdate(serverObj))).start();
			i = 0;
		}
		return true;
	}

	/*
	 * This method does the clean up. Whenever a new client packet arrives with a given VIP,
	 * this method checks the current configuration to see if any pool members have been deleted and
	 * cleans up the metadata stored by this loadbalancing algorithm.
	 */
	private void syncWithLoadBalancerData(){
		antLogger.debug("[Client - PoolMember] table before cleanup : {}",this.clientMemberMap.toString());

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

			antLogger.debug("Removed client : {} ",client);
		}
		antLogger.debug("[Client - PoolMember] table after cleanup : {}",this.clientMemberMap.toString());
	}

}
