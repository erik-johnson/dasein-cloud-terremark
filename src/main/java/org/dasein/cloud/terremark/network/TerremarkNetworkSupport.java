package org.dasein.cloud.terremark.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.NetworkInterface;
import org.dasein.cloud.network.Subnet;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.network.VLANState;
import org.dasein.cloud.network.VLANSupport;
import org.dasein.cloud.terremark.EnvironmentsAndComputePools;
import org.dasein.cloud.terremark.Terremark;
import org.dasein.cloud.terremark.TerremarkException;
import org.dasein.cloud.terremark.TerremarkMethod;
import org.dasein.cloud.terremark.TerremarkMethod.HttpMethodName;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TerremarkNetworkSupport  implements VLANSupport {
	
	// API Calls
	public final static String NETWORKS       = "networks";
	public final static String NETWORK_HOSTS  = "networkHosts";

	// Response Tags
	public final static String NETWORKS_TAG      = "Networks";
	public final static String NETWORK_TAG       = "Network";
	public final static String NETWORK_HOST_TAG  = "NetworkHost";
	
	// Types
	public final static String NETWORK_TYPE       = "application/vnd.tmrk.cloud.network";
	public final static String NETWORK_HOST_TYPE  = "application/vnd.tmrk.cloud.networkHost";
	
	static Logger logger = Terremark.getLogger(TerremarkNetworkSupport.class);
	
	private Terremark provider;

	TerremarkNetworkSupport(Terremark provider) { this.provider = provider; }

	@Override
    public boolean allowsNewVlanCreation() throws CloudException, InternalException {
		return false;
	}
    
	@Override
    public boolean allowsNewSubnetCreation() throws CloudException, InternalException{
		return false;
	}

	@Override
    public Subnet createSubnet(String cidr, String inProviderVlanId, String name, String description) throws CloudException, InternalException{
		throw new OperationNotSupportedException("Subnets not supported.");
	}
    
	@Override
    public VLAN createVlan(String cidr, String name, String description, String domainName, String[] dnsServers, String[] ntpServers) throws CloudException, InternalException{
		throw new OperationNotSupportedException("Network provisioning is not supported");
	}
    
	@Override
    public int getMaxVlanCount() throws CloudException, InternalException{
		return 1;
	}
    
	@Override
    public String getProviderTermForNetworkInterface(Locale locale){
		return "network interface";
	}
    
	@Override
    public String getProviderTermForSubnet(Locale locale){
		return "";
	}
    
	@Override
    public String getProviderTermForVlan(Locale locale){
		return "network";
	}
    
	@Override
    public @Nullable Subnet getSubnet(@Nonnull String subnetId) throws CloudException, InternalException{
		return null;
	}
    
	@Override
    public @Nullable VLAN getVlan(@Nonnull String vlanId) throws CloudException, InternalException{
		VLAN network = null;
		String url = "/" + NETWORKS + "/" + vlanId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = null;
		try {
			doc = method.invoke();
		} catch (TerremarkException e) {
			logger.warn("Failed to get network " + vlanId);
		}
		catch (CloudException e) {
			logger.warn("Failed to get network " + vlanId);
		}
		catch (InternalException e) {
			logger.warn("Failed to get network " + vlanId);
		}
		if (doc != null){
			Node networkNode = doc.getElementsByTagName(NETWORK_TAG).item(0);
			if (networkNode != null) {
				network = toVLAN(networkNode);
			}
		}
		return network;
	}

	@Override
    public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}
    
	@Override
    public boolean isSubnetDataCenterConstrained() throws CloudException, InternalException{
		return false;
	}

	@Override
    public boolean isVlanDataCenterConstrained() throws CloudException, InternalException{
		return false;
	}
    
	@Override
    public @Nonnull Iterable<NetworkInterface> listNetworkInterfaces(@Nonnull String forVmId) throws CloudException, InternalException {
    	Collection<NetworkInterface> interfaces = new ArrayList<NetworkInterface>(); //TODO: Implement this.
    	return interfaces;
	}
	
    public @Nonnull NetworkInterface getNetworkInterface(@Nonnull String networkHostId) throws CloudException, InternalException {
    	NetworkInterface networkInterface = new NetworkInterface();
		String url = "/" + NETWORK_HOSTS + "/" + networkHostId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null) {
			Node networkHostNode = doc.getElementsByTagName(NETWORK_HOST_TAG).item(0);
			networkInterface = toNetworkInterface(networkHostNode);
		}
		return networkInterface;
	}
    
    private NetworkInterface toNetworkInterface(Node networkHostNode) {
		NetworkInterface networkInterface = new NetworkInterface();
		String id = Terremark.hrefToId(networkHostNode.getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
		networkInterface.setProviderNetworkInterfaceId(id);
		NodeList nodes = networkHostNode.getChildNodes();
		for (int i=0; i < nodes.getLength(); i++) {
			Node hostChild = nodes.item(i);
			if (hostChild.getNodeName().equals("Device")){
				String vmId = Terremark.hrefToId(hostChild.getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
				logger.debug("toNetworkInterface(): network host " + networkInterface.getProviderNetworkInterfaceId() + " vm id = " + vmId);
				networkInterface.setProviderVirtualMachineId(vmId);
			}
			else if (hostChild.getNodeName().equals(NETWORKS_TAG)){
				NodeList networkChildren = networkHostNode.getChildNodes();
				for (int j=0; j < networkChildren.getLength(); j++) {
					Node networkNode = networkChildren.item(j);
					if (networkNode.getNodeName().equals(NETWORK_TAG) && networkNode.getAttributes().getNamedItem(Terremark.TYPE).equals(NETWORK_TYPE)) {
						networkInterface.setProviderVlanId(Terremark.hrefToId(networkNode.getAttributes().getNamedItem(Terremark.HREF).getNodeValue()));
					}
				}
			}
		}

		return networkInterface;
    }

	@Override
    public @Nonnull Iterable<Subnet> listSubnets(@Nonnull String inVlanId) throws CloudException, InternalException{
		return Collections.emptyList();
	}
	
	@Override
    public @Nonnull Iterable<VLAN> listVlans() throws CloudException, InternalException{
		Collection<VLAN> vlans = new ArrayList<VLAN>();
		String url = "/" + NETWORKS + "/" + EnvironmentsAndComputePools.ENVIRONMENTS + "/" + provider.getContext().getRegionId();
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = null;
		try {
			doc = method.invoke();
		} 
		catch (TerremarkException e) {
			throw new CloudException("Failed to list networks " + e.getMessage());
		}
		if (doc != null){
			NodeList networks = doc.getElementsByTagName(NETWORK_TAG);
			for (int i=0; i<networks.getLength(); i++) {
				VLAN vlan = toVLAN(networks.item(i));
				if (vlan != null) {
					vlans.add(vlan);
				}
			}
		}
		return vlans;
	}
	
	private VLAN toVLAN(Node networkNode) throws CloudException, InternalException {
		VLAN network = new VLAN();
		network.setProviderOwnerId(provider.getContext().getAccountNumber());
		NamedNodeMap networkAttrs = networkNode.getAttributes();
		String vlanId = Terremark.hrefToId(networkAttrs.getNamedItem(Terremark.HREF).getTextContent());
		network.setProviderVlanId(vlanId);
		logger.debug("toVLAN(): VLAN ID = " + network.getProviderVlanId());
		String vlanName = networkAttrs.getNamedItem(Terremark.NAME).getTextContent();
		network.setName(vlanName);
		logger.debug("toVLAN(): VLAN ID = " + network.getProviderVlanId() + " Name = " + network.getName());
		network.setCidr(vlanName);
		logger.debug("toVLAN(): VLAN ID = " + network.getProviderVlanId() + " CIDR = " + network.getCidr());
		network.setProviderRegionId(provider.getContext().getRegionId());
		logger.debug("toVLAN(): VLAN ID = " + network.getProviderVlanId() + " Region = " + network.getProviderRegionId());
		network.setCurrentState(VLANState.AVAILABLE);
		NodeList networkChildren = networkNode.getChildNodes();
		for (int i=0; i<networkChildren.getLength(); i++) {
			Node networkChild = networkChildren.item(i);
			if (networkChild.getNodeName().equals("NetworkType")) {
				String type = networkChild.getTextContent();
				network.setDescription(type);
				logger.debug("toVLAN(): VLAN ID = " + network.getProviderVlanId() + " Description = " + network.getDescription());
			}
			else if (networkChild.getNodeName().equals("GatewayAddress")) {
				String gateway = networkChild.getTextContent();
				network.setGateway(gateway);
				logger.debug("toVLAN(): VLAN ID = " + network.getProviderVlanId() + " Gateway = " + network.getGateway());
			}
		}
		network.setDnsServers(null);
		network.setDomainName(null);
		network.setNtpServers(null);
		network.setProviderDataCenterId(null);
		network.setTags(null);
		return network;
	}
	
	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	@Override
    public void removeSubnet(String providerSubnetId) throws CloudException, InternalException{
		throw new OperationNotSupportedException("Subnets not supported");
	}
    
	@Override
    public void removeVlan(String vlanId) throws CloudException, InternalException{
		throw new OperationNotSupportedException("Network removal is not supported");
	}
    
	@Override
    public boolean supportsVlansWithSubnets() throws CloudException, InternalException {
		return false;
	}

}
