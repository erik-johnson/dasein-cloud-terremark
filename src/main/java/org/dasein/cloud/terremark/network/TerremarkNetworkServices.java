package org.dasein.cloud.terremark.network;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.dasein.cloud.network.AbstractNetworkServices;
import org.dasein.cloud.terremark.Terremark;
import org.dasein.cloud.terremark.network.TerremarkIpAddressSupport;

public class TerremarkNetworkServices extends AbstractNetworkServices {
    private Terremark cloud;
    
    public TerremarkNetworkServices(@Nonnull Terremark cloud) { this.cloud = cloud; }
    
    @Override
    public @Nonnull TerremarkIpAddressSupport getIpAddressSupport() {
        return new TerremarkIpAddressSupport(cloud);
    }
    
    @Override 
    public @Nullable FirewallRule getFirewallSupport() {
        return new FirewallRule(cloud);
    }
    
    @Override
    public @Nonnull TerremarkNetworkSupport getVlanSupport() {
        return new TerremarkNetworkSupport(cloud);
    }

}
