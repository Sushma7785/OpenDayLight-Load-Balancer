package org.opendaylight.controller.samples.loadbalancer.entities;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="server")
@XmlAccessorType(XmlAccessType.NONE)
public class Server {
	
	/*
	 * Server IP
	 */
	@XmlElement
	private String ip;
	
	/*
	 * CPU usage
	 */
	@XmlElement
	private String usage;
	
	@SuppressWarnings("unused")
    private Server() {}
	
	public Server(String ip, String usage) {
		this.ip = ip;
		this.usage = usage;
	}
	
	public void setIP(String ip) {
		this.ip = ip;
	}
	
	public String getIP() {
		return ip;
	}
	
	public void setUsage(String usage) {
		this.usage = usage;
	}
	
	public String getUsage() {
		return usage;
	}
	
	@Override
    public int hashCode() {
		final int prime = 31;
        int result = 1;
        result = prime * result + ((ip == null) ? 0 : ip.hashCode());
        result = prime * result+ ((usage == null) ? 0 : usage.hashCode());
        return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Client)) {
            return false;
        }
        Server other = (Server) obj;
        if (ip == null) {
            if (other.ip != null) {
                return false;
            }
        }else if (!ip.equals(other.ip)) {
            return false;
        }
        if (usage == null) {
            if (other.usage != null) {
                return false;
            }
        }else if (!usage.equals(other.usage)) {
            return false;
        }
        return true;
	}
	
	 @Override
	    public String toString() {
		 return this.ip + " " + this.usage;
	 }

}
