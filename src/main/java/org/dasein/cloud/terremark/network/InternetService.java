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

import org.dasein.cloud.network.Protocol;

public class InternetService {
	
	private String backupInternetServiceHref;
	private String backupInternetServiceName;
	private String description;
	private boolean enabled;
	private String id;
	private String name;
	private String persistenceTimeout;
	private String persistenceType;
	private int port;
	private Protocol protocol;
	private String publicIpId;
	private String redirectURL;
	private String trustedNetworkGroupHref;
	private String trustedNetworkGroupName;
	
	public InternetService() { }
	
	public String getBackupInternetServiceHref() {
		return backupInternetServiceHref;
	}

	public void setBackupInternetServiceHref(String backupInternetServiceHref) {
		this.backupInternetServiceHref = backupInternetServiceHref;
	}

	public String getBackupInternetServiceName() {
		return backupInternetServiceName;
	}

	public void setBackupInternetServiceName(String backupInternetServiceName) {
		this.backupInternetServiceName = backupInternetServiceName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPersistenceTimeout() {
		return persistenceTimeout;
	}

	public void setPersistenceTimeout(String persistenceTimeout) {
		this.persistenceTimeout = persistenceTimeout;
	}

	public String getPersistenceType() {
		return persistenceType;
	}

	public void setPersistenceType(String persistenceType) {
		this.persistenceType = persistenceType;
	}

	public int getPort() {
		return port;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public Protocol getProtocol() {
		return protocol;
	}

	public void setProtocol(Protocol protocol) {
		this.protocol = protocol;
	}

	public String getPublicIpId() {
		return publicIpId;
	}
	
	public void setPublicIpId(String publicIpId) {
		this.publicIpId = publicIpId;
	}

	public String getRedirectURL() {
		return redirectURL;
	}

	public void setRedirectURL(String redirectURL) {
		this.redirectURL = redirectURL;
	}

	public String getTrustedNetworkGroupHref() {
		return trustedNetworkGroupHref;
	}

	public void setTrustedNetworkGroupHref(String trustedNetworkGroupHref) {
		this.trustedNetworkGroupHref = trustedNetworkGroupHref;
	}

	public String getTrustedNetworkGroupName() {
		return trustedNetworkGroupName;
	}

	public void setTrustedNetworkGroupName(String trustedNetworkGroupName) {
		this.trustedNetworkGroupName = trustedNetworkGroupName;
	}

}
