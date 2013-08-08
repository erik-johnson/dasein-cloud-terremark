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

package org.dasein.cloud.terremark;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;

import javax.annotation.Nonnull;

public class TerremarkException extends CloudException {
	private String         code      = null;
	private CloudErrorType errorType = null;
	private int            status    = 0;
	private static final long serialVersionUID = -4873119673296587092L;

	public TerremarkException(@Nonnull TerremarkMethod.ParsedError e) {
		super(CloudErrorType.GENERAL, e.code, String.valueOf(e.code), e.message);
	}

	public TerremarkException(@Nonnull CloudErrorType type, @Nonnull TerremarkMethod.ParsedError e) {
		super(type, e.code, String.valueOf(e.code), e.message);        
	}

	public TerremarkException(int status, String code, String message) {
		super(message);
		this.code = code;
		this.status = status;
		if( code.equals("OperationNotAllowed") ) {
		    errorType = CloudErrorType.AUTHENTICATION;
		}
	}

	public String getCode() {
		return code;
	}

	public CloudErrorType getErrorType() {
		return (errorType == null ? CloudErrorType.GENERAL : errorType);
	}

	public int getStatus() {
		return status;
	}

	public String getSummary() { 
		return (status + "/" + code + ": " + getMessage());
	}
}
