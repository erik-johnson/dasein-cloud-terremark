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

import javax.annotation.Nonnull;

import org.dasein.cloud.compute.AbstractComputeServices;
import org.dasein.cloud.terremark.Terremark;
import org.dasein.cloud.terremark.compute.Template;
import org.dasein.cloud.terremark.compute.VMSupport;

public class TerremarkComputeServices  extends AbstractComputeServices {
	
    private Terremark cloud;
    
    public TerremarkComputeServices(@Nonnull Terremark cloud) { this.cloud = cloud; }

    @Override
    public @Nonnull Template getImageSupport() {
        return new Template(cloud);
    }
    
    @Override
    public @Nonnull VMSupport getVirtualMachineSupport() {
        return new VMSupport(cloud);
    }
    
    @Override
    public @Nonnull DiskSupport getVolumeSupport() {
        return new DiskSupport(cloud);
    }

}
