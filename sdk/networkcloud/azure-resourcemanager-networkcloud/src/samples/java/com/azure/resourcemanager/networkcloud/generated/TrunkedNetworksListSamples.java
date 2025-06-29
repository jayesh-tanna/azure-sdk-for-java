// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) AutoRest Code Generator.

package com.azure.resourcemanager.networkcloud.generated;

/**
 * Samples for TrunkedNetworks List.
 */
public final class TrunkedNetworksListSamples {
    /*
     * x-ms-original-file:
     * specification/networkcloud/resource-manager/Microsoft.NetworkCloud/stable/2025-02-01/examples/
     * TrunkedNetworks_ListBySubscription.json
     */
    /**
     * Sample code: List trunked networks for subscription.
     * 
     * @param manager Entry point to NetworkCloudManager.
     */
    public static void
        listTrunkedNetworksForSubscription(com.azure.resourcemanager.networkcloud.NetworkCloudManager manager) {
        manager.trunkedNetworks().list(com.azure.core.util.Context.NONE);
    }
}
