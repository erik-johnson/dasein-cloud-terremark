package org.dasein.cloud.terremark.network;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.Direction;
import org.dasein.cloud.network.Firewall;
import org.dasein.cloud.network.FirewallSupport;
import org.dasein.cloud.network.Permission;
import org.dasein.cloud.network.Protocol;
import org.dasein.cloud.terremark.EnvironmentsAndComputePools;
import org.dasein.cloud.terremark.Terremark;
import org.dasein.cloud.terremark.TerremarkMethod;
import org.dasein.cloud.terremark.TerremarkMethod.HttpMethodName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class FirewallRule implements FirewallSupport {

	private Terremark provider = null;

	FirewallRule(@Nonnull Terremark provider) {
		this.provider = provider;
	}

	static private final Logger logger = Logger.getLogger(FirewallRule.class);

	// API Calls
	public final static String FIREWALL_ACLS        = "firewallAcls";
	public final static String CREATE_FIREWALL_ACL  = "createFirewallAcl";

	// Response Tags
	public final static String FIREWALL_ACL_TAG    = "FirewallAcl";

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}
	
	/**
	 * Provides positive authorization for the specified firewall rule. Any call to this method should
	 * result in an override of any previous revocations.
	 * @param firewallId the unique, cloud-specific ID for the firewall being targeted by the new rule
	 * @param cidr the source CIDR (http://en.wikipedia.org/wiki/CIDR) for the allowed traffic
	 * @param protocol the protocol (tcp/udp/icmp) supported by this rule
	 * @param beginPort the beginning of the port range to be allowed, inclusive
	 * @param endPort the end of the port range to be allowed, inclusive
	 * @return the provider ID of the new rule
	 * @throws CloudException an error occurred with the cloud provider establishing the rule
	 * @throws InternalException an error occurred locally trying to establish the rule
	 */
	@Override
	public @Nonnull String authorize(@Nonnull String firewallId, @Nonnull String cidr, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Not yet supported"); //TODO: Add support for this after a network id parameter gets added to cloud-core
	}

	/**
	 * Provides negative authorization for the specified firewall rule. Any call to this method should
	 * result in an override of any previous revocations.
	 * @param firewallId the unique, cloud-specific ID for the firewall being targeted by the new rule
	 * @param cidr the source CIDR (http://en.wikipedia.org/wiki/CIDR) for the denied traffic
	 * @param protocol the protocol (tcp/udp/icmp) supported by this rule
	 * @param beginPort the beginning of the port range to be allowed, inclusive
	 * @param endPort the end of the port range to be allowed, inclusive
	 * @return the provider ID of the new rule
	 * @throws CloudException an error occurred with the cloud provider establishing the rule
	 * @throws InternalException an error occurred locally trying to establish the rule
	 */
	public @Nonnull String deny(@Nonnull String firewallId, @Nonnull String cidr, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
		String providerRuleId = "";
		String id = provider.getContext().getRegionId();
		String url = "/" + FIREWALL_ACLS + "/" + EnvironmentsAndComputePools.ENVIRONMENTS + "/" + id + "/" + Terremark.ACTION + "/" + CREATE_FIREWALL_ACL;
		String body = "";

		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			docBuilder = docFactory.newDocumentBuilder();

			// root element
			Document doc = docBuilder.newDocument();
			Element rootElement = doc.createElement("CreateFirewallAcl");

			Element permissionElement = doc.createElement("Permission");
			permissionElement.appendChild(doc.createTextNode("deny"));
			rootElement.appendChild(permissionElement);

			String protocolString = "TCP";
			switch (protocol) {
			case TCP  : protocolString = "TCP"; break;
			case UDP  : protocolString = "UDP"; break;
			case ICMP : protocolString = "Any"; break;
			}

			Element protocolElement = doc.createElement("Protocol");
			protocolElement.appendChild(doc.createTextNode(protocolString));
			rootElement.appendChild(protocolElement);
			
			Element sourceElement = doc.createElement("Source");
			String networkSize = cidr.substring(cidr.lastIndexOf("/")+1);
			String address = cidr.substring(0, cidr.lastIndexOf("/"));
			Element sourceTypeElement = doc.createElement("Type");
			if (networkSize.equals("32")) {
				sourceTypeElement.appendChild(doc.createTextNode("ExternalIp"));
				sourceElement.appendChild(sourceTypeElement);
				
				Element externalIpAddressElement = doc.createElement("ExternalIpAddress");
				externalIpAddressElement.appendChild(doc.createTextNode(address));
				sourceElement.appendChild(externalIpAddressElement);
			}
			else {
				sourceTypeElement.appendChild(doc.createTextNode("ExternalNetwork"));
				sourceElement.appendChild(sourceTypeElement);
				
				Element externalNetworkElement = doc.createElement("ExternalNetwork");
				
				Element addressElement = doc.createElement("Address");
				addressElement.appendChild(doc.createTextNode(address));
				externalNetworkElement.appendChild(addressElement);
				
				Element sizeElement = doc.createElement("Size");
				sizeElement.appendChild(doc.createTextNode(networkSize));
				externalNetworkElement.appendChild(sizeElement);
				
				sourceElement.appendChild(externalNetworkElement);
			}
			rootElement.appendChild(sourceElement);
			
			Element destinationElement = doc.createElement("Destination");
			Element destinationTypeElement = doc.createElement("Type");
			destinationTypeElement.appendChild(doc.createTextNode("Any"));
			destinationElement.appendChild(destinationTypeElement);
			rootElement.appendChild(destinationElement);
			
			Element portRangeElement = doc.createElement("PortRange");
			
			Element addressElement = doc.createElement("Start");
			addressElement.appendChild(doc.createTextNode(Integer.toString(beginPort)));
			portRangeElement.appendChild(addressElement);
			
			Element sizeElement = doc.createElement("End");
			sizeElement.appendChild(doc.createTextNode(Integer.toString(endPort)));
			portRangeElement.appendChild(sizeElement);
			
			rootElement.appendChild(portRangeElement);
			
			doc.appendChild(rootElement);

			StringWriter stw = new StringWriter(); 
			Transformer serializer = TransformerFactory.newInstance().newTransformer(); 
			serializer.transform(new DOMSource(doc), new StreamResult(stw)); 
			body = stw.toString();

		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}

		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.POST, url, null, body);
		Document doc = method.invoke();
		
		if (doc != null) {
			String ruleHref = doc.getElementsByTagName(FIREWALL_ACL_TAG).item(0).getAttributes().getNamedItem(Terremark.HREF).getNodeValue();
			String uri = FIREWALL_ACLS + "/";
			providerRuleId = ruleHref.substring(ruleHref.lastIndexOf(uri)+uri.length());
		}
		
		return providerRuleId;
	}

	/**
	 * Not supported in Terremark.
	 */
	@Override
	public @Nonnull String create(@Nonnull String name, @Nonnull String description) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Not supported.");
	}

	/**
	 * Not supported in Terremark.
	 */
	@Override
	public @Nonnull String createInVLAN(@Nonnull String name, @Nonnull String description, @Nonnull String providerVlanId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Not supported");
	}


	/**
	 * Not supported in Terremark.
	 */
	@Override
	public void delete(@Nonnull String firewallId) throws InternalException, CloudException {

	}

	/**
	 * Provides the full firewall data for the specified firewall.
	 * @param firewallId the unique ID of the desired firewall
	 * @return the firewall state for the specified firewall instance
	 * @throws InternalException an error occurred locally independent of any events in the cloud
	 * @throws CloudException an error occurred with the cloud provider while performing the operation
	 */
	@Override
	public @Nullable Firewall getFirewall(@Nonnull String firewallId) throws InternalException, CloudException {
		Firewall firewall = null;
		String id = provider.getContext().getRegionId();
		if (firewallId.equals(id)){
			firewall = new Firewall();
			firewall.setActive(true);
			firewall.setAvailable(true);
			String regionName = provider.getDataCenterServices().getRegion(id).getName();
			firewall.setDescription("The " + regionName + " firewall");
			firewall.setName(regionName + " Firewall");
			firewall.setProviderFirewallId(id);
			firewall.setRegionId(id);
		}
		return firewall;
	}

	/**
	 * Provides the firewall terminology for the concept of a firewall. For example, AWS calls a 
	 * firewall a "security group".
	 * @param locale the locale for which you should translate the firewall term
	 * @return the translated term for firewall with the target cloud provider
	 */
	@Override
	public @Nonnull String getProviderTermForFirewall(@Nonnull Locale locale) {
		return "Firewall rule";
	}

	/**
	 * Provides the affirmative rules supported by the named firewall.
	 * @param firewallId the unique ID of the firewall being queried
	 * @return all rules supported by the target firewall
	 * @throws InternalException an error occurred locally independent of any events in the cloud
	 * @throws CloudException an error occurred with the cloud provider while performing the operation
	 */
	@Override
	public @Nonnull Collection<org.dasein.cloud.network.FirewallRule> getRules(@Nonnull String firewallId) throws InternalException, CloudException {
		Collection<org.dasein.cloud.network.FirewallRule> rules = new ArrayList<org.dasein.cloud.network.FirewallRule>();
		String id = provider.getContext().getRegionId();
		if (firewallId.equals(id)){
			String url = "/" + FIREWALL_ACLS + "/" + EnvironmentsAndComputePools.ENVIRONMENTS + "/" + id;
			TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
			Document doc = method.invoke();
			if (doc != null) {
				NodeList firewallAclNodes = doc.getElementsByTagName(FIREWALL_ACL_TAG);
				for (int i=0; i < firewallAclNodes.getLength(); i++) {
					org.dasein.cloud.network.FirewallRule rule = toFirewallRule(firewallAclNodes.item(i));
					if (rule != null) {
						rules.add(rule);
					}
				}

			}
		}
		else {
			rules = Collections.emptyList(); 
		}
		return rules;
	}

	private org.dasein.cloud.network.FirewallRule toFirewallRule(Node firewallAcl) {
		org.dasein.cloud.network.FirewallRule rule = new org.dasein.cloud.network.FirewallRule();
		rule.setFirewallId(provider.getContext().getRegionId());
		String href = firewallAcl.getAttributes().getNamedItem(Terremark.HREF).getNodeValue();
		String uri = FIREWALL_ACLS + "/";
		String ruleId = href.substring(href.lastIndexOf(uri)+uri.length());
		rule.setProviderRuleId(ruleId);
		String sourceType = null;
		rule.setDirection(Direction.INGRESS);

		NodeList firewallRuleChildren = firewallAcl.getChildNodes();
		for (int i=0; i < firewallRuleChildren.getLength(); i++) {
			Node firewallChild = firewallRuleChildren.item(i);
			if (firewallChild.getNodeName().equals("Permission")) {
				if (firewallChild.getTextContent().equals("allow")){
					rule.setPermission(Permission.ALLOW);
				}
				else if (firewallChild.getTextContent().equals("deny")){
					rule.setPermission(Permission.DENY);
				}
			}
			if (firewallChild.getNodeName().equals("PortType")) {
				if (firewallChild.getTextContent().equals("Any")){
					rule.setStartPort(0);
					rule.setEndPort(65535);
				}
			}
			if (firewallChild.getNodeName().equals("Protocol")) {
				System.out.println("Protocol = " + firewallChild.getTextContent());
				if (firewallChild.getTextContent().equals("UDP")){
					System.out.println("Setting protocol to UDP");
					rule.setProtocol(Protocol.UDP);
					System.out.println("Rule protocol = " + rule.getProtocol());
					System.out.println("Rule = " + rule.toString());
				}
				else if (firewallChild.getTextContent().equals("IPSEC")){
					rule.setProtocol(Protocol.ICMP);
				}
				else { // HTTP, HTTPS, TCP, FTP
					System.out.println("Setting protocol to TCP");
					rule.setProtocol(Protocol.TCP);
				}
			}
			if (firewallChild.getNodeName().equals("Source")) {
				Node sourceTypeNode = firewallChild.getFirstChild();
				sourceType = sourceTypeNode.getTextContent();
				Node source = sourceTypeNode.getNextSibling();
				String sourceTmrkType = "";
				if (source != null && source.hasAttributes()) {
					sourceTmrkType = source.getAttributes().getNamedItem(Terremark.TYPE).getNodeValue();
				}
				if (sourceType.equals("Any")){
					rule.setCidr("0.0.0.0/0");
				}
				else if (sourceType.equals(TerremarkIpAddressSupport.IP_ADDRESS_TAG) && sourceTmrkType.equals(TerremarkIpAddressSupport.IP_ADDRESS_TYPE)){
					rule.setCidr(source.getAttributes().getNamedItem(Terremark.NAME).getNodeValue() + "/32");
				}
				else if (sourceType.equals(TerremarkNetworkSupport.NETWORK_TAG) && sourceTmrkType.equals(TerremarkNetworkSupport.NETWORK_TYPE)){
					rule.setCidr(source.getAttributes().getNamedItem(Terremark.NAME).getNodeValue());
				}
				else if (sourceType.equals("ExternalIp")){
					rule.setCidr(source.getTextContent() + "/32");
				}
				else if (sourceType.equals("ExternalNetwork")){
					Node networkAddressNode = source.getFirstChild();
					String networkSize = networkAddressNode.getNextSibling().getTextContent();
					rule.setCidr(networkAddressNode.getTextContent() + "/" + networkSize);
				}
				else if (sourceType.equals("TrustedNetworkGroup")){
					rule = null;
					break;
				}
			}
			if (firewallChild.getNodeName().equals("PortRange")) {
				rule.setStartPort(Integer.parseInt(firewallChild.getFirstChild().getTextContent()));
				rule.setEndPort(Integer.parseInt(firewallChild.getLastChild().getTextContent()));
			}

		}
		return rule;
	}

	/**
	 * Identifies whether or not the current account is subscribed to firewall services in the current region.
	 * @return true if the current account is subscribed to firewall services for the current region
	 * @throws CloudException an error occurred with the cloud provider while determining subscription status
	 * @throws InternalException an error occurred in the Dasein Cloud implementation while determining subscription status
	 */
	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		boolean subscribed = false;
		try {
			getRules(provider.getContext().getRegionId());
			subscribed = true;
		}
		catch (Exception e) {
			logger.warn("");
		}
		return subscribed;
	}

	/**
	 * Lists all firewalls in the current provider context.
	 * @return a list of all firewalls in the current provider context
	 * @throws InternalException an error occurred locally independent of any events in the cloud
	 * @throws CloudException an error occurred with the cloud provider while performing the operation
	 */
	@Override
	public @Nonnull Collection<Firewall> list() throws InternalException, CloudException {
		Collection<Firewall> firewalls = new ArrayList<Firewall>();
		firewalls.add(getFirewall(provider.getContext().getRegionId()));
		return firewalls;
	}

	/**
	 * Not supported in Terremark.
	 */
	@Override
	public void revoke(@Nonnull String firewallId, @Nonnull String cidr, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Not yet implemented"); //TODO: Implement this.
	}

}
