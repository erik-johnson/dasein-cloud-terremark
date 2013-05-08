package org.dasein.cloud.terremark.compute;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import javax.annotation.Nonnull;
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
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.Volume;
import org.dasein.cloud.compute.VolumeCreateOptions;
import org.dasein.cloud.compute.VolumeFormat;
import org.dasein.cloud.compute.VolumeProduct;
import org.dasein.cloud.compute.VolumeState;
import org.dasein.cloud.compute.VolumeSupport;
import org.dasein.cloud.compute.VolumeType;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.terremark.EnvironmentsAndComputePools;
import org.dasein.cloud.terremark.Terremark;
import org.dasein.cloud.terremark.TerremarkException;
import org.dasein.cloud.terremark.TerremarkMethod;
import org.dasein.cloud.terremark.TerremarkMethod.HttpMethodName;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DiskSupport implements VolumeSupport {

	// API Calls
	public final static String DETACHED_DISKS              = "detachedDisks";
	public final static String DISKS                       = "disks";

	// Response Tags
	public final static String DETACHED_DISK_TAG           = "DetachedDisk";
	public final static String DISK_TAG                    = "Disk";

	// Types
	public final static String DETACHED_DISK_TYPE          = "application/vnd.tmrk.cloud.detachedDisk";

	//Operation Names
	public final static String DELETE_DISK_OPERATION       = "Delete Detached Virtual Disk";
	public final static String DETACH_DISK_OPERATION       = "Detach Virtual Disk";
	public final static String ATTACH_DISK_OPERATION       = "Attach Virtual Disk";
	public final static String RETRY_ATTACH_DISK_OPERATION = "Retry attach disk";

	// Task Wait Times
	public final static long DEFAULT_SLEEP                 = CalendarWrapper.SECOND * 10;
	public final static long DEFAULT_TIMEOUT               = CalendarWrapper.MINUTE * 45;

	static Logger logger = Terremark.getLogger(DiskSupport.class);

	private Terremark provider;

	DiskSupport(Terremark provider) {
		this.provider = provider;
	}

	@Override
	public void attach(String volumeId, String toServer, String device) throws InternalException, CloudException {
		Volume detachedDisk = null;
		if (volumeId.contains(":")) {
			throw new InternalException("Can't attach " + volumeId + " because this volume is already attached to a vm.");
		}
		else {
			detachedDisk = getDetachedDisk(volumeId);
		}
		if (detachedDisk == null ) {
			throw new InternalException("Failed to find detached disk " + volumeId);
		}

		String url = "/" + VMSupport.VIRTUAL_MACHINES + "/" + toServer + "/hardwareConfiguration/" + DISKS + "/actions/attach";
		String body="";
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			docBuilder = docFactory.newDocumentBuilder();

			Document doc = docBuilder.newDocument();
			Element rootElement = doc.createElement("AttachDisks");
			Element detachedDisksElement = doc.createElement("DetachedDisks");
			Element detachedDiskElement = doc.createElement("DetachedDisk");

			String diskHref = Terremark.DEFAULT_URI_PATH + "/" + DETACHED_DISKS + "/" + volumeId;
			detachedDiskElement.setAttribute(Terremark.HREF, diskHref);
			detachedDiskElement.setAttribute(Terremark.NAME, detachedDisk.getName());
			detachedDiskElement.setAttribute(Terremark.TYPE, DETACHED_DISK_TYPE);

			detachedDisksElement.appendChild(detachedDiskElement);


			rootElement.appendChild(detachedDisksElement);

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
			String taskHref = Terremark.getTaskHref(doc, ATTACH_DISK_OPERATION);
			try {
				provider.waitForTask(taskHref, DEFAULT_SLEEP, DEFAULT_TIMEOUT);
			}
			catch (CloudException e) {
				String retryHref = "/attachDiskRetryOperations/" + VMSupport.VIRTUAL_MACHINES + "/" + toServer + "/action/retry";
				TerremarkMethod retryMethod = new TerremarkMethod(provider, HttpMethodName.POST, retryHref, null, "");
				Document retryDoc = retryMethod.invoke();
				
				String retryTaskHref = Terremark.getTaskHref(retryDoc, RETRY_ATTACH_DISK_OPERATION);
				provider.waitForTask(retryTaskHref, DEFAULT_SLEEP, DEFAULT_TIMEOUT);
			}

		}
	}

    /**
     * Creating detached volumes is not supported in Terremark.
     * @throws OperationNotSupportedException this method is not supported in Terremark
     */
	@Override
	public String create(String fromSnapshot, int sizeInGb, String inZone) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Creating volumes is not supported");
	}
	
    /**
     * Creating detached volumes is not supported in Terremark.
     * @throws OperationNotSupportedException this method is not supported in Terremark
     */
	@Override
    public @Nonnull String createVolume(@Nonnull VolumeCreateOptions options) throws InternalException, CloudException {
    	throw new OperationNotSupportedException("Creating volumes is not supported");
    }

    /**
     * Detaches the specified volume from any virtual machines to which it might be attached.
     * @param volumeId the unique ID of the volume to be detached
     * @throws InternalException an error occurred in the Dasein Cloud implementation while performing the detachment
     * @throws CloudException the detachment failed with the cloud provider
     */
	@Override
	public void detach(String volumeId) throws InternalException, CloudException {
		detach(volumeId, false);
	}
	
    /**
     * Detaches the specified volume from any virtual machines to which it might be attached with the option to
     * force the detachment when some cloud state is preventing it.
     * @param volumeId the unique ID of the volume to be detached
     * @param force indicate whether or not the detach should be forced even if the VM is not releasing it
     * @throws InternalException an error occurred in the Dasein Cloud implementation while performing the detachment
     * @throws CloudException the detachment failed with the cloud provider
     */
    public void detach(@Nonnull String volumeId, boolean force) throws InternalException, CloudException {
		String vmId;
		String diskIndex;
		if (volumeId.contains(":")) {
			String[] volumeIds = volumeId.split(":");
			vmId = volumeIds[0];
			diskIndex = volumeIds[1];
		}
		else {
			throw new InternalException("Can't detach " + volumeId + " because this volume is not attached to a vm.");
		}

		if (diskIndex.equals("0")) {
			throw new InternalException("Can't detach a system disk");
		}
		
		VmState state = provider.getComputeServices().getVirtualMachineSupport().getVirtualMachine(vmId).getCurrentState();
		if (!state.equals(VmState.STOPPED) && !state.equals(VmState.TERMINATED)) {
			if (force) {
				provider.getComputeServices().getVirtualMachineSupport().stop(vmId);
			}
			else {
				throw new InternalException("You can only detach volumes from stopped servers.");
			}
		}
		else if (state.equals(VmState.TERMINATED)) {
			throw new InternalException("You can't detach a volume from a terminated server.");
		}


		Volume volume = getVolume(volumeId);
		String name = System.currentTimeMillis() + "-" + volume.getName();

		String url = "/" + VMSupport.VIRTUAL_MACHINES + "/" + vmId + "/hardwareConfiguration/" + DISKS + "/actions/detach";
		String body="";
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			docBuilder = docFactory.newDocumentBuilder();

			Document doc = docBuilder.newDocument();
			Element rootElement = doc.createElement("DetachDisk");
			rootElement.setAttribute(Terremark.NAME, name.substring(0, 15));

			Element descriptionElement = doc.createElement("Description");
			descriptionElement.appendChild(doc.createTextNode(name));
			rootElement.appendChild(descriptionElement);

			Element diskElement = doc.createElement(DISK_TAG);
			Element indexElement = doc.createElement("Index");
			indexElement.appendChild(doc.createTextNode(diskIndex));
			diskElement.appendChild(indexElement);
			rootElement.appendChild(diskElement);

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
			try {
				String diskId = Terremark.hrefToId(doc.getElementsByTagName(DETACHED_DISK_TAG).item(0).getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
				String computePool = Terremark.hrefToId(doc.getElementsByTagName("Link").item(0).getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
				String disksUrl = "/" + DETACHED_DISKS + "/" + EnvironmentsAndComputePools.COMPUTE_POOLS + "/" + computePool;
				TerremarkMethod diskMethod = new TerremarkMethod(provider, HttpMethodName.GET, disksUrl, null, null);
				Document disksDoc = diskMethod.invoke();
				String taskHref = null;
				NodeList tasks = disksDoc.getElementsByTagName("Tasks");
				for (int i=0; i<tasks.getLength(); i++) {
					Node tasksNode = tasks.item(i);
					if (tasksNode.getAttributes().getNamedItem(Terremark.HREF).getNodeValue().contains("/" + diskId)) {
						NodeList taskElements = tasksNode.getChildNodes();
						for (int j=0; j<taskElements.getLength(); j++) {
							Node taskElement = taskElements.item(j);
							NodeList taskChildren = taskElement.getChildNodes();
							for (int k=0; k<taskChildren.getLength(); k++) {
								Node taskChild = taskChildren.item(k);
								if (taskChild.getNodeName().equals(Terremark.OPERATION_TAG)) {
									if (taskChild.getTextContent().equals(DETACH_DISK_OPERATION)) { 
										taskHref = taskElement.getAttributes().getNamedItem(Terremark.HREF).getNodeValue();
										break;
									}
								}
							}
							if (taskHref != null) {
								break;
							}
						}
					}
				}
				try {
					provider.waitForTask(taskHref, DEFAULT_SLEEP, DEFAULT_TIMEOUT);
				}
				catch (CloudException e) {
					String retryHref = "/detachDiskRetryOperations/" + VMSupport.VIRTUAL_MACHINES + "/" + vmId + "/action/retry";
					TerremarkMethod retryMethod = new TerremarkMethod(provider, HttpMethodName.POST, retryHref, null, "");
					Document retryDoc = retryMethod.invoke();
					
					String retryTaskHref = Terremark.getTaskHref(retryDoc, DETACH_DISK_OPERATION);
					provider.waitForTask(retryTaskHref, DEFAULT_SLEEP, DEFAULT_TIMEOUT);
				}
			}
			catch (Exception e) {
				logger.warn("Encountered an error while getting and polling the task: " + e);
				e.printStackTrace();
			}

		}
    }

	private Volume getAttachedDisk(String volumeId) throws InternalException, CloudException {
		Volume volume = null;
		String vmId = null;
		if (volumeId.contains(":")) {
			String[] volumeIds = volumeId.split(":");
			vmId = volumeIds[0];
		}
		else {
			throw new InternalException("Invalid attached disk ID.");
		}

		Collection<Volume> attachedDisks = getVirtualMachineDisks(vmId);
		for (Volume attachedDisk : attachedDisks) {
			if (attachedDisk.getProviderVolumeId().equals(volumeId)) {
				volume  = attachedDisk;
				break;
			}
		}

		return volume;
	}

	private Volume getDetachedDisk(String diskId) throws CloudException, InternalException {
		Volume volume = null;
		String url = "/" + DETACHED_DISKS + "/" + diskId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null) {
			volume = new Volume();
			Node diskNode = doc.getElementsByTagName(DETACHED_DISK_TAG).item(0);
			volume.setProviderVolumeId(diskId);
			volume.setName(diskNode.getAttributes().getNamedItem(Terremark.NAME).getNodeValue());
			if (volume.getName().contains("-")) {
				String timestamp = volume.getName().split("-")[0];
				try {
					volume.setCreationTimestamp(Long.parseLong(timestamp));
				}
				catch (NumberFormatException e) {
					logger.info("Failed to set volume creation timestamp.");
				}
			}
			volume.setProviderRegionId(provider.getContext().getRegionId());
			NodeList diskChildren = diskNode.getChildNodes();
			for (int i=0; i<diskChildren.getLength(); i++) {
				Node diskChild = diskChildren.item(i);
				if (diskChild.getNodeName().equals("Links")) {
					String cpHref = diskChild.getFirstChild().getAttributes().getNamedItem(Terremark.HREF).getNodeValue();
					volume.setProviderDataCenterId(Terremark.hrefToId(cpHref));
				}
				else if (diskChild.getNodeName().equals("Size")) {
					String unit = diskChild.getFirstChild().getTextContent();
					String diskSize = diskChild.getLastChild().getTextContent();
					int sizeInGb = 0;
					if (unit.equalsIgnoreCase("GB")) {
						sizeInGb = Integer.parseInt(diskSize);
					}
					else if (unit.equalsIgnoreCase("MB")) {
						sizeInGb = (Integer.parseInt(diskSize) / 1024);
					}
					else if (unit.equalsIgnoreCase("TB")) {
						sizeInGb = (Integer.parseInt(diskSize) * 1024);
					}
					volume.setSize(new Storage<Gigabyte>(sizeInGb, Storage.GIGABYTE));
				}
				else if (diskChild.getNodeName().equals("Status")) {
					if (diskChild.getTextContent().equals("Available")) {
						volume.setCurrentState(VolumeState.AVAILABLE);
					}
					else {
						volume.setCurrentState(VolumeState.PENDING);
					}
				}
			}
		}
		return volume;
	}

	/**
     * Indicates the maximum number of volumes that may be provisioned in this account.
     * @return the maximum number of volumes that may be provisioned, -1 for unlimited, or -2 for unknown
     * @throws InternalException an error occurred within the Dasein Cloud implementation determining the limit
     * @throws CloudException an error occurred retrieving the limit from the cloud
     */
	@Override
    public int getMaximumVolumeCount() throws InternalException, CloudException {
		return -2;
	}

	/**
     * Indicates the largest provisionable volume.
     * @return the largest provisionable volume or null if a limit is not known
     * @throws InternalException an error occurred within the Dasein Cloud implementation determining the limit
     * @throws CloudException an error occurred retrieving the limit from the cloud
     */
	@Override
	public Storage<Gigabyte> getMaximumVolumeSize() throws InternalException, CloudException {
		return new Storage<Gigabyte>(512, Storage.GIGABYTE);
	}

	/**
     * Indicates the smallest provisionable volume.
     * @return the size of the smallest provisionable volume
     * @throws InternalException an error occurred within the Dasein Cloud implementation determining the limit
     * @throws CloudException an error occurred retrieving the limit from the cloud
     */
	@Override
	public Storage<Gigabyte> getMinimumVolumeSize() throws InternalException, CloudException {
		return new Storage<Gigabyte>(1, Storage.GIGABYTE);
	}

	@Override
	public String getProviderTermForVolume(Locale locale) {
		return "Disk";
	}

	protected Collection<Volume> getVirtualMachineDisks(String vmId) throws CloudException {
		Collection<Volume> disks = new ArrayList<Volume>();
		String url = "/" + VMSupport.VIRTUAL_MACHINES + "/" + vmId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = null;
		try {
			doc = method.invoke();
		} catch (TerremarkException e) {
			logger.warn("Failed to get vm " + vmId);
		} catch (CloudException e) {
			logger.warn("Failed to get vm " + vmId);
		} catch (InternalException e) {
			logger.warn("Failed to get vm " + vmId);
		}
		if (doc != null){
			String dcId = null;
			NodeList linkNodes = doc.getElementsByTagName("Link");
			for(int i=0; i<linkNodes.getLength(); i++) {
				if (linkNodes.item(i).getAttributes().getNamedItem(Terremark.TYPE).getNodeValue().equals(EnvironmentsAndComputePools.COMPUTE_POOL_TYPE)) {
					dcId = Terremark.hrefToId(linkNodes.item(i).getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
				}
			}

			long createTime = 0;
			String taskHref = Terremark.getTaskHref(doc, VMSupport.CONFIGURE_OPERATION);
			if (taskHref == null) {
				String date = null;
				NodeList taskElements = doc.getElementsByTagName(Terremark.TASK_TAG);
				for (int j=0; j<taskElements.getLength(); j++) {
					Node taskElement = taskElements.item(j);
					NodeList taskChildren = taskElement.getChildNodes();
					for (int k=0; k<taskChildren.getLength(); k++) {
						Node taskChild = taskChildren.item(k);
						if (taskChild.getNodeName().equals(Terremark.OPERATION_TAG)) {
							if (!taskChild.getTextContent().equals(VMSupport.CREATE_SERVER_OPERATION)) { 
								break;
							}
						}
						else if (taskChild.getNodeName().equals("StartTime")) {
							date = taskChild.getTextContent();
						}
					}
					if (date != null) {
						createTime = Terremark.parseIsoDate(date).getTime();
						break;
					}
				}
			}

			NodeList diskNodes = doc.getElementsByTagName(DISK_TAG);
			for(int i=0; i<diskNodes.getLength(); i++) {
				Volume disk = new Volume();
				disk.setCurrentState(VolumeState.AVAILABLE);
				disk.setProviderRegionId(provider.getContext().getRegionId());
				disk.setProviderSnapshotId(null);
				disk.setProviderVirtualMachineId(vmId);
				disk.setProviderDataCenterId(dcId);
				if (createTime > 0) {
					disk.setCreationTimestamp(createTime);
				}

				NodeList diskChildren = diskNodes.item(i).getChildNodes();
				for (int j=0; j<diskChildren.getLength(); j++) {
					Node diskChild = diskChildren.item(j);
					if (diskChild.getNodeName().equals("Index")) {
						String diskIndex = diskChild.getTextContent();
						disk.setProviderVolumeId(vmId + ":" + diskIndex);
						if (diskIndex.equals("0")) {
							disk.setRootVolume(true);
							String os = doc.getElementsByTagName("OperatingSystem").item(0).getAttributes().getNamedItem(Terremark.NAME).getNodeValue();
							disk.setGuestOperatingSystem(Platform.guess(os));
						}
					}
					else if (diskChild.getNodeName().equals("Size")) {
						String diskUnit = diskChild.getFirstChild().getTextContent();
						String diskSize = diskChild.getLastChild().getTextContent();
						int sizeInGb = 0;
						if (diskUnit.equalsIgnoreCase("GB")) { // API Doc says disks use GB
							sizeInGb = Integer.parseInt(diskSize);
						}
						else if (diskUnit.equalsIgnoreCase("MB")) {
							sizeInGb = (Integer.parseInt(diskSize) / 1024);
						}
						else if (diskUnit.equalsIgnoreCase("TB")) {
							sizeInGb = (Integer.parseInt(diskSize) * 1024);
						}
						disk.setSize(new Storage<Gigabyte>(sizeInGb, Storage.GIGABYTE));
					}
					else if (diskChild.getNodeName().equals("Name")) {
						disk.setName(diskChild.getTextContent());
					}
				}
				disk.setDescription(disk.getName());
				disk.setFormat(VolumeFormat.BLOCK);
				
				disk.setType(VolumeType.HDD);
				disks.add(disk);
			}
		}
		return disks;
	}
	
	private Collection<ResourceStatus> getVirtualMachineDisksStatus(String vmId) throws CloudException {
		Collection<ResourceStatus> disksStatus = new ArrayList<ResourceStatus>();
		String url = "/" + VMSupport.VIRTUAL_MACHINES + "/" + vmId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = null;
		try {
			doc = method.invoke();
		} catch (TerremarkException e) {
			logger.warn("Failed to get vm " + vmId);
		} catch (CloudException e) {
			logger.warn("Failed to get vm " + vmId);
		} catch (InternalException e) {
			logger.warn("Failed to get vm " + vmId);
		}
		if (doc != null){

			NodeList diskNodes = doc.getElementsByTagName(DISK_TAG);
			for(int i=0; i<diskNodes.getLength(); i++) {
				VolumeState state = VolumeState.AVAILABLE;
				String volumeId = null;

				NodeList diskChildren = diskNodes.item(i).getChildNodes();
				for (int j=0; j<diskChildren.getLength(); j++) {
					Node diskChild = diskChildren.item(j);
					if (diskChild.getNodeName().equals("Index")) {
						String diskIndex = diskChild.getTextContent();
						volumeId = vmId + ":" + diskIndex;
						break;
					}
				}
				disksStatus.add(new ResourceStatus(volumeId, state));
			}
		}
		return disksStatus;
	}

	@Override
	public Volume getVolume(String volumeId) throws InternalException, CloudException {
		Volume volume = null;
		try {
			if (volumeId.contains(":")) {
				volume = getAttachedDisk(volumeId);
			}
			else {
				volume = getDetachedDisk(volumeId);
			}
		}
		catch (CloudException e) {
			logger.warn("Failed to get volume " + volumeId + ": " + e);
		}
		catch (InternalException e) {
			logger.warn("Failed to get volume " + volumeId + ": " + e);
		}
		return volume;
	}
	
	/**
     * Identifies to what degree volume products are supported/required in this cloud. If the support
     * level is {@link Requirement#NONE}, then {@link #listVolumeProducts()} should return an empty list.
     * @return whether or not specifying a volume product is required to create a volume
     * @throws InternalException an error occurred in the Dasein Cloud implementation determining the support level
     * @throws CloudException an error occurred with the cloud provider determining the support level
     */
	@Override
	public Requirement getVolumeProductRequirement() throws InternalException, CloudException {
		 return Requirement.NONE;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}
	
	/**
     * Indicates that a volume size is not necessary (and ultimately ignored) during the volume creation process
     * because the volume size is determined by the selected volume product.
     * @return true if the volume size is determined by the product choice
     * @throws InternalException an error occurred within Dasein Cloud determining this feature
     * @throws CloudException an error occurred identifying this requirement from the cloud provider
     */
	@Override
	public boolean isVolumeSizeDeterminedByProduct() throws InternalException, CloudException {
		return true;
	}

	private Collection<Volume> listDetachedDisks() throws InternalException, CloudException {
		Collection<Volume> volumes = new ArrayList<Volume>();
		String regionId = provider.getContext().getRegionId();
		Collection<DataCenter> dcs = provider.getDataCenterServices().listDataCenters(regionId);
		for (DataCenter dc : dcs) {
			String url = "/" + DETACHED_DISKS + "/" + EnvironmentsAndComputePools.COMPUTE_POOLS + "/" +  dc.getProviderDataCenterId();
			TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
			Document doc = method.invoke();
			if (doc != null) {
				NodeList detachedDiskNodes = doc.getElementsByTagName(DETACHED_DISK_TAG);
				for (int i=0; i<detachedDiskNodes.getLength(); i++) {
					Node detachedDiskNode = detachedDiskNodes.item(i);
					Volume volume = new Volume();
					volume.setProviderDataCenterId(dc.getProviderDataCenterId());
					volume.setProviderRegionId(regionId);
					volume.setProviderVirtualMachineId(null);
					volume.setProviderSnapshotId(null);
					String id = Terremark.hrefToId(detachedDiskNode.getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
					volume.setProviderVolumeId(id);
					volume.setName(detachedDiskNode.getAttributes().getNamedItem(Terremark.NAME).getNodeValue());
					if (volume.getName().contains("-")) {
						String timestamp = volume.getName().split("-")[0];
						try {
							volume.setCreationTimestamp(Long.parseLong(timestamp));
						}
						catch (NumberFormatException e) {
							logger.info("Failed to set volume creation timestamp.");
						}
					}
					NodeList diskChildren = detachedDiskNode.getChildNodes();
					for (int j=0; j<diskChildren.getLength(); j++) {
						Node diskChild = diskChildren.item(j);
						if (diskChild.getNodeName().equals("Size")) {
							String unit = diskChild.getFirstChild().getTextContent();
							String diskSize = diskChild.getLastChild().getTextContent();
							int sizeInGb = 0;
							if (unit.equalsIgnoreCase("GB")) {
								sizeInGb = Integer.parseInt(diskSize);
							}
							else if (unit.equalsIgnoreCase("MB")) {
								sizeInGb = (Integer.parseInt(diskSize) / 1024);
							}
							else if (unit.equalsIgnoreCase("TB")) {
								sizeInGb = (Integer.parseInt(diskSize) * 1024);
							}
							volume.setSize(new Storage<Gigabyte>(sizeInGb, Storage.GIGABYTE));
						}
						else if (diskChild.getNodeName().equals("Status")) {
							if (diskChild.getTextContent().equals("Available")) {
								volume.setCurrentState(VolumeState.AVAILABLE);
							}
							else {
								volume.setCurrentState(VolumeState.PENDING);
							}
						}
						else if (diskChild.getNodeName().equals("Type")) {
							boolean rootVolume = diskChild.getNodeValue().equalsIgnoreCase("System");
							volume.setRootVolume(rootVolume);
							if (rootVolume) {
								//Fix this, don't get this from doc
								String os = doc.getElementsByTagName("OperatingSystem").item(0).getAttributes().getNamedItem(Terremark.NAME).getNodeValue();
								volume.setGuestOperatingSystem(Platform.guess(os));
							}
						}
					}
					volumes.add(volume);
				}
			}
		}
		return volumes;
	}

	private Collection<ResourceStatus> listDetachedDisksStatus() throws InternalException, CloudException {
		Collection<ResourceStatus> volumeStatus = new ArrayList<ResourceStatus>();
		String regionId = provider.getContext().getRegionId();
		Collection<DataCenter> dcs = provider.getDataCenterServices().listDataCenters(regionId);
		for (DataCenter dc : dcs) {
			String url = "/" + DETACHED_DISKS + "/" + EnvironmentsAndComputePools.COMPUTE_POOLS + "/" +  dc.getProviderDataCenterId();
			TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
			Document doc = method.invoke();
			if (doc != null) {
				NodeList detachedDiskNodes = doc.getElementsByTagName(DETACHED_DISK_TAG);
				for (int i=0; i<detachedDiskNodes.getLength(); i++) {
					Node detachedDiskNode = detachedDiskNodes.item(i);
					VolumeState state = VolumeState.PENDING;
					String id = Terremark.hrefToId(detachedDiskNode.getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
					NodeList diskChildren = detachedDiskNode.getChildNodes();
					for (int j=0; j<diskChildren.getLength(); j++) {
						Node diskChild = diskChildren.item(j);
						if (diskChild.getNodeName().equals("Status")) {
							if (diskChild.getTextContent().equals("Available")) {
								state = VolumeState.AVAILABLE;
							}
							else {
								state = VolumeState.PENDING;
							}
						}
					}
					volumeStatus.add(new ResourceStatus(id, state));
				}
			}
		}
		return volumeStatus;
	}

	@Override
	public Iterable<String> listPossibleDeviceIds(Platform platform) throws InternalException, CloudException {
		ArrayList<String> list = new ArrayList<String>();

		if( platform.isWindows() ) {
			list.add("xvdf");
			list.add("xvdg");
			list.add("xvdh");
			list.add("xvdi");
			list.add("xvdj");
			list.add("xvdk");
			list.add("xvdl");
			list.add("xvdm");
			list.add("xvdn");
			list.add("xvdo");
			list.add("xvdp");
			list.add("xvdq");
			list.add("xvdr");
			list.add("xvds");
			list.add("xvdt");
		}
		else {
			list.add("/dev/sdf");
			list.add("/dev/sdg");
			list.add("/dev/sdh");
			list.add("/dev/sdi");
			list.add("/dev/sdj");
			list.add("/dev/sdk");
			list.add("/dev/sdl");
			list.add("/dev/sdm");
			list.add("/dev/sdn");
			list.add("/dev/sdo");
			list.add("/dev/sdp");
			list.add("/dev/sdq");
			list.add("/dev/sdr");
			list.add("/dev/sds");
			list.add("/dev/sdt");
		}
		return list;
	}

    /**
     * Describes the formats supported in this cloud.
     * @return a list of supported formats
     * @throws InternalException an error occurred in the Dasein Cloud implementation while assembling the list
     * @throws CloudException an error occurred fetching a list from the cloud provider
     */
	@Override
	public Iterable<VolumeFormat> listSupportedFormats() throws InternalException, CloudException {
		return Collections.singletonList(VolumeFormat.BLOCK);
	}

    private Collection<Volume> listVmDisks() throws InternalException, CloudException {
		logger.trace("enter - listVmDisks()");
		Collection<Volume> volumes = new ArrayList<Volume>();
		ProviderContext ctx = provider.getContext();
		if( ctx == null ) {
			throw new CloudException("No context was established for this request");
		}
		String regionId = ctx.getRegionId();
		Document environmentDoc = provider.getDataCenterServices().getEnvironmentById(regionId);
		NodeList vmNodes = environmentDoc.getElementsByTagName(VMSupport.VIRTUAL_MACHINE_TAG);
		logger.trace("listVmDisks(): Found " + vmNodes.getLength() + " VMs in region");
		for (int i=0; i < vmNodes.getLength(); i++){
			String vmHref = vmNodes.item(i).getAttributes().item(0).getNodeValue();
			Collection<Volume> disks = getVirtualMachineDisks(Terremark.hrefToId(vmHref));
			volumes.addAll(disks);
		}
		logger.trace("exit - listVmDisks()");
		return volumes;
	}

    private Collection<ResourceStatus> listVmDisksStatus() throws InternalException, CloudException {
		logger.trace("enter - listVmDisks()");
		Collection<ResourceStatus> volumesStatus = new ArrayList<ResourceStatus>();
		ProviderContext ctx = provider.getContext();
		if( ctx == null ) {
			throw new CloudException("No context was established for this request");
		}
		String regionId = ctx.getRegionId();
		Document environmentDoc = provider.getDataCenterServices().getEnvironmentById(regionId);
		NodeList vmNodes = environmentDoc.getElementsByTagName(VMSupport.VIRTUAL_MACHINE_TAG);
		logger.trace("listVmDisks(): Found " + vmNodes.getLength() + " VMs in region");
		for (int i=0; i < vmNodes.getLength(); i++){
			String vmHref = vmNodes.item(i).getAttributes().item(0).getNodeValue();
			Collection<ResourceStatus> disks = getVirtualMachineDisksStatus(Terremark.hrefToId(vmHref));
			volumesStatus.addAll(disks);
		}
		logger.trace("exit - listVmDisks()");
		return volumesStatus;
	}

    /**
     * Lists the set of volume products that may be used in provisioning a block storage volume. Because not all clouds
     * support the concept of volume products (as indicated by {@link #getVolumeProductRequirement()}, this method should
     * return an empty list in such clouds.
     * @return the list of products that may be used to provision volumes
     * @throws InternalException an error occurred within the Dasein Cloud implementation assembling the list
     * @throws CloudException an error occurred fetching the product list from the cloud provider
     */
	@Override
	public Iterable<VolumeProduct> listVolumeProducts() throws InternalException, CloudException {
		 return Collections.emptyList();
	}

    @Override
	public Iterable<Volume> listVolumes() throws InternalException, CloudException {
		Collection<Volume> volumes = new ArrayList<Volume>();
		volumes.addAll(listDetachedDisks());
		volumes.addAll(listVmDisks());
		return volumes;
	}

    /**
     * Lists the status for all volumes in the current region.
     * @return the status for all volumes in the current region
     * @throws InternalException an error occurred within the Dasein Cloud implementation
     * @throws CloudException an error occurred with the cloud provider
     */
	@Override
	public Iterable<ResourceStatus> listVolumeStatus() throws InternalException, CloudException {
		Collection<ResourceStatus> volumesStatus = new ArrayList<ResourceStatus>();
		volumesStatus.addAll(listDetachedDisksStatus());
		volumesStatus.addAll(listVmDisksStatus());
		return volumesStatus;
	}

    @Override
	public String[] mapServiceAction(ServiceAction arg0) {
		return new String[0];
	}

    @Override
	public void remove(String volumeId) throws InternalException, CloudException {
		if (volumeId.contains(":")) {
			throw new InternalException("You cannot delete an attached volume.");
		}

		String url = "/" + DETACHED_DISKS + "/" + volumeId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.DELETE, url, null, "");
		Document doc = method.invoke();
		if (doc != null) {
			String taskHref = Terremark.getTaskHref(doc, DELETE_DISK_OPERATION);
			provider.waitForTask(taskHref, DEFAULT_SLEEP, DEFAULT_TIMEOUT);
		}
	}
}