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

public class NodeService {

	private String description;
	private boolean enabled;
	private String id;
	private String name;
	private int port;
	private String privateIpAddressId;
	private String internetServiceId;
	
	public NodeService() { }
	
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

	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public String getPrivateIpAddressId() {
		return privateIpAddressId;
	}

	public void setPrivateIpAddressId(String privateIpAddressId) {
		this.privateIpAddressId = privateIpAddressId;
	}

	public String getInternetServiceId() {
		return internetServiceId;
	}
	public void setInternetServiceId(String internetServiceId) {
		this.internetServiceId = internetServiceId;
	}
}
