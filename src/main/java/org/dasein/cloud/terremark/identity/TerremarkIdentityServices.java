package org.dasein.cloud.terremark.identity;

import org.dasein.cloud.identity.AbstractIdentityServices;
import org.dasein.cloud.terremark.Terremark;
import org.dasein.cloud.terremark.identity.TerremarkKeypair;

public class TerremarkIdentityServices extends AbstractIdentityServices {
    private Terremark cloud;
    
    public TerremarkIdentityServices(Terremark cloud) { this.cloud = cloud; }
    
    @Override
    public TerremarkKeypair getShellKeySupport() {
        return new TerremarkKeypair(cloud);
    }

}
