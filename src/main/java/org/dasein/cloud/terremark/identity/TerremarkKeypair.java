package org.dasein.cloud.terremark.identity;

import java.io.StringWriter;
import java.util.ArrayList;
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
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.identity.SSHKeypair;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.identity.ShellKeySupport;
import org.dasein.cloud.terremark.Terremark;
import org.dasein.cloud.terremark.TerremarkMethod;
import org.dasein.cloud.terremark.TerremarkMethod.HttpMethodName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TerremarkKeypair  implements ShellKeySupport {

	// API Calls
	public final static String SSH_KEYS        = "sshKeys";

	// Response Tags
	public final static String SSH_KEY_TAG     = "SshKey";

	// Types
	public final static String SSH_KEY_TYPE    = "application/vnd.tmrk.cloud.admin.sshKey";

	static private final Logger logger = Logger.getLogger(Terremark.class);

	private Terremark provider = null;

	public TerremarkKeypair(@Nonnull Terremark cloud) {
		this.provider =  cloud;
	}

	/**
	 * Creates an SSH keypair having the specified name.
	 * @param name the name of the SSH keypair
	 * @return a new SSH keypair
	 * @throws InternalException an error occurred within Dasein Cloud while processing the request
	 * @throws CloudException an error occurred in the cloud provider executing the keypair creation
	 */
	public @Nonnull SSHKeypair createKeypair(@Nonnull String name) throws InternalException, CloudException {
		logger.trace("enter - createKeypair()");
		SSHKeypair key = null;
		String orgId = provider.getOrganization().getId();
		String url = "/admin/" + SSH_KEYS + "/" + Terremark.ORGANZIATIONS + "/" + orgId + "/" + Terremark.ACTION + "/createSshKey";
		String body = "";

		name = validateName(name);

		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			docBuilder = docFactory.newDocumentBuilder();

			Document doc = docBuilder.newDocument();
			Element rootElement = doc.createElement("CreateSshKey");
			rootElement.setAttribute(Terremark.NAME, name);
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
			Node sshKey = doc.getElementsByTagName(SSH_KEY_TAG).item(0);
			logger.debug("createKeypair(): Parsing create keypair response.");
			key = toSSHKeypair(sshKey);
		}
		return key;
	}

	/**
	 * Creates a valid name for a Terremark ssh key
	 * @param name The proposed name
	 * @return A valid name based on the proposed name.
	 */
	private String validateName(String name) {
		// Terremark ssh key name rules: Max length of 50 characters. Only the characters a - z, A - Z, 0 - 9, underscores, or spaces allowed in the key name.
		if (name.length() > 50) {
			name.substring(0, 50);
		}

		StringBuilder str = new StringBuilder();

		for( int i=0; i<name.length(); i++ ) {
			char c = name.charAt(i);

			if( Character.isLetterOrDigit(c) ) {
				str.append(c);
			}
			else if( c == ' ' || c == '_' ) {
				str.append(c);
			}
			else if( c == '-' ) {
				str.append('_');
			}
		}
		if( str.length() < 1 ) {
			str.append("unnamed");
		}

		return str.toString();
	}

	/**
	 * Deletes the specified keypair having the specified name.
	 * @param providerId the provider ID of the keypair to be deleted
	 * @throws InternalException an error occurred within Dasein Cloud while performing the deletion
	 * @throws CloudException an error occurred with the cloud provider while performing the deletion
	 */
	public void deleteKeypair(@Nonnull String providerId) throws InternalException, CloudException {
		String url = "/admin/" + SSH_KEYS + "/" + providerId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.DELETE, url, null, "");
		method.invoke();
	}

	/**
	 * Fetches the fingerprint of the specified key so you can validate it against the one you have.
	 * @param providerId the provider ID of the keypair
	 * @return the fingerprint for the specified keypair
	 * @throws InternalException an error occurred within Dasein Cloud while fetching the fingerprint
	 * @throws CloudException an error occurred with the cloud provider while fetching the fingerprint
	 */
	public @Nullable String getFingerprint(@Nonnull String providerId) throws InternalException, CloudException {
		logger.trace("enter - getFingerprint(" + providerId + ")");
		String fingerprint = null;
		SSHKeypair key = null;
		String url = "/admin/" + SSH_KEYS + "/" + providerId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null) {
			Node sshKey = doc.getElementsByTagName(SSH_KEY_TAG).item(0);
			key = toSSHKeypair(sshKey);
		}
		if (key != null) {
			fingerprint = key.getFingerprint();
		}
		logger.trace("exit - getFingerprint(" + providerId + ")");
		return fingerprint;
	}

	/**
	 * Indicates to what degree key importing vs. creation is supported. If importing is not supported, then that
	 * means all keys must be created through the cloud provider via {@link #createKeypair(String)}. If importing is
	 * required, it means all key creation must be done by importing your keys through {@link #importKeypair(String, String)}.
	 * If optional, then you can either import or create keys. When you have the choice, it is always recommended that
	 * you import keys.
	 * @return the requirement state of importing key pairs
	 * @throws CloudException an error occurred with the cloud provider in determining support
	 * @throws InternalException a local error occurred while determining support
	 */
	@Override
	public Requirement getKeyImportSupport() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	/**
	 * Fetches the specified keypair from the cloud provider. The cloud provider may or may not know
	 * about the public key at this time, so be prepared for a null {@link SSHKeypair#getPublicKey()}.
	 * @param providerId the unique ID of the target keypair
	 * @return the keypair matching the specified provider ID or <code>null</code> if no such key exists
	 * @throws InternalException an error occurred in the Dasein Cloud implementation while fetching the keypair
	 * @throws CloudException an error occurred with the cloud provider while fetching the keypair
	 */
	public @Nullable SSHKeypair getKeypair(@Nonnull String providerId) throws InternalException, CloudException {
		logger.trace("enter - getKeypair(" + providerId + ")");
		SSHKeypair key = null;
		String url = "/admin/" + SSH_KEYS + "/" + providerId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		try {
			Document doc = method.invoke();
			if (doc != null) {
				Node sshKey = doc.getElementsByTagName(SSH_KEY_TAG).item(0);
				key = toSSHKeypair(sshKey);
			}
		}
		catch (CloudException e) {
			logger.debug("Did not find requested keypair");
		}
		catch (InternalException i) {
			logger.debug("Failed to find requested keypair");
		}

		logger.trace("exit - getKeypair(" + providerId + ")");
		return key;
	}

	/**
	 * Provides the provider term for an SSH keypair.
	 * @param locale the locale into which the term should be translated
	 * @return the provider term for the SSH keypair, ideally translated for the specified locale
	 */
	public @Nonnull String getProviderTermForKeypair(@Nonnull Locale locale) {
		return "SSH key";
	}

	/**
	 * Imports the specified public key into your store of keys with the cloud provider under the specified name.
	 * @param name the name of the key to be imported
	 * @param publicKey the MD5 public key fingerprint as specified in section 4 of RFC4716
	 * @return the unique ID of the key as it is stored with the cloud provider
	 * @throws InternalException a local error occurred assembling the request
	 * @throws CloudException an error occurred with the cloud provider while importing the keys
	 */
	@Override
	public SSHKeypair importKeypair(String name, String publicKey) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Importing keypairs is not supported in Terremark.");
	}

	/**
	 * @return true if the cloud provider supports shell keypairs in the current region and the current account can use them
	 * @throws CloudException an error occurred with the cloud provider while determining subscription status
	 * @throws InternalException an error occurred within the Dasein Cloud implementation determining subscription status
	 */
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	/**
	 * @return a list of all available SSH keypairs (private key is null)
	 * @throws InternalException an error occurred within Dasein Cloud listing the keypairs
	 * @throws CloudException an error occurred with the cloud provider listing the keyspairs
	 */
	public @Nonnull Collection<SSHKeypair> list() throws InternalException, CloudException {
		logger.trace("enter - list()");
		Collection<SSHKeypair> keys = new ArrayList<SSHKeypair>();
		String orgId = provider.getOrganization().getId();
		String url = "/admin/" + SSH_KEYS + "/" + Terremark.ORGANZIATIONS + "/" + orgId;
		TerremarkMethod method = new TerremarkMethod(provider, HttpMethodName.GET, url, null, null);
		Document doc = method.invoke();
		if (doc != null) {
			NodeList sshKeys = doc.getElementsByTagName(SSH_KEY_TAG);
			for (int i=0; i < sshKeys.getLength(); i++) {
				SSHKeypair key = toSSHKeypair(sshKeys.item(i));
				if (key != null) {
					keys.add(key);
				}
			}
		}
		logger.trace("exit - list()");
		return keys;
	}

	@Override
	public String[] mapServiceAction(ServiceAction arg0) {
		return new String[0];
	}

	private SSHKeypair toSSHKeypair(Node keyNode) {
		SSHKeypair key = new SSHKeypair();
		String keyId = Terremark.hrefToId(keyNode.getAttributes().getNamedItem(Terremark.HREF).getTextContent());
		key.setProviderKeypairId(keyId);
		String name = keyNode.getAttributes().getNamedItem(Terremark.NAME).getTextContent();
		key.setName(name);
		NodeList keyChildren = keyNode.getChildNodes();
		for (int i=0; i < keyChildren.getLength(); i++) {
			Node keyChild = keyChildren.item(i);
			if (keyChild.getNodeName().equals("FingerPrint")) {
				key.setFingerprint(keyChild.getTextContent());
				logger.debug("toSSHKeypair(): ID: " + key.getProviderKeypairId() + " Fingerprint: " + key.getFingerprint());
			}
			else if (keyChild.getNodeName().equals("PrivateKey")){
				String encodedKey = keyChild.getTextContent();
				byte[] privateKey = encodedKey.getBytes();
				key.setPrivateKey(privateKey);
			}
		}
		key.setProviderOwnerId(provider.getContext().getAccountNumber());
		key.setProviderRegionId(provider.getContext().getRegionId());
		return key;
	}
}
