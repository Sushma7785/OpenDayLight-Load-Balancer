package org.opendaylight.controller.samples.loadbalancer.northbound;

import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.opendaylight.controller.samples.loadbalancer.entities.Server;
import org.opendaylight.controller.samples.loadbalancer.entities.VIP;

/**
 * JAX-RS resource for handling details of all the available Servers
 * in response to respective REST API requests.
 */

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)

public class Servers {
	
	@XmlElement(name ="server")
	Set<Server> LBServers;
	
	public Servers() {}
	
	public Servers(Set<Server> LBservers) {
		this.LBServers = LBservers;
	}
	
	/**
     * @return the loadBalancerServers
     */
    public Set<Server> getLoadBalancerServers() {
        return LBServers;
    }

    /**
     * @param loadBalancerVIPs the loadBalancerServers to set
     */

    public void setLoadBalancerVIPs(Set<Server> LBServers) {
        this.LBServers = LBServers;
    }
	
	

}
