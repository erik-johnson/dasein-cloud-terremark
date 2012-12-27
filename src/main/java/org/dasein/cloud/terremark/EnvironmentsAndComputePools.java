package org.dasein.cloud.terremark;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import javax.annotation.Nonnull;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.terremark.Organization;
import org.dasein.cloud.terremark.Terremark;
import org.dasein.cloud.terremark.TerremarkMethod.HttpMethodName;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class EnvironmentsAndComputePools  implements DataCenterServices {

	// API Calls
	public final static String ENVIRONMENTS       = "environments";
	public final static String COMPUTE_POOLS      = "computePools";

	// Response Tags
	public final static String ENVIRONMENT_TAG    = "Environment";
	public final static String COMPUTE_POOL_TAG   = "ComputePool";
	
	//Types
	public final static String LOCATION_TYPE      = "application/vnd.tmrk.cloud.location";
	public final static String COMPUTE_POOL_TYPE  = "application/vnd.tmrk.cloud.computePool";
	private transient Terremark provider;

	EnvironmentsAndComputePools(@Nonnull Terremark provider) {
		this.provider = provider;
	}

	@Override
	public DataCenter getDataCenter(String providerDataCenterId) throws InternalException, CloudException {
		Logger logger = Terremark.getLogger(EnvironmentsAndComputePools.class);
		DataCenter dc = null;
		String url = "/" + COMPUTE_POOLS + "/" + providerDataCenterId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = null;
		try {
			doc = method.invoke();
		} catch (TerremarkException e) {
			logger.warn(e.getMessage());
		} catch (CloudException e) {
			logger.warn(e.getMessage());
		} catch (InternalException e) {
			logger.warn(e.getMessage());
		}
		if (doc != null) {
			NodeList pools = doc.getElementsByTagName(COMPUTE_POOL_TAG);
			dc = toDc(pools.item(0), provider.getContext().getRegionId());
		}
		return dc;
	}

	@Override
	public String getProviderTermForDataCenter(Locale arg0) {
		return "compute pool";
	}

	@Override
	public String getProviderTermForRegion(Locale arg0) {
		return "environment";
	}

	@Override
	public Region getRegion(String regionId)  throws InternalException, CloudException{
		Logger logger = Terremark.getLogger(EnvironmentsAndComputePools.class);
		Node environmentNode = null;
		Region region = null;
		String url = "/" + ENVIRONMENTS + "/" + regionId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = null;
		try {
			doc = method.invoke();
		} catch (TerremarkException e) {
			logger.warn(e.getMessage());
		} catch (CloudException e) {
			logger.warn(e.getMessage());
		} catch (InternalException e) {
			logger.warn(e.getMessage());
		}
		if (doc != null) {
			environmentNode = doc.getElementsByTagName(ENVIRONMENT_TAG).item(0);
			if (environmentNode != null){
				region = toRegion(environmentNode);
			}
		}
		return region;
	}
	
	public Document getEnvironmentById(String regionId) throws InternalException, CloudException {
		String url = "/" + ENVIRONMENTS + "/" + regionId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		return doc;
	}
	
	/**
	 * Retrieves the location ID for a specified region/environment.
	 * @param regionId The id of the region whose location you are requesting
	 * @return The location id String
	 * @throws InternalException
	 * @throws CloudException
	 */
	public String getRegionLocation(String regionId) throws InternalException, CloudException {	
		String locationId = null;
		Document doc = getEnvironmentById(regionId);
		if (doc != null) {
			NodeList links = doc.getElementsByTagName("Link");
			for (int i=0; i < links.getLength(); i++) {
				NamedNodeMap attrs = links.item(i).getAttributes();
				if (attrs.getNamedItem(Terremark.TYPE).getNodeValue().equals(LOCATION_TYPE)) {
					locationId = Terremark.hrefToId(attrs.getNamedItem(Terremark.HREF).getNodeValue());
					break;
				}
			}
		}
		return locationId;
	}

	@Override
	public Collection<DataCenter> listDataCenters(String regionId) throws InternalException, CloudException {	
		Collection<DataCenter> dataCenters = new ArrayList<DataCenter>();
		String url = "/" + COMPUTE_POOLS + "/" + ENVIRONMENTS + "/" + regionId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null) {
			NodeList pools = doc.getElementsByTagName(COMPUTE_POOL_TAG);
			for (int i=0; i < pools.getLength(); i++){
				DataCenter dc = toDc(pools.item(i), regionId);
				dataCenters.add(dc);
			}
		}
		return dataCenters;
	}

	private DataCenter toDc(Node dcNode, String regionId) {
		DataCenter dc = new DataCenter();
		NamedNodeMap attributes = dcNode.getAttributes();
		for (int i=0; i < attributes.getLength(); i++) {
			Node node = attributes.item(i);
			if (node.getNodeName().equals(Terremark.HREF)){
				dc.setProviderDataCenterId(Terremark.hrefToId(node.getNodeValue()));
			}
			else if (node.getNodeName().equals(Terremark.NAME)){
				dc.setName(node.getNodeValue());
			}
		}
		dc.setRegionId(regionId);
		dc.setActive(true);
		dc.setAvailable(true);
		return dc;
	}

	@Override
	public Collection<Region> listRegions() throws InternalException, CloudException {
		Collection<Region> regions = new ArrayList<Region>();
		Organization org = provider.getOrganization();
		String url = "/" + ENVIRONMENTS + "/" + Terremark.ORGANZIATIONS + "/" + org.getId();
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null) {
			NodeList nodes = doc.getElementsByTagName(ENVIRONMENT_TAG);
			for (int i=0; i < nodes.getLength(); i++){
				Region region = toRegion(nodes.item(i));
				regions.add(region);
			}
		}
		return regions;
	}

	private Region toRegion(Node regionNode) throws CloudException, InternalException {
		Region region = new Region();
		NamedNodeMap attributes = regionNode.getAttributes();
		for (int i=0; i < attributes.getLength(); i++) {
			Node node = attributes.item(i);
			if (node.getNodeName().equals(Terremark.HREF)){
				region.setProviderRegionId(Terremark.hrefToId(node.getNodeValue()));
			}
			else if (node.getNodeName().equals(Terremark.NAME)){
				region.setName(node.getNodeValue());
			}
		}
		String locationHref = regionNode.getFirstChild().getFirstChild().getAttributes().item(0).getNodeValue();
		region.setJurisdiction(getJurisdiction(locationHref));
		region.setActive(true);
		region.setAvailable(true);
		return region;
	}

	private String getJurisdiction(String locationHref) throws CloudException, InternalException {
		String jurisdiction = "";
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, locationHref, null, null);
		Document doc = method.invoke();
		jurisdiction = doc.getElementsByTagName("ISO3166").item(0).getTextContent().substring(0, 2);
		return jurisdiction;
	}

}
