/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jclouds.profitbricks.rest.compute.function;

import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Predicate;
import static com.google.common.base.Predicates.not;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import org.apache.jclouds.profitbricks.rest.domain.LicenceType;
import org.apache.jclouds.profitbricks.rest.domain.Nic;
import org.apache.jclouds.profitbricks.rest.domain.Server;
import org.apache.jclouds.profitbricks.rest.domain.zonescoped.ServerInDataCenter;
import org.jclouds.collect.Memoized;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.HardwareBuilder;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Processor;
import org.jclouds.compute.domain.Volume;
import org.jclouds.compute.functions.GroupNamingConvention;
import org.jclouds.domain.Location;
import org.jclouds.util.InetAddresses2;

public class ServerInDataCenterToNodeMetadata implements Function<ServerInDataCenter, NodeMetadata> {

    private final Function<org.apache.jclouds.profitbricks.rest.domain.Volume, Volume> fnVolume;
    private final Supplier<Set<? extends Location>> locationSupply;
    private final Function<List<Nic>, List<String>> fnCollectIps;

    private final GroupNamingConvention groupNamingConvention;

    @Inject
    public ServerInDataCenterToNodeMetadata(Function<org.apache.jclouds.profitbricks.rest.domain.Volume, Volume> fnVolume,
            @Memoized Supplier<Set<? extends Location>> locationsSupply,
            GroupNamingConvention.Factory groupNamingConvention) {
        this.fnVolume = fnVolume;
        this.locationSupply = locationsSupply;
        this.groupNamingConvention = groupNamingConvention.createWithoutPrefix();
        this.fnCollectIps = new Function<List<Nic>, List<String>>() {
            @Override
            public List<String> apply(List<Nic> in) {
                List<String> ips = Lists.newArrayListWithExpectedSize(in.size());
                for (Nic nic : in) {
                    ips.addAll(nic.properties().ips());
                }
                return ips;
            }
        };
    }

    @Override
    public NodeMetadata apply(final ServerInDataCenter serverInDataCenter) {
        Server server = serverInDataCenter.getServer();
        checkNotNull(server, "Null server");

        //Map fetched dataCenterId with actual populated object
        Location location = null;
        if (server.dataCenterId() != null) {
            location = Iterables.find(locationSupply.get(), new Predicate<Location>() {

                @Override
                public boolean apply(Location t) {
                    return t.getId().equals(serverInDataCenter.getServer().dataCenterId());
                }
            });
        }

        float size = 0f;
        List<Volume> volumes = Lists.newArrayList();
        List<org.apache.jclouds.profitbricks.rest.domain.Volume> storages = server.entities().volumes().items();
        if (storages != null) {
            for (org.apache.jclouds.profitbricks.rest.domain.Volume storage : storages) {
                size += storage.properties().size();
                volumes.add(fnVolume.apply(storage));
            }
        }

        // Build hardware
        String id = String.format("cpu=%d,ram=%d,disk=%.0f", server.properties().cores(), server.properties().ram(), size);
        Hardware hardware = new HardwareBuilder()
                .ids(id)
                .name(id)
                .ram(server.properties().ram())
                .processor(new Processor(server.properties().cores(), 1d))
                .hypervisor("kvm")
                .volumes(volumes)
                .location(location)
                .build();

        // Collect ips
        List<String> addresses = fnCollectIps.apply(server.entities().nics().items());
        OperatingSystem os = null;
        if (server.properties() != null && server.properties().bootVolume() != null && server.properties().bootVolume().properties() != null) {
            os = mapOsType(server.properties().bootVolume().properties().licenceType());

        }
        // Build node
        NodeMetadataBuilder nodeBuilder = new NodeMetadataBuilder();
        nodeBuilder.ids(serverInDataCenter.slashEncode())
                .group(groupNamingConvention.extractGroup(server.properties().name()))
                //              .hostname(server.href())
                .name(server.properties().name())
                .backendStatus(server.metadata().state().toString())
                .status(mapStatus(server.properties().vmState()))
                .hardware(hardware)
                .operatingSystem(os)
                .location(location)
                .privateAddresses(Iterables.filter(addresses, InetAddresses2.IsPrivateIPAddress.INSTANCE))
                .publicAddresses(Iterables.filter(addresses, not(InetAddresses2.IsPrivateIPAddress.INSTANCE)));

        return nodeBuilder.build();
    }

    static NodeMetadata.Status mapStatus(Server.Status status) {
        if (status == null) {
            return NodeMetadata.Status.UNRECOGNIZED;
        }
        switch (status) {
            case SHUTDOWN:
            case SHUTOFF:
            case PAUSED:
                return NodeMetadata.Status.SUSPENDED;
            case RUNNING:
                return NodeMetadata.Status.RUNNING;
            case BLOCKED:
                return NodeMetadata.Status.PENDING;
            case CRASHED:
                return NodeMetadata.Status.ERROR;
            default:
                return NodeMetadata.Status.UNRECOGNIZED;
        }
    }

    static OperatingSystem mapOsType(LicenceType osType) {
        if (osType != null) {
            switch (osType) {
                case WINDOWS:
                    return OperatingSystem.builder()
                            .description(OsFamily.WINDOWS.value())
                            .family(OsFamily.WINDOWS)
                            .build();
                case LINUX:
                    return OperatingSystem.builder()
                            .description(OsFamily.LINUX.value())
                            .family(OsFamily.LINUX)
                            .build();
            }
        }
        return OperatingSystem.builder()
                .description(OsFamily.UNRECOGNIZED.value())
                .family(OsFamily.UNRECOGNIZED)
                .build();
    }

}
