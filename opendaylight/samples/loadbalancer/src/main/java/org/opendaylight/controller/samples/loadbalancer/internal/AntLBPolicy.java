package org.opendaylight.controller.samples.loadbalancer.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.hosttracker.hostAware.HostNodeConnector;
import org.opendaylight.controller.sal.core.Edge;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.samples.loadbalancer.ConfigManager;
import org.opendaylight.controller.samples.loadbalancer.entities.Client;
import org.opendaylight.controller.samples.loadbalancer.entities.PoolMember;
import org.opendaylight.controller.samples.loadbalancer.entities.VIP;
import org.opendaylight.controller.samples.loadbalancer.policies.ILoadBalancingPolicy;
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

	@SuppressWarnings("unused")
	private AntLBPolicy(){}

	public AntLBPolicy(ConfigManager cmgr){
		this.cmgr = cmgr;
		this.clientMemberMap = new HashMap<Client, PoolMember>();
	}


	public void initialize(ITopologyManager topo, IfIptoHost hostTracker, ISwitchManager switchManager) {
		if(!initialized) {
			synchronized(AntLBPolicy.class){
				if(!initialized) {
					this.topo = topo;
					this.hostTracker = hostTracker;
					this.switchManager = switchManager;
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
		HashMap<String, IP> serverObj = new HashMap<String, IP>();
		Node srcNode = null;
		for(HostNodeConnector hc : allHosts) {
			if(hc.getNetworkAddressAsString().equals("10.0.0.1")) {
				srcNode = hc.getnodeconnectorNode();
				antLogger.info(srcNode + " " + hc.getNetworkAddressAsString());
				allHosts.remove(hc);
			}
		}
		antLogger.info("testingg");
		antLogger.info("No of servers: " + allHosts.size());
		for(HostNodeConnector hc : allHosts) {
			Node dstNode = hc.getnodeconnectorNode();
			antLogger.info(hc.getNetworkAddressAsString());
			IP obj = new IP(hc.getNetworkAddressAsString());
			Set<Node> allNodes = new HashSet<Node>();
			antLogger.info("Get allpaths called");
			Set<List<Edge>> paths= getAllPaths(srcNode, dstNode, topo.getEdges().keySet(), allNodes);
			int pathID = 1;
			float initialPheromoneValue = (float) 0.1;
			for (List<Edge> path : paths) {
				obj.pheromoneMatrix.put(pathID++, new PathList(path,initialPheromoneValue)); 
			}
			serverObj.put(obj.IPaddress, obj);
		}

		Iterator<String> i = serverObj.keySet().iterator();
		antLogger.info("Number of server: " + serverObj.size());
		while(i.hasNext()) {
			IP obj = serverObj.get(i.next());
			Map<Integer, PathList> pheromoneMatrix = obj.pheromoneMatrix;
			Iterator<Integer> i1 = pheromoneMatrix.keySet().iterator();
			while(i1.hasNext()) {
				antLogger.info(pheromoneMatrix.get(i1).path.toString());
			}
		}
		/*String ipOfServerSelected = "10.0.0.3";
		IP main = serverObj.get(ipOfServerSelected);
		float min = 999;
		for(Integer i : main.pheromoneMatrix.keySet()) {
			PathList obj = main.pheromoneMatrix.get(i);
			List<Edge> path = obj.path;
			float pathUtil = (float) 10.0;
			//pathUtil= getUtilization(path);	
			if(pathUtil < min) {
				min = pathUtil;
			}
		}*/
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

			if (nextNode == dstNode) {
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

		antLogger.trace("Received traffic from client : {} for VIP : {} ",source, dest);

		syncWithLoadBalancerData();

		PoolMember pm= null;

		if(this.clientMemberMap.containsKey(source)){
			pm= this.clientMemberMap.get(source);
			antLogger.trace("Client {} had sent traffic before,new traffic will be routed to the same pool member {}",source,pm);
		}else{
			/* Pool pool = null;
                pool = this.cmgr.getPool(dest.getPoolName());
                int memberNum = this.randomGenerator.nextInt(pool.getAllMembers().size()-1);
                pm = pool.getAllMembers().get(memberNum);
                this.clientMemberMap.put(source, pm );
                antLogger.trace("Network traffic from client {} will be directed to pool member {}",pm); */
		}
		return pm.getIp();
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
