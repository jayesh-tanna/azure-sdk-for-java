// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) TypeSpec Code Generator.

package com.azure.resourcemanager.servicefabricmanagedclusters.generated;

import com.azure.resourcemanager.servicefabricmanagedclusters.models.FaultSimulationContentWrapper;
import com.azure.resourcemanager.servicefabricmanagedclusters.models.ZoneFaultSimulationContent;
import java.util.Arrays;

/**
 * Samples for NodeTypes StartFaultSimulation.
 */
public final class NodeTypesStartFaultSimulationSamples {
    /*
     * x-ms-original-file: 2025-03-01-preview/faultSimulation/NodeTypeStartFaultSimulation_example.json
     */
    /**
     * Sample code: Start Node Type Fault Simulation.
     * 
     * @param manager Entry point to ServiceFabricManagedClustersManager.
     */
    public static void startNodeTypeFaultSimulation(
        com.azure.resourcemanager.servicefabricmanagedclusters.ServiceFabricManagedClustersManager manager) {
        manager.nodeTypes()
            .startFaultSimulation("resRg", "myCluster", "BE", new FaultSimulationContentWrapper().withParameters(
                new ZoneFaultSimulationContent().withZones(Arrays.asList("2"))), com.azure.core.util.Context.NONE);
    }
}
