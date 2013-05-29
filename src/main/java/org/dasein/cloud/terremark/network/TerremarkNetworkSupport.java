/**
 * Copyright (C) 2009-2013 Dell, Inc.
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

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
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.AbstractVLANSupport;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.IpAddress;
import org.dasein.cloud.network.NICCreateOptions;
import org.dasein.cloud.network.NICState;
import org.dasein.cloud.network.NetworkInterface;
import org.dasein.cloud.network.Networkable;
import org.dasein.cloud.network.RawAddress;
import org.dasein.cloud.network.RoutingTable;
import org.dasein.cloud.network.Subnet;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.network.VLANState;
import org.dasein.cloud.network.VLANSupport;
import org.dasein.cloud.terremark.EnvironmentsAndComputePools;
import org.dasein.cloud.terremark.Terremark;
import org.dasein.cloud.terremark.TerremarkException;
import org.dasein.cloud.terremark.TerremarkMethod;
import org.dasein.cloud.terremark.TerremarkMethod.HttpMethodName;
import org.dasein.cloud.terremark.compute.VMSupport;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TerremarkNetworkSupport extends AbstractVLANSupport {

	// API Calls
	public final static String NETWORKS       = "networks";
	public final static String NETWORK_HOSTS  = "networkHosts";

	// Response Tags
	public final static String NETWORKS_TAG      = "Networks";
	public final static String NETWORK_TAG       = "Network";
	public final static String NETWORK_HOST_TAG  = "NetworkHost";

	// Types
	public final static String NETWORK_TYPE       = "application/vnd.tmrk.cloud.network";
	public final static String NETWORK_IPV6_TYPE  = "application/vnd.tmrk.cloud.network.ipv6";
	public final static String NETWORK_HOST_TYPE  = "application/vnd.tmrk.cloud.networkHost";

	static Logger logger = Terremark.getLogger(TerremarkNetworkSupport.class);

	private Terremark provider;

	TerremarkNetworkSupport(Terremark provider) {
        super(provider);
        this.provider = provider;
    }

	/**
	 * Specifies the maximum number of network interfaces that may be provisioned.
	 * @return the maximum number of network interfaces that may be provisioned or -1 for no limit or -2 for unknown
	 * @throws CloudException an error occurred requesting the limit from the cloud provider
	 * @throws InternalException a local error occurred figuring out the limit
	 */
	@Override
	public int getMaxNetworkInterfaceCount() throws CloudException, InternalException {
		return -2;
	}

	@Override
	public int getMaxVlanCount() throws CloudException, InternalException{
		return -2;
	}

	/**
	 * Fetches the network interfaced specified by the unique network interface ID.
	 * @param networkHostId the unique ID of the desired network interface
	 * @return the network interface that matches the specified ID
	 * @throws CloudException an error occurred in the cloud provider fetching the desired network interface
	 * @throws InternalException a local error occurred while fetching the desired network interface
	 */
	public @Nonnull NetworkInterface getNetworkInterface(@Nonnull String networkHostId) throws CloudException, InternalException {
		NetworkInterface networkInterface = null;
		String url = "/" + NETWORK_HOSTS + "/" + networkHostId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = null;
		try {
			doc = method.invoke();
		}
		catch (CloudException e) {
			logger.debug("Did not find requested network interface");
		}
		catch (InternalException i) {
			logger.debug("Did not find requested network interface");
		}
		if (doc != null) {
			Node networkHostNode = doc.getElementsByTagName(NETWORK_HOST_TAG).item(0);
			networkInterface = toNetworkInterface(networkHostNode);
		}
		return networkInterface;
	}

	/**
	 * Identifies the provider term for a network interface.
	 * @param locale the locale in which the term should be provided
	 * @return a localized term for "network interface" specific to this cloud provider
	 */
	@Override
	public String getProviderTermForNetworkInterface(Locale locale){
		return "network host";
	}

	@Override
	public String getProviderTermForSubnet(Locale locale){
		return "subnet";
	}

	@Override
	public String getProviderTermForVlan(Locale locale){
		return "network";
	}

	/**
	 * Indicates whether or not you may or must manage routing tables for your VLANs/subnets.
	 * @return the level of routing table management that is required
	 * @throws CloudException an error occurred in the cloud provider determining support
	 * @throws InternalException a local error occurred processing the request
	 */
	@Override
	public Requirement getRoutingTableSupport() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	/**
	 * Indicates whether subnets in VLANs are required, optional, or not supported.
	 * @return the level of support for subnets in this cloud
	 * @throws CloudException an error occurred in the cloud while determining the support level
	 * @throws InternalException a local error occurred determining subnet support level
	 */
	@Override
	public Requirement getSubnetSupport() throws CloudException, InternalException {
		return Requirement.NONE;
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

	/**
	 * Indicates whether or not this cloud included the concept of network interfaces in its networking support.
	 * @return true if this cloud supports network interfaces as part of its networking concepts
	 * @throws CloudException an error occurred with the cloud provider determining support for network interfaces
	 * @throws InternalException a local error occurred determining support for network interfaces
	 */
	@Override
	public boolean isNetworkInterfaceSupportEnabled() throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean isSubnetDataCenterConstrained() throws CloudException, InternalException{
		return false;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean isVlanDataCenterConstrained() throws CloudException, InternalException{
		return false;
	}

	/**
	 * Lists the IDs of the firewalls protecting the specified network interface.
	 * @param nicId the network interface ID of the desired network interface
	 * @return the firewall/security group IDs of all firewalls supporting this network interface
	 * @throws CloudException an error occurred with the cloud providing fetching the firewall IDs
	 * @throws InternalException a local error occurred while attempting to communicate with the cloud
	 */
	@Override
	public Collection<String> listFirewallIdsForNIC(String nicId) throws CloudException, InternalException {
		Collection<String> firewallIds = new ArrayList<String>();
		String url = "/" + NETWORK_HOSTS + "/" + nicId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null) {
			NodeList acls = doc.getElementsByTagName(FirewallRule.FIREWALL_ACL_TAG);
			for (int i=0; i<acls.getLength(); i++) {
				String aclId = Terremark.hrefToFirewallRuleId(acls.item(i).getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
				firewallIds.add(aclId);
			}
		}
		return firewallIds;
	}

	/**
	 * Lists all network interfaces currently provisioned in the current region.
	 * @return a list of all provisioned network interfaces in the current region
	 * @throws CloudException an error occurred with the cloud provider fetching the network interfaces
	 * @throws InternalException a local error occurred fetching the network interfaces
	 */
	@Override
	public Iterable<NetworkInterface> listNetworkInterfaces() throws CloudException, InternalException {
		Collection<NetworkInterface> networkHosts = new ArrayList<NetworkInterface>();
		String url = "/" + NETWORK_HOSTS + "/" + EnvironmentsAndComputePools.ENVIRONMENTS + "/" + provider.getContext().getRegionId();
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null){
			NodeList networkHostNodes = doc.getElementsByTagName("Host");
			for (int i=0; i<networkHostNodes.getLength(); i++) {
				String nicId = Terremark.hrefToId(networkHostNodes.item(i).getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
				NetworkInterface nic = getNetworkInterface(nicId);
				if (nic != null) {
					networkHosts.add(nic);	
				}
			}
		}
		return networkHosts;
	}

	/**
	 * Lists all network interfaces attached to a specific virtual machine.
	 * @param forVmId the virtual machine whose network interfaces you want listed
	 * @return the network interfaces attached to the specified virtual machine
	 * @throws CloudException an error occurred with the cloud provider determining the attached network interfaces
	 * @throws InternalException a local error occurred listing the network interfaces attached to the specified virtual machine
	 */
	@Override
	public Iterable<NetworkInterface> listNetworkInterfacesForVM(String forVmId) throws CloudException, InternalException {
		logger.trace("enter - listNetworkInterfacesForVM(" + forVmId + ")");
		Collection<NetworkInterface> netowrkHost = new ArrayList<NetworkInterface>();
		String url = "/" + VMSupport.VIRTUAL_MACHINES + "/" + forVmId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null){
			Node networkHostNode = doc.getElementsByTagName(NETWORK_HOST_TAG).item(0);
			String nicId = Terremark.hrefToId(networkHostNode.getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
			netowrkHost.add(getNetworkInterface(nicId));
		}
		logger.trace("exit - listNetworkInterfacesForVM(" + forVmId + ")");
		return netowrkHost;
	}

	/**
	 * Lists all network interfaces connected to a specific VLAN. Valid only if the cloud provider supports VLANs.
	 * @param vlanId the VLAN ID for the VLAN in which you are searching
	 * @return all interfaces within the specified VLAN
	 * @throws CloudException an error occurred in the cloud identifying the matching network interfaces
	 * @throws InternalException a local error occurred constructing the cloud query
	 */
	@Override
	public Iterable<NetworkInterface> listNetworkInterfacesInVLAN(String vlanId) throws CloudException, InternalException {
		Collection<NetworkInterface> vlanNics = new ArrayList<NetworkInterface>();
		for (NetworkInterface nic : listNetworkInterfaces()) {
			if (nic.getProviderVlanId().equals(vlanId)) {
				vlanNics.add(nic);
			}
		}
		return vlanNics;
	}

	/**
	 * Lists the status of all network interfaces currently provisioned in the current region.
	 * @return a list of status for all provisioned network interfaces in the current region
	 * @throws CloudException an error occurred with the cloud provider fetching the network interfaces
	 * @throws InternalException a local error occurred fetching the network interfaces
	 */
	@Override
	public Iterable<ResourceStatus> listNetworkInterfaceStatus() throws CloudException, InternalException {
		Collection<ResourceStatus> networkHostsStatus = new ArrayList<ResourceStatus>();
		String url = "/" + NETWORK_HOSTS + "/" + EnvironmentsAndComputePools.ENVIRONMENTS + "/" + provider.getContext().getRegionId();
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null){
			NodeList networkHostNodes = doc.getElementsByTagName("Host");
			for (int i=0; i<networkHostNodes.getLength(); i++) {
				String nicId = Terremark.hrefToId(networkHostNodes.item(i).getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
				if (nicId != null) {
					networkHostsStatus.add(new ResourceStatus(nicId, NICState.AVAILABLE));	
				}
			}
		}
		return networkHostsStatus;
	}

	/**
	 * Lists all resources associated with the specified VLAN. In many clouds, this is a very expensive operation. So
	 * call this method with care.
	 * @param inVlanId the VLAN for whom you are seeking the resource list
	 * @return a list of resources associated with the specified VLAN
	 * @throws CloudException an error occurred in the cloud identifying the matching resources
	 * @throws InternalException a local error occurred constructing the cloud query
	 */
	@Override
	public Iterable<Networkable> listResources(String inVlanId) throws CloudException, InternalException {
		logger.trace("enter - listResources(" + inVlanId + ")");
		Collection<Networkable> resouces = new ArrayList<Networkable>();
		for (IpAddress ip : provider.getNetworkServices().getIpAddressSupport().listPrivateIps(inVlanId, false, false, true)) {
			resouces.add(ip);
		}
		logger.trace("exit - listResources(" + inVlanId + ")");
		return resouces;
	}

	/**
	 * Lists all IP protocol versions supported for VLANs in this cloud.
	 * @return a list of supported versions
	 * @throws CloudException an error occurred checking support for IP versions with the cloud provider
	 * @throws InternalException a local error occurred preparing the supported version
	 */
	@Override
	public Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
		Collection<IPVersion> versions = new ArrayList<IPVersion>();
		versions.add(IPVersion.IPV4);
		versions.add(IPVersion.IPV6);
		return versions;
	}

	/**
	 * Lists all VLANs in the current region.
	 * @return all VLANs in the current region
	 * @throws CloudException an error occurred communicating with the cloud provider
	 * @throws InternalException an error occurred within the Dasein Cloud implementation
	 */
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

	/**
	 * Lists the status of all VLANs in the current region.
	 * @return the status of all VLANs in the current region
	 * @throws CloudException an error occurred communicating with the cloud provider
	 * @throws InternalException an error occurred within the Dasein Cloud implementation
	 */
	@Override
	public Iterable<ResourceStatus> listVlanStatus() throws CloudException, InternalException {
		Collection<ResourceStatus> vlans = new ArrayList<ResourceStatus>();
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
				ResourceStatus vlanStatus = toVLANStatus(networks.item(i));
				vlans.add(vlanStatus);
			}
		}
		return vlans;
	}

	/**
	 * Indicates whether or not this cloud allows enabling of internet gateways for VLANs. This is not relevant if all VLANs are Internet
	 * routable or if they simply cannot be made routable.
	 * @return true if this cloud supports the optional enablement of Internet gateways for VLANS, false if all VLANs are either always or never Internet routable
	 * @throws CloudException an error occurred determining this capability from the cloud provider
	 * @throws InternalException a local error occurred determining this capability
	 */
	@Override
	public boolean supportsInternetGatewayCreation() throws CloudException, InternalException {
		return false;
	}

	/**
	 * Indicates whether you can specify a raw IP address as a target for your routing table.
	 * @return true if you can specify raw addresses, false if you need to specify other resources
	 * @throws CloudException an error occurred identifying support
	 * @throws InternalException a local error occurred identifying support
	 */
	@Override
	public boolean supportsRawAddressRouting() throws CloudException, InternalException {
		return false;
	}

	private NetworkInterface toNetworkInterface(Node networkHostNode) {
		NetworkInterface networkInterface = new NetworkInterface();
		String id = Terremark.hrefToId(networkHostNode.getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
		networkInterface.setProviderNetworkInterfaceId(id);
		String name = Terremark.hrefToId(networkHostNode.getAttributes().getNamedItem(Terremark.NAME).getNodeValue());
		networkInterface.setName(name);
		networkInterface.setProviderRegionId(provider.getContext().getRegionId());
		ArrayList<RawAddress> addresses = new ArrayList<RawAddress>();
		NodeList nodes = networkHostNode.getChildNodes();
		for (int i=0; i < nodes.getLength(); i++) {
			Node hostChild = nodes.item(i);
			if (hostChild.getNodeName().equals("Device")){
				String vmId = Terremark.hrefToId(hostChild.getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
				logger.debug("toNetworkInterface(): network host " + networkInterface.getProviderNetworkInterfaceId() + " vm id = " + vmId);
				networkInterface.setProviderVirtualMachineId(vmId);
			}
			else if (hostChild.getNodeName().equals(NETWORKS_TAG)){
				NodeList networksChildren = hostChild.getChildNodes();
				for (int j=0; j < networksChildren.getLength(); j++) {
					Node networkNode = networksChildren.item(j);
					networkInterface.setProviderVlanId(Terremark.hrefToNetworkId(networkNode.getAttributes().getNamedItem(Terremark.HREF).getNodeValue()));
					NodeList networkChildren = networkNode.getChildNodes();
					for (int k=0; k<networkChildren.getLength(); k++) {
						if (networkChildren.item(k).getNodeName().equals(TerremarkIpAddressSupport.IP_ADDRESS_TAG)) {
							NodeList ipAddressNodes = networkChildren.item(k).getChildNodes();
							for (int l=0; l<ipAddressNodes.getLength(); l++) {
								Node ipNode = ipAddressNodes.item(l);
								String ipAddress = ipNode.getAttributes().getNamedItem(Terremark.NAME).getNodeValue();
								String type = ipNode.getAttributes().getNamedItem(Terremark.TYPE).getNodeValue();
								IPVersion version = null;
								if (type.equals(TerremarkIpAddressSupport.IP_ADDRESS_TYPE)) {
									version = IPVersion.IPV4;
								}
								else if (type.equals(TerremarkIpAddressSupport.IP_ADDRESS_IPV6_TYPE)) {
									version = IPVersion.IPV6;
								}
								addresses.add(new RawAddress(ipAddress, version));
							}
						}
					}
				}
			}
		}

		networkInterface.setIpAddresses(addresses.toArray(new RawAddress[addresses.size()]));
		networkInterface.setCurrentState(NICState.AVAILABLE);


		return networkInterface;
	}

	private VLAN toVLAN(Node networkNode) throws CloudException, InternalException {
		VLAN network = new VLAN();
		network.setProviderOwnerId(provider.getContext().getAccountNumber());
		NamedNodeMap networkAttrs = networkNode.getAttributes();
		String vlanId = Terremark.hrefToNetworkId(networkAttrs.getNamedItem(Terremark.HREF).getTextContent());
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
				boolean ipv6 = networkAttrs.getNamedItem(Terremark.TYPE).getTextContent().equals(NETWORK_IPV6_TYPE);
				if (ipv6) {
					type = type + " IPv6";
				}
				else {
					type = type + " IPv4";
				}
				network.setNetworkType(type);
				network.setDescription(type);
				logger.debug("toVLAN(): VLAN ID = " + network.getProviderVlanId() + " Description = " + network.getDescription());
				break;
			}
		}
		network.setDnsServers(null);
		network.setDomainName(null);
		network.setNtpServers(null);
		network.setProviderDataCenterId(null);
		network.setTags(null);

		return network;
	}

	private ResourceStatus toVLANStatus(Node networkNode) throws CloudException, InternalException {
		NamedNodeMap networkAttrs = networkNode.getAttributes();
		String vlanId = Terremark.hrefToId(networkAttrs.getNamedItem(Terremark.HREF).getTextContent());
		return new ResourceStatus(vlanId, VLANState.AVAILABLE);
	}

}
