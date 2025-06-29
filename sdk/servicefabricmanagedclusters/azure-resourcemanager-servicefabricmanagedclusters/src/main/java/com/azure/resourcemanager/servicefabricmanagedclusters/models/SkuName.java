// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) TypeSpec Code Generator.

package com.azure.resourcemanager.servicefabricmanagedclusters.models;

import com.azure.core.util.ExpandableStringEnum;
import java.util.Collection;

/**
 * Sku Name.
 */
public final class SkuName extends ExpandableStringEnum<SkuName> {
    /**
     * Basic requires a minimum of 3 nodes and allows only 1 node type.
     */
    public static final SkuName BASIC = fromString("Basic");

    /**
     * Requires a minimum of 5 nodes and allows 1 or more node type.
     */
    public static final SkuName STANDARD = fromString("Standard");

    /**
     * Creates a new instance of SkuName value.
     * 
     * @deprecated Use the {@link #fromString(String)} factory method.
     */
    @Deprecated
    public SkuName() {
    }

    /**
     * Creates or finds a SkuName from its string representation.
     * 
     * @param name a name to look for.
     * @return the corresponding SkuName.
     */
    public static SkuName fromString(String name) {
        return fromString(name, SkuName.class);
    }

    /**
     * Gets known SkuName values.
     * 
     * @return known SkuName values.
     */
    public static Collection<SkuName> values() {
        return values(SkuName.class);
    }
}
