/**
 * Copyright (C) 2009-2012 enStratus Networks Inc
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * An enumeration-like object for different implementations of the EC2 API. This cannot be an enum as it allows
 * for arbitrary values. Furthermore, values are matched in an case-insensitive fashion.
 * <p>Created by Erik Johnson: 11/28/12 4:30 PM</p>
 * @author Erik Johnson
 * @version 2012.04 initial version
 * @since 2012.04
 */
public class TerremarkProvider implements Comparable<TerremarkProvider> {
    static private final String ENTERPRISE_CLOUD_NAME  = "Terremark Enterprise Cloud";
    static private final String VCLOUD_EXPRESS_NAME    = "Terremark vCloud Express";
    static private final String OTHER_NAME             = "Other";

    static public final TerremarkProvider ENTERPRISE_CLOUD = new TerremarkProvider(ENTERPRISE_CLOUD_NAME);
    static public final TerremarkProvider VCLOUD_EXPRESS   = new TerremarkProvider(VCLOUD_EXPRESS_NAME);
    static public final TerremarkProvider OTHER            = new TerremarkProvider(OTHER_NAME);

    static private Set<TerremarkProvider> providers;

    static public @Nonnull TerremarkProvider valueOf(@Nonnull String providerName) {
        // cannot statically initialize the list of providers
        if( providers == null ) {
            TreeSet<TerremarkProvider> tmp = new TreeSet<TerremarkProvider>();

            Collections.addAll(tmp, ENTERPRISE_CLOUD, VCLOUD_EXPRESS);
            providers = Collections.unmodifiableSet(tmp);
        }
        for( TerremarkProvider provider : providers ) {
            if( provider.providerName.equalsIgnoreCase(providerName) ) {
                return provider;
            }
        }
        return OTHER;
    }

    static public @Nonnull Set<TerremarkProvider> values() {
        return providers;
    }

    private String providerName;

    private TerremarkProvider(String provider) {
        this.providerName = provider;
    }

    @Override
    public int compareTo(@Nullable TerremarkProvider other) {
        if( other == null ) {
            return -1;
        }
        return providerName.compareTo(other.providerName);
    }

    @Override
    public boolean equals(@Nullable Object ob) {
        return ob != null && (ob == this || getClass().getName().equals(ob.getClass().getName()) && providerName.equals(((TerremarkProvider) ob).providerName));
    }

    public @Nonnull String getName() {
        return providerName;
    }

    @Override
    public @Nonnull String toString() {
        return providerName;
    }

    public boolean isEnterpriseCloud() {
        return providerName.equals(ENTERPRISE_CLOUD_NAME);
    }

    public boolean isVcloudExpress() {
        return providerName.equals(VCLOUD_EXPRESS_NAME);
    }

    public boolean isOther() {
        return providerName.equals(OTHER_NAME);
    }

}
