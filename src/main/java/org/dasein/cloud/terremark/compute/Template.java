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

package org.dasein.cloud.terremark.compute;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import org.dasein.cloud.AsynchronousTask;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.AbstractImageSupport;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.ImageCreateOptions;
import org.dasein.cloud.compute.ImageFilterOptions;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageFormat;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.MachineImageType;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.terremark.EnvironmentsAndComputePools;
import org.dasein.cloud.terremark.Terremark;
import org.dasein.cloud.terremark.TerremarkException;
import org.dasein.cloud.terremark.TerremarkMethod;
import org.dasein.cloud.terremark.TerremarkMethod.HttpMethodName;
import org.dasein.cloud.util.APITrace;
import org.dasein.util.CalendarWrapper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Template extends AbstractImageSupport {

	public enum ImageType {
		TEMPLATE, CATALOG_ENTRY;
	}

	// API Calls
	public final static String TEMPLATES            = "templates";
	public final static String CATALOG              = "catalog";
	public final static String CONFIGURATION        = "configuration";

	// Response Tags
	public final static String TEMPLATE_TAG         = "Template";
	public final static String CATALOG_ENTRY_TAG    = "CatalogEntry";
	public final static String NETWORK_MAPPING_TAG  = "NetworkMapping";

	// Types
	public final static String TEMPLATE_TYPE        = "application/vnd.tmrk.cloud.template";

	// Operation Names
	public final static String CREATE_CATALOG_OPERATION = "Create Catalog Item";

	public final static String NETWORK_MAPPING_NAME = "NetworkMappingName";

	// Default Task Wait Times
	public final static long DEFAULT_SLEEP             = CalendarWrapper.SECOND * 30;
	public final static long DEFAULT_TIMEOUT           = CalendarWrapper.HOUR * 6;

	static Logger logger = Terremark.getLogger(Template.class);

	private Terremark provider;

	Template(Terremark provider) {
        super(provider);
		this.provider = provider;
	}

    @Override
	protected @Nonnull MachineImage capture(@Nonnull ImageCreateOptions options, @Nullable AsynchronousTask<MachineImage> task) throws CloudException, InternalException {
		APITrace.begin(provider, "captureImage");
		try {
			if( task != null ) {
				task.setStartTime(System.currentTimeMillis());
			}
			VirtualMachine vm = null;
			String vmId = options.getVirtualMachineId();
			String name = options.getName();

			long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 30L);

			while( timeout > System.currentTimeMillis() ) {
				try {
					vm = provider.getComputeServices().getVirtualMachineSupport().getVirtualMachine(vmId);
					if( vm == null ) {
						break;
					}
					if( VmState.STOPPED.equals(vm.getCurrentState()) ) {
						break;
					}
					else if ( VmState.RUNNING.equals(vm.getCurrentState()) ) {
						provider.getComputeServices().getVirtualMachineSupport().stop(vm.getProviderVirtualMachineId());
					}
				}
				catch( Throwable error ) {
					logger.warn(error.getMessage());
				}
				try { Thread.sleep(15000L); }
				catch( InterruptedException ignore ) { }
			}
			if( vm == null ) {
				throw new CloudException("No such virtual machine: " + vmId);
			}

			String url = "/" + VMSupport.VIRTUAL_MACHINES + "/" + vmId + "/" + Terremark.ACTION + "/export";
			String body = "";
			MachineImage image = null;

			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder;
			try {
				docBuilder = docFactory.newDocumentBuilder();
				Document doc = docBuilder.newDocument();

				Element rootElement = doc.createElement("ExportVirtualMachineRequest");

				Element catalogNameElement = doc.createElement("CatalogName");
				catalogNameElement.appendChild(doc.createTextNode(name));
				rootElement.appendChild(catalogNameElement);			

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
			Document doc;
			try {
				doc = method.invoke();
			}
			catch( CloudException e ) {
				logger.error(e.getMessage());
				throw new CloudException(e);
			}
			String catalogEntryId = Terremark.hrefToId(doc.getElementsByTagName(CATALOG_ENTRY_TAG).item(0).getAttributes().getNamedItem(Terremark.HREF).getNodeValue());
			String taskHref = Terremark.getTaskHref(doc, CREATE_CATALOG_OPERATION);
			provider.waitForTask(taskHref, DEFAULT_SLEEP, DEFAULT_TIMEOUT);
			String imageId = catalogEntryId + "::" + ImageType.CATALOG_ENTRY.name();
			image = getImage(imageId);
			if (image == null) {
				throw new CloudException("No image exists for " + imageId + " as created during the capture process");
			}
			return image;

		}
		finally {
			APITrace.end();
		}
	}

	private MachineImage catalogEntryToMachineImage(Node catalogEntryNode) throws CloudException, InternalException {
		logger.trace("enter - catalogEntryToMachineImage()");
		MachineImage catalogEntry = new MachineImage(); 
		NamedNodeMap catalogEntryAtrs = catalogEntryNode.getAttributes();
		String imageId = Terremark.hrefToId(catalogEntryAtrs.getNamedItem(Terremark.HREF).getNodeValue());
		String name = catalogEntryAtrs.getNamedItem(Terremark.NAME).getNodeValue();
		catalogEntry.setProviderMachineImageId(imageId + "::" + ImageType.CATALOG_ENTRY.name());
		catalogEntry.setName(name);
		logger.debug("catalogEntryToMachineImage() - Image ID = " + catalogEntry.getProviderMachineImageId() + " Name " + catalogEntry.getName());
		catalogEntry.setProviderOwnerId(provider.getContext().getAccountNumber());
		catalogEntry.setProviderRegionId(provider.getContext().getRegionId());
		catalogEntry.setDescription(name);
		catalogEntry.setType(MachineImageType.VOLUME);
		catalogEntry.setImageClass(ImageClass.MACHINE);
		NodeList ceChildren = catalogEntryNode.getChildNodes();
		for (int i=0; i<ceChildren.getLength(); i++) {
			Node ceChild = ceChildren.item(i);
			if (ceChild.getNodeName().equals("Status")) {
				String status = ceChild.getTextContent();
				if (status.equalsIgnoreCase("Completed")) {
					catalogEntry.setCurrentState(MachineImageState.ACTIVE);
				}
				else if (status.equalsIgnoreCase("Failed")) {
					catalogEntry.setCurrentState(MachineImageState.DELETED);
				}
				else {
					catalogEntry.setCurrentState(MachineImageState.PENDING);
				}
			}
			else if (ceChild.getNodeName().equals("CatalogType")) {
				String type = ceChild.getTextContent();
				Tag networkMappingTag = new Tag();
				networkMappingTag.setKey("CatalogType");
				networkMappingTag.setValue(type);
				logger.debug("Adding tag: " + networkMappingTag);
				catalogEntry.addTag(networkMappingTag);
			}
		}
		logger.debug("catalogEntryToMachineImage() - Getting catalog configuration for " + catalogEntry);
		String url = "/" + Terremark.ADMIN + "/" + CATALOG + "/" +  imageId + "/" + CONFIGURATION;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null) {

			String os = doc.getElementsByTagName("OperatingSystem").item(0).getTextContent();
			String osDescription = name + " " + os;
			catalogEntry.setPlatform(Platform.guess(osDescription));
			logger.debug("catalogEntryToMachineImage(): ID = " + catalogEntry.getProviderMachineImageId() + " OS = " + os + " Platform = " + catalogEntry.getPlatform());
			if( osDescription == null || osDescription.indexOf("32 bit") != -1 || osDescription.indexOf("32-bit") != -1 ) {
				catalogEntry.setArchitecture(Architecture.I32);            
			}
			else if( osDescription.indexOf("64 bit") != -1 || osDescription.indexOf("64-bit") != -1 ) {
				catalogEntry.setArchitecture(Architecture.I64);
			}
			else {
				catalogEntry.setArchitecture(Architecture.I64);
			}
			NodeList networkMappingElements = doc.getElementsByTagName(NETWORK_MAPPING_TAG);
			Tag networkMappingCountTag = new Tag();
			networkMappingCountTag.setKey("NetworkMappingCount");
			networkMappingCountTag.setValue(String.valueOf(networkMappingElements.getLength()));
			logger.debug("Adding tag: " + networkMappingCountTag);
			catalogEntry.addTag(networkMappingCountTag);
			for (int i=0; i<networkMappingElements.getLength(); i++) {
				String networkMappingName = networkMappingElements.item(i).getFirstChild().getTextContent();
				Tag networkMappingTag = new Tag();
				networkMappingTag.setKey(NETWORK_MAPPING_NAME + "-" + i);
				networkMappingTag.setValue(networkMappingName);
				logger.debug("Adding tag: " + networkMappingTag);
				catalogEntry.addTag(networkMappingTag);
			}
			catalogEntry.setSoftware("");
		}
		logger.trace("exit - catalogEntryToMachineImage()");
		return catalogEntry;
	}
	
	private ResourceStatus catalogEntryToMachineImageStatus(Node catalogEntryNode) throws CloudException, InternalException {
		logger.trace("enter - catalogEntryToMachineImageStatus()");
		String resourceId;
		MachineImageState resourceState = MachineImageState.PENDING;
		NamedNodeMap catalogEntryAtrs = catalogEntryNode.getAttributes();
		String imageId = Terremark.hrefToId(catalogEntryAtrs.getNamedItem(Terremark.HREF).getNodeValue());
		resourceId = imageId + "::" + ImageType.CATALOG_ENTRY.name();

		NodeList ceChildren = catalogEntryNode.getChildNodes();
		for (int i=0; i<ceChildren.getLength(); i++) {
			Node ceChild = ceChildren.item(i);
			if (ceChild.getNodeName().equals("Status")) {
				String status = ceChild.getTextContent();
				if (status.equalsIgnoreCase("Completed")) {
					resourceState = MachineImageState.ACTIVE;
				}
				else if (status.equalsIgnoreCase("Failed")) {
					resourceState = MachineImageState.DELETED;
				}
				else {
					resourceState = MachineImageState.PENDING;
				}
				break;
			}
		}
		logger.trace("exit - catalogEntryToMachineImageStatus()");
		return new ResourceStatus(resourceId, resourceState);
	}

	/**
	 * Provides access to the current state of the specified image.
	 * @param providerImageId the cloud provider ID uniquely identifying the desired image
	 * @return the image matching the desired ID if it exists
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException a local error occurred in the Dasein Cloud implementation
	 */
	@Override
	public @Nullable MachineImage getImage(String providerImageId) throws CloudException, InternalException {
		if (providerImageId == null){
			logger.debug("getImage(): The image id is null");
			return null;
		}
		MachineImage template = null;
		String imageId = null;
		String dataCenterId = null;
		String imageType = null;

		if (providerImageId.contains(":")){
			String[] imageIds = providerImageId.split(":");
			imageId = imageIds[0];
			dataCenterId = imageIds[1];
			imageType = imageIds[2];
		}
		else {
			logger.error("getImage(): Invalid machineImageId " + providerImageId + ". Must be of the form: <templateId>:<computePoolId>:<image_type>");
			return null;
		}
		if (imageType.equalsIgnoreCase(ImageType.TEMPLATE.name())) {
			String url = "/" + TEMPLATES + "/" + imageId + "/" + EnvironmentsAndComputePools.COMPUTE_POOLS + "/" + dataCenterId;
			TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
			Document doc = null;
			try {
				doc = method.invoke();
			} catch (TerremarkException e) {
				logger.warn("getImage(): Failed to get template " + providerImageId);
			} catch (CloudException e) {
				logger.warn("getImage(): Failed to get template " + providerImageId);
			} catch (InternalException e) {
				logger.warn("getImage(): Failed to get template " + providerImageId);
			}
			if (doc != null){
				template = templateToMachineImage(doc);
			}
		}
		else if (imageType.equalsIgnoreCase(ImageType.CATALOG_ENTRY.name())) {
			String url = "/" + Terremark.ADMIN + "/" + CATALOG + "/" + imageId;
			TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
			Document doc = null;
			try {
				doc = method.invoke();
			} catch (TerremarkException e) {
				logger.warn("getImage(): Failed to get template " + providerImageId);
			} catch (CloudException e) {
				logger.warn("getImage(): Failed to get template " + providerImageId);
			} catch (InternalException e) {
				logger.warn("getImage(): Failed to get template " + providerImageId);
			}
			if (doc != null){
				Node catalogEntryNode = doc.getElementsByTagName(CATALOG_ENTRY_TAG).item(0);
				template = catalogEntryToMachineImage(catalogEntryNode);
			}
		}

		return template;
	}

    /**
     * Provides the cloud provider specific term for a custom image of the specified image class.
     * @param locale the locale for which the term should be translated
     * @param cls the image class for the desired type
     * @return the term used by the provider to describe a custom image
     */
	@Override
	public String getProviderTermForCustomImage(Locale locale, ImageClass cls) {
		return "catalog entry";
	}

	/**
	 * Provides the cloud provider specific term for a machine image.
	 * @param locale the locale for which the term should be translated
	 * @return the term used by the provider to describe a machine image
	 * @deprecated Use {@link #getProviderTermForImage(Locale, ImageClass)}
	 */
	@Override
	public @Nonnull String getProviderTermForImage(@Nonnull Locale locale) {
		return "template/catalog entry";
	}

    /**
     * Provides the cloud provider specific term for a public image of the specified image class.
     * @param cls the image class for the desired type
     * @return the term used by the provider to describe a public image
     */
	@Override
	public String getProviderTermForImage(Locale locale, ImageClass cls) {
		return "template";
	}

	/**
	 * Indicates whether or not a public image library of {@link ImageClass#MACHINE} is supported.
	 * @return true if there is a public library
	 * @deprecated Use {@link #supportsPublicLibrary(ImageClass)}
	 */
	@Override
	public boolean hasPublicLibrary() {
		return true;
	}

	/**
	 * Identifies if you can bundle a virtual machine to cloud storage from within the VM. If you must bundle local to the
	 * virtual machine (as with AWS), this should return {@link Requirement#REQUIRED}. If you must be external, this
	 * should return {@link Requirement#NONE}. If both external and local are supported, this method
	 * should return {@link Requirement#OPTIONAL}.
	 * @return how local bundling is supported
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException an error occurred within the Dasein cloud implementation
	 */
	@Override
	public Requirement identifyLocalBundlingRequirement() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	/**
	 * Indicates whether or not the specified image is shared publicly. It should return false when public image sharing
	 * simply isn't supported by the underlying cloud.
	 * @param machineImageId the machine image being checked for public status
	 * @return true if the target machine image is shared with the general public
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException an error occurred within the Dasein cloud implementation
	 */
	@Override
	public boolean isImageSharedWithPublic(String machineImageId) throws CloudException, InternalException {
		String imageType = null;
		boolean isPublic = true;
		if (machineImageId.contains(":")){
			String[] imageIds = machineImageId.split(":");
			imageType = imageIds[2];
		}
		else {
			logger.error("getMachineImage(): Invalid machineImageId " + machineImageId + ". Must be of the form: <templateId>:<computePoolId>:<image_type>");
		}
		if (imageType.equalsIgnoreCase(ImageType.CATALOG_ENTRY.name())) {
			isPublic = false;
		}
		return isPublic;
	}

	/**
	 * Indicates whether or not this account has access to any image services that might exist in this cloud.
	 * @return true if the account is subscribed
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException an error occurred within the Dasein cloud implementation
	 */
	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}
	
	private Collection<MachineImage> listCatalogItems() throws CloudException, InternalException {
		logger.trace("enter - listCatalogItems()");
		ArrayList<MachineImage> images = new ArrayList<MachineImage>();
		ProviderContext ctx = provider.getContext();
		String locationId = provider.getDataCenterServices().getRegionLocation(ctx.getRegionId());
		String url = "/" + Terremark.ADMIN + "/" + CATALOG + "/" + Terremark.ORGANZIATIONS + "/" + provider.getOrganization().getId() + "/locations/" + locationId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null) {
			NodeList catalogEntries = doc.getElementsByTagName(CATALOG_ENTRY_TAG);
			logger.debug("listCatalogItems() - Found " + catalogEntries.getLength() + " catalog entries.");
			for (int i=0; i<catalogEntries.getLength(); i++) {
				MachineImage image = null;
				try {
					image = catalogEntryToMachineImage(catalogEntries.item(i));
				}
				catch (CloudException e) {
					logger.warn("listCatalogItems(): Skipping catalog item: " + e.getMessage());
					if (logger.isDebugEnabled()) {
						e.printStackTrace();
					}
				}
				catch (InternalException e) {
					logger.warn("listCatalogItems(): Skipping catalog item: " + e.getMessage());
					if (logger.isDebugEnabled()) {
						e.printStackTrace();
					}
				}
				catch (RuntimeException e) {
					logger.warn("listCatalogItems(): Skipping catalog item: " + e.getMessage());
					if (logger.isDebugEnabled()) {
						e.printStackTrace();
					}
				}
				if (image != null) {
					images.add(image);
				}
			}
		}
		logger.trace("exit - listCatalogItems()");
		return images;
	}

	private Collection<ResourceStatus> listCatalogItemsStatus() throws CloudException, InternalException {
		logger.trace("enter - listCatalogItemsStatus()");
		ArrayList<ResourceStatus> imagesStatus = new ArrayList<ResourceStatus>();
		ProviderContext ctx = provider.getContext();
		String locationId = provider.getDataCenterServices().getRegionLocation(ctx.getRegionId());
		String url = "/" + Terremark.ADMIN + "/" + CATALOG + "/" + Terremark.ORGANZIATIONS + "/" + provider.getOrganization().getId() + "/locations/" + locationId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null) {
			NodeList catalogEntries = doc.getElementsByTagName(CATALOG_ENTRY_TAG);
			logger.debug("listCatalogItemsStatus() - Found " + catalogEntries.getLength() + " catalog entries.");
			for (int i=0; i<catalogEntries.getLength(); i++) {
				ResourceStatus imageState = null;
				try {
					imageState = catalogEntryToMachineImageStatus(catalogEntries.item(i));
				}
				catch (CloudException e) {
					logger.warn("listCatalogItemsStatus(): Skipping catalog item: " + e.getMessage());
					if (logger.isDebugEnabled()) {
						e.printStackTrace();
					}
				}
				catch (InternalException e) {
					logger.warn("listCatalogItemsStatus(): Skipping catalog item: " + e.getMessage());
					if (logger.isDebugEnabled()) {
						e.printStackTrace();
					}
				}
				catch (RuntimeException e) {
					logger.warn("listCatalogItemsStatus(): Skipping catalog item: " + e.getMessage());
					if (logger.isDebugEnabled()) {
						e.printStackTrace();
					}
				}
				if (imageState != null) {
					imagesStatus.add(imageState);
				}
			}
		}
		logger.trace("exit - listCatalogItemsStatus()");
		return imagesStatus;
	}


    @Nonnull
    @Override
    public Iterable<MachineImage> listImages(@Nullable ImageFilterOptions options) throws CloudException, InternalException {
        logger.trace("enter - listImages()");
		Collection<MachineImage> images = new ArrayList<MachineImage>();

        for( MachineImage img: listCatalogItems() ) {
            if( options == null || options.matches(img) ) {
                images.add(img);
            }
        }
		logger.trace("exit - listImages()");
		return images;
	}

	/**
	 * Lists the current status for all images in my library. The images returned should be the same list provided by
	 * {@link #listImages(ImageClass)}, except that this method returns a list of {@link ResourceStatus} objects.
	 * @param cls the image class of the target images
	 * @return a list of status objects for the images in the library
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException a local error occurred in the Dasein Cloud implementation
	 */
	@Override
	public Iterable<ResourceStatus> listImageStatus(ImageClass cls) throws CloudException, InternalException {
		logger.trace("enter - listImageStatus()");
		Collection<ResourceStatus> imagesStatus = new ArrayList<ResourceStatus>();
		imagesStatus.addAll(listCatalogItemsStatus());
		logger.trace("exit - listImageStatus()");
		return imagesStatus;
	}

	/**
	 * Lists all machine image formats for any uploading/registering of machine images that might be supported.
	 * If uploading/registering is not supported, this method will return any empty set.
	 * @return the list of supported formats you can upload to the cloud
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException a local error occurred in the Dasein Cloud implementation
	 */
	@Override
	public Iterable<MachineImageFormat> listSupportedFormats() throws CloudException, InternalException {
		Collection<MachineImageFormat> formats = new ArrayList<MachineImageFormat>();
		formats.add(MachineImageFormat.OVF);
		formats.add(MachineImageFormat.VMDK);
		return formats;
	}

	/**
	 * Lists all machine image formats that can be used in bundling a virtual machine. This should be a sub-set
	 * of formats specified in {@link #listSupportedFormats()} as you need to be able to register images of this format.
	 * If bundling is not supported, this method will return an empty list.
	 * @return the list of supported formats in which you can bundle a virtual machine
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException a local error occurred in the Dasein Cloud implementation
	 */
	@Override
	public Iterable<MachineImageFormat> listSupportedFormatsForBundling() throws CloudException, InternalException {
		Collection<MachineImageFormat> formats = new ArrayList<MachineImageFormat>();
		formats.add(MachineImageFormat.OVF);
		formats.add(MachineImageFormat.VMDK);
		return formats;
	}

	/**
	 * Lists the image classes supported in this cloud.
	 * @return the supported image classes
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException a local error occurred in the Dasein Cloud implementation
	 */
	@Override
	public Iterable<ImageClass> listSupportedImageClasses() throws CloudException, InternalException {
		Collection<ImageClass> formats = new ArrayList<ImageClass>();
		formats.add(ImageClass.MACHINE);
		return formats;
	}

	/**
	 * Enumerates the types of images supported in this cloud.
	 * @return the list of supported image types
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException a local error occurred in the Dasein Cloud implementation
	 */
	@Override
	public Iterable<MachineImageType> listSupportedImageTypes() throws CloudException, InternalException {
		Collection<MachineImageType> formats = new ArrayList<MachineImageType>();
		formats.add(MachineImageType.VOLUME);
		return formats;
	}


	private Collection<MachineImage> listTemplates() throws InternalException, CloudException {
		logger.trace("enter - listTemplates()");
		ArrayList<MachineImage> images = new ArrayList<MachineImage>();
		ArrayList<String> templateIds = new ArrayList<String>();
		Collection<DataCenter> dcs = provider.getDataCenterServices().listDataCenters(provider.getContext().getRegionId());
		logger.debug("listTemplates(): dcs size = " + dcs.size());
		for (DataCenter dc : dcs){
			String url = "/" + TEMPLATES + "/" + EnvironmentsAndComputePools.COMPUTE_POOLS + "/" + dc.getProviderDataCenterId();
			TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
			Document doc = method.invoke();
			NodeList templates = doc.getElementsByTagName(TEMPLATE_TAG);
			logger.debug("listTemplates(): templates length = " + templates.getLength());
			for (int i = 0; i<templates.getLength(); i++){
				String templateHref = templates.item(i).getAttributes().getNamedItem(Terremark.HREF).getNodeValue();
				templateIds.add(Terremark.getTemplateIdFromHref(templateHref));
			}
		}
		logger.debug("listTemplates(): templateIds size = " + templateIds.size());
		for (String templateId : templateIds){
			MachineImage image = getImage(templateId);
			if (image != null){
				logger.debug("listTemplates(): adding image = " + image);
				images.add(image);
			}
			else {
				logger.debug("listTemplates(): image is null.");
			}
		}
		logger.trace("exit - listTemplates()");
		return images;
	}

	/**
	 * Registers the bundled virtual machine stored in object storage as a machine image in the cloud.
	 * @param options the options used in registering the machine image
	 * @return a newly created machine image
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException a local error occurred in the Dasein Cloud implementation
	 * @throws OperationNotSupportedException the cloud does not support registering image from object store bundles
	 */
	@Override
	public MachineImage registerImageBundle(ImageCreateOptions options) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Not yet supported.");
		//TODO: Implement. The implementation should expect a zip file with an OVF and a VMDK file and unzip it and load both parts.
	}

	/**
	 * Permanently removes all traces of the target image. This method should remove both the image record in the cloud
	 * and any cloud storage location in which the image resides for staging.
	 * @param machineImageId the unique ID of the image to be removed
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException a local error occurred in the Dasein Cloud implementation
	 */
	@Override
	public void remove(@Nonnull String machineImageId) throws CloudException, InternalException {
		String imageId = null;
		String imageType = null;

		if (machineImageId.contains(":")){
			String[] imageIds = machineImageId.split(":");
			imageId = imageIds[0];
			imageType = imageIds[2];
		}
		else {
			throw new InternalException("getMachineImage(): Invalid machineImageId " + machineImageId + ". Must be of the form: <templateId>:<computePoolId>:<imageType>");
		}

		if (imageType.equals(ImageType.TEMPLATE.name())) {
			throw new CloudException("Deleting template type images is not supported");
		}

		String url = "/" + Terremark.ADMIN + "/" + CATALOG + "/" + imageId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.DELETE, url, null, "");
		method.invoke();
	}

	  /**
	   * Permanently removes all traces of the target image. This method should remove both the image record in the cloud
	   * and any cloud storage location in which the image resides for staging.
	   * @param providerImageId the unique ID of the image to be removed
	   * @param checkState if the state of the machine image should be checked first
	   * @throws CloudException an error occurred with the cloud provider
	   * @throws InternalException a local error occurred in the Dasein Cloud implementation
	   */
	@Override
	public void remove(@Nonnull String providerImageId, boolean checkState) throws CloudException, InternalException {
	    if ( checkState ) {
	        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 30L);

	        while ( timeout > System.currentTimeMillis() ) {
	          try {
	            MachineImage img = getImage( providerImageId );

	            if ( img == null || MachineImageState.DELETED.equals( img.getCurrentState() ) ) {
	              return;
	            }
	            if ( MachineImageState.ACTIVE.equals( img.getCurrentState() ) ) {
	              break;
	            }
	          } catch ( Throwable ignore ) {
	            // ignore
	          }
	          try {
	            Thread.sleep( 15000L );
	          } catch ( InterruptedException ignore ) {
	          }
	        }
	      }

	      remove( providerImageId );
	}

	/**
	 * Searches images owned by the specified account number (if null, all visible images are searched). It will match against
	 * the specified parameters. Any null parameter does not constrain the search.
	 * @param accountNumber the account number to search against or null for searching all visible images
	 * @param keyword a keyword on which to search
	 * @param platform the platform to match
	 * @param architecture the architecture to match
	 * @param imageClasses the image classes to search for (null or empty list for all)
	 * @return all matching machine images
	 * @throws CloudException an error occurred with the cloud provider
	 * @throws InternalException a local error occurred in the Dasein Cloud implementation
	 */
	@Override
	public Iterable<MachineImage> searchImages(String accountNumber, String keyword, Platform platform, Architecture architecture, ImageClass... imageClasses) throws CloudException, InternalException {
		logger.trace("enter - searchImages(" + accountNumber + ", " + keyword + ", " + platform + ", " + architecture + ")");

		boolean machineClass = false;

		for (ImageClass imageClass : imageClasses) {
			if (imageClass == ImageClass.MACHINE) {
				machineClass = true;
				break;
			}
		}

		ArrayList<MachineImage> results = new ArrayList<MachineImage>();
		
		if (machineClass) {
			logger.debug("searchImages(): Calling list templates");
			Collection<MachineImage> images = new ArrayList<MachineImage>();
			if (accountNumber == null || accountNumber == provider.getContext().getAccountNumber()) {
				images.addAll(listCatalogItems());
			}
					
			for( MachineImage image : images ) {
				if( keyword != null ) {
					if( !image.getProviderMachineImageId().contains(keyword) && !image.getName().contains(keyword) && !image.getDescription().contains(keyword) ) {
						continue;
					}
				}
				if( platform != null ) {
					Platform p = image.getPlatform();

					if( !platform.equals(p) ) {
						if( platform.isWindows() ) {
							if( !p.isWindows() ) {
								continue;
							}
						}
						else if( platform.equals(Platform.UNIX) ){
							if( !p.isUnix() ) {
								continue;
							}
						}
						else {
							continue;
						}
					}
				}
				if (architecture != null) {
					if (architecture != image.getArchitecture()) {
						continue;
					}
				}
				results.add(image);
			}
		}
		logger.trace("exit - searchImages()");
		return results;
	}

	@Override
	public @Nonnull Iterable<MachineImage> searchPublicImages(@Nonnull ImageFilterOptions options) throws CloudException, InternalException {
		logger.trace("enter - searchPublicImages(" + options + ")");

		ArrayList<MachineImage> results = new ArrayList<MachineImage>();
		
        logger.debug("searchMachineImages(): Calling list templates");
        Iterable<MachineImage> images = listTemplates();
        for( MachineImage image : images ) {
            if( options.matches(image) ) {
                logger.debug("searchMachineImages(): Adding image " + image + " to results");
                results.add(image);
            }
        }
		logger.trace("exit - searchPublicImages()");
		return results;
	}

	/**
	 * Indicates whether or not the cloud supports the ability to capture custom images.
	 * @return true if you can capture custom images in this cloud
	 * @throws CloudException an error occurred with the cloud provider when checking this capability
	 * @throws InternalException an error occurred within the Dasein cloud implementation while check this capability
	 * @deprecated Use {@link #supportsImageCapture(MachineImageType)}
	 */
	@Override
	public boolean supportsCustomImages() {
		return true;
	}

	/**
	 * Supports the ability to directly upload an image into the cloud and have it registered as a new image. When
	 * doing this, you construct your create options using {@link ImageCreateOptions#getInstance(MachineImageFormat, InputStream, Platform, String, String)}.
	 * @return true if you can do direct uploads into the cloud
	 * @throws CloudException an error occurred with the cloud provider when checking this capability
	 * @throws InternalException an error occurred within the Dasein cloud implementation while check this capability
	 */
	@Override
	public boolean supportsDirectImageUpload() throws CloudException, InternalException {
		return true;
	}

	/**
	 * Indicates whether capturing a virtual machine as a custom image of type {@link ImageClass#MACHINE} is supported in
	 * this cloud.
	 * @param type the type of image you are checking for capture capabilities
	 * @return true if you can capture custom images in this cloud
	 * @throws CloudException an error occurred with the cloud provider when checking this capability
	 * @throws InternalException an error occurred within the Dasein cloud implementation while check this capability
	 */
	@Override
	public boolean supportsImageCapture(MachineImageType type) throws CloudException, InternalException {
		return true;
	}

	/**
	 * Indicates whether or not this cloud supports sharing images with specific accounts.
	 * @return true if you can share your images with another account
	 * @throws CloudException an error occurred with the cloud provider when checking this capability
	 * @throws InternalException an error occurred within the Dasein cloud implementation while check this capability
	 */
	@Override
	public boolean supportsImageSharing() {
		return false;
	}

	/**
	 * Indicates whether or not this cloud supports making images publicly available to all other accounts.
	 * @return true if you can share your images publicly
	 * @throws CloudException an error occurred with the cloud provider when checking this capability
	 * @throws InternalException an error occurred within the Dasein cloud implementation while check this capability
	 */
	@Override
	public boolean supportsImageSharingWithPublic() {
		return false;
	}

	@Override
	public boolean supportsPublicLibrary(ImageClass cls) throws CloudException, InternalException {
		return cls.equals(ImageClass.MACHINE);
	}

	private MachineImage templateToMachineImage(Document templateDoc) throws CloudException, InternalException {
		MachineImage template = new MachineImage(); 
		Node templateNode = templateDoc.getElementsByTagName(TEMPLATE_TAG).item(0);
		String href = templateNode.getAttributes().getNamedItem(Terremark.HREF).getTextContent();
		template.setProviderMachineImageId(Terremark.getTemplateIdFromHref(href));
		logger.debug("toMachineImage(): Image ID = " + template.getProviderMachineImageId());
		template.setName(templateNode.getAttributes().getNamedItem(Terremark.NAME).getTextContent());
		logger.debug("toMachineImage(): ID = " + template.getProviderMachineImageId() + " Image Name = " + template.getName());
		String description = templateDoc.getElementsByTagName("Description").item(0).getTextContent();
		template.setDescription(description);
		logger.debug("toMachineImage(): ID = " + template.getProviderMachineImageId() + " Image Description = " + template.getDescription());
		String osName = templateDoc.getElementsByTagName("OperatingSystem").item(0).getAttributes().getNamedItem(Terremark.NAME).getTextContent();
		String osDescription = osName + " " + description;
		template.setPlatform(Platform.guess(osDescription));
		logger.debug("toMachineImage(): ID = " + template.getProviderMachineImageId() + " OS = " + osName + " Platform = " + template.getPlatform());
		if( osDescription == null || osDescription.indexOf("32 bit") != -1 || osDescription.indexOf("32-bit") != -1 ) {
			template.setArchitecture(Architecture.I32);            
		}
		else if( osDescription.indexOf("64 bit") != -1 || osDescription.indexOf("64-bit") != -1 ) {
			template.setArchitecture(Architecture.I64);
		}
		else {
			template.setArchitecture(Architecture.I64);
		}
		logger.debug("toVirtualMachine(): ID = " + template.getProviderMachineImageId() + " OS = " + osName + " Architecture = " + template.getArchitecture());
		template.setProviderOwnerId(provider.getContext().getAccountNumber());
		logger.debug("toMachineImage(): ID = " + template.getProviderMachineImageId() + " Image Owner = " + template.getProviderOwnerId());
		template.setProviderRegionId(provider.getContext().getRegionId());
		logger.debug("toMachineImage(): ID = " + template.getProviderMachineImageId() + " Image Region = " + template.getProviderRegionId());

		template.setCurrentState(MachineImageState.ACTIVE);
		NodeList softwareNodes = templateDoc.getElementsByTagName("Software");
		String[] software = new String[softwareNodes.getLength()];
		for (int i=0; i<softwareNodes.getLength();i++){
			NodeList softwareChildren = softwareNodes.item(i).getChildNodes();
			for (int j=0; j<softwareChildren.getLength(); j++){
				if (softwareChildren.item(j).getNodeName().equals("Description")){
					software[i] = softwareChildren.item(j).getTextContent();
				}
			}
		}
		template.setSoftware(Arrays.toString(software));
		template.setType(MachineImageType.VOLUME);
		template.setImageClass(ImageClass.MACHINE);
		return template;
	}

}
