// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) TypeSpec Code Generator.

package com.azure.resourcemanager.servicefabricmanagedclusters.implementation;

import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.util.Context;
import com.azure.core.util.logging.ClientLogger;
import com.azure.resourcemanager.servicefabricmanagedclusters.fluent.ManagedMaintenanceWindowStatusesClient;
import com.azure.resourcemanager.servicefabricmanagedclusters.fluent.models.ManagedMaintenanceWindowStatusInner;
import com.azure.resourcemanager.servicefabricmanagedclusters.models.ManagedMaintenanceWindowStatus;
import com.azure.resourcemanager.servicefabricmanagedclusters.models.ManagedMaintenanceWindowStatuses;

public final class ManagedMaintenanceWindowStatusesImpl implements ManagedMaintenanceWindowStatuses {
    private static final ClientLogger LOGGER = new ClientLogger(ManagedMaintenanceWindowStatusesImpl.class);

    private final ManagedMaintenanceWindowStatusesClient innerClient;

    private final com.azure.resourcemanager.servicefabricmanagedclusters.ServiceFabricManagedClustersManager serviceManager;

    public ManagedMaintenanceWindowStatusesImpl(ManagedMaintenanceWindowStatusesClient innerClient,
        com.azure.resourcemanager.servicefabricmanagedclusters.ServiceFabricManagedClustersManager serviceManager) {
        this.innerClient = innerClient;
        this.serviceManager = serviceManager;
    }

    public Response<ManagedMaintenanceWindowStatus> getWithResponse(String resourceGroupName, String clusterName,
        Context context) {
        Response<ManagedMaintenanceWindowStatusInner> inner
            = this.serviceClient().getWithResponse(resourceGroupName, clusterName, context);
        if (inner != null) {
            return new SimpleResponse<>(inner.getRequest(), inner.getStatusCode(), inner.getHeaders(),
                new ManagedMaintenanceWindowStatusImpl(inner.getValue(), this.manager()));
        } else {
            return null;
        }
    }

    public ManagedMaintenanceWindowStatus get(String resourceGroupName, String clusterName) {
        ManagedMaintenanceWindowStatusInner inner = this.serviceClient().get(resourceGroupName, clusterName);
        if (inner != null) {
            return new ManagedMaintenanceWindowStatusImpl(inner, this.manager());
        } else {
            return null;
        }
    }

    private ManagedMaintenanceWindowStatusesClient serviceClient() {
        return this.innerClient;
    }

    private com.azure.resourcemanager.servicefabricmanagedclusters.ServiceFabricManagedClustersManager manager() {
        return this.serviceManager;
    }
}
