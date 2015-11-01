package org.opendaylight.controller.samples.loadbalancer.internal;


import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.controller.sal.core.Edge;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.core.Property;
import org.opendaylight.controller.sal.reader.NodeConnectorStatistics;
import org.opendaylight.controller.sal.utils.ServiceHelper;
import org.opendaylight.controller.statisticsmanager.IStatisticsManager;
import org.opendaylight.controller.topologymanager.ITopologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkUtilization implements Runnable {

    protected static Logger Linklog = LoggerFactory.getLogger(LinkUtilization.class);
    /** Last timestamp of Executor run */
    protected long lastDataRateTimestamp = System.currentTimeMillis();
    /** The interval for the Executor, in TimeUnit.SECONDS */
    protected final int DATARATE_CALCULATOR_INTERVAL = 5;
    /** The map that maintains up to date link data rate */
    protected static ConcurrentHashMap<Edge, Double> linkDataRate = new ConcurrentHashMap<Edge, Double>();
    /** The map that maintains up to date link Bytes transferred data */
    protected static ConcurrentHashMap<Edge, Long> linkBytesTransferred = new ConcurrentHashMap<Edge, Long>();

   public void init() {
        linkDataRate = new ConcurrentHashMap<Edge, Double>();
        linkBytesTransferred = new ConcurrentHashMap<Edge, Long>();
        Linklog.info("initialized");
    }
    
    @Override
    public void run() {
    	Linklog.info("running now");
        long thisDataRateTimestamp = System.currentTimeMillis();

        try {

            //log.info("CalculateDataRates running");

            ITopologyManager topologyManager = (ITopologyManager) ServiceHelper
                    .getGlobalInstance(ITopologyManager.class, this);

            IStatisticsManager statisticsManager = (IStatisticsManager) ServiceHelper
                    .getGlobalInstance(IStatisticsManager.class, this);

            if(topologyManager == null || statisticsManager == null) {
                Linklog.info("CalculateDataRates: topology or statistics Manager is null!");
                return;
            }
            if(linkDataRate == null || linkBytesTransferred == null) {
                Linklog.info("CalculateDataRates: linkDataRate or linkBytesTransferred maps are null");
                return;
            }
            Map<Edge, Set<Property>> edgeTopology = topologyManager.getEdges();

            // Elapsed time in seconds
            double elapsedTime = 0.001 * (double) (thisDataRateTimestamp - lastDataRateTimestamp);
            Linklog.info("time in sec : " + elapsedTime);
            Set<Edge> currentEdges = edgeTopology.keySet();
            Linklog.info("No of edges " + currentEdges.size());
            for (Edge edge : currentEdges) {
                //log.info("Data rate calculator for edge {}", edge.toString());
                // For this edge, find the nodeconnector of the tail (the source
                // of the traffic)
                NodeConnector tailNodeConnector = edge.getTailNodeConnector();
                // Get the statistics for this NodeConnector
                NodeConnectorStatistics ncStats = statisticsManager.getNodeConnectorStatistics(tailNodeConnector);
                if(ncStats == null) {
                	Linklog.info("stats null");
                	continue;
                }
                // long receiveBytes = ncStats.getReceiveByteCount();
                long transmitBytes = ncStats.getTransmitByteCount();
                Linklog.info("transmit bytes : " + transmitBytes );
                long totalBytes = transmitBytes;

                double dataRate = 0;
                if (linkBytesTransferred.containsKey(edge)
                        && linkDataRate.containsKey(edge)) {
                    // Already have a measurement for this edge
                    dataRate = (totalBytes - linkBytesTransferred.get(edge))
                            / elapsedTime;
                    Linklog.info("Data rate : " + dataRate );
                }
                linkBytesTransferred.put(edge, totalBytes);
                linkDataRate.put(edge, dataRate);
            }
            Linklog.info("completed");
            
        } catch (Exception e) {
        	e.printStackTrace();
        	//throw new RuntimeException(e);
            Linklog.info("CalculateDataRates exception {}", e);
        }

        lastDataRateTimestamp = thisDataRateTimestamp;
        
    }

    public static Map<Edge,Double> getEdgeDataRates() {
        return linkDataRate;
    }
}

