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
