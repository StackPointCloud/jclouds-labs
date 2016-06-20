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
package org.apache.jclouds.profitbricks.rest.compute;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import static com.google.common.base.Strings.isNullOrEmpty;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import static com.google.common.collect.Iterables.transform;
import com.google.common.collect.Lists;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.inject.Inject;
import static java.lang.String.format;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javax.annotation.Resource;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.jclouds.profitbricks.rest.ProfitBricksApi;
import org.apache.jclouds.profitbricks.rest.compute.concurrent.ProvisioningJob;
import org.apache.jclouds.profitbricks.rest.compute.concurrent.ProvisioningManager;
import static org.apache.jclouds.profitbricks.rest.config.ProfitBricksComputeProperties.POLL_PREDICATE_DATACENTER;
import static org.apache.jclouds.profitbricks.rest.config.ProfitBricksComputeProperties.POLL_PREDICATE_NIC;
import static org.apache.jclouds.profitbricks.rest.config.ProfitBricksComputeProperties.POLL_PREDICATE_SERVER;
import org.apache.jclouds.profitbricks.rest.domain.DataCenter;
import org.apache.jclouds.profitbricks.rest.domain.Image;
import org.apache.jclouds.profitbricks.rest.domain.IpBlock;
import org.apache.jclouds.profitbricks.rest.domain.IpBlock.PropertiesRequest;
import org.apache.jclouds.profitbricks.rest.domain.Nic;
import org.apache.jclouds.profitbricks.rest.domain.Provisionable;
import org.apache.jclouds.profitbricks.rest.domain.Server;
import org.apache.jclouds.profitbricks.rest.domain.Snapshot;
import org.apache.jclouds.profitbricks.rest.domain.VolumeType;
import org.apache.jclouds.profitbricks.rest.domain.options.DepthOptions;
import org.apache.jclouds.profitbricks.rest.domain.zonescoped.DataCenterAndId;
import org.apache.jclouds.profitbricks.rest.domain.zonescoped.ServerInDataCenter;
import org.apache.jclouds.profitbricks.rest.features.DataCenterApi;
import org.apache.jclouds.profitbricks.rest.features.ServerApi;
import org.apache.jclouds.profitbricks.rest.ids.NicRef;
import org.apache.jclouds.profitbricks.rest.ids.ServerRef;
import org.apache.jclouds.profitbricks.rest.ids.VolumeRef;
import static org.jclouds.Constants.PROPERTY_USER_THREADS;
import org.jclouds.compute.ComputeServiceAdapter;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_NODE_RUNNING;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.HardwareBuilder;
import org.jclouds.compute.domain.Processor;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.Volume;
import org.jclouds.compute.domain.internal.VolumeImpl;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.compute.reference.ComputeServiceConstants;
import org.jclouds.compute.util.ComputeServiceUtils;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.logging.Logger;
import org.jclouds.rest.ResourceNotFoundException;

@Singleton
public class ProfitBricksComputeServiceAdapter implements ComputeServiceAdapter<ServerInDataCenter, Hardware, Provisionable, DataCenter> {

    @Resource
    @Named(ComputeServiceConstants.COMPUTE_LOGGER)
    protected Logger logger = Logger.NULL;

    private final ProfitBricksApi api;
    private final Predicate<String> waitDcUntilAvailable;
    private final Predicate<VolumeRef> waitVolumeUntilAvailable;
    private final Predicate<ServerRef> waitServerUntilAvailable;
    private final Predicate<NicRef> waitNICUntilAvailable;
    private final ListeningExecutorService executorService;
    private final ProvisioningJob.Factory jobFactory;
    private final ProvisioningManager provisioningManager;

    private String dataCenterId = "";

    private static final Integer DEFAULT_LAN_ID = 1;

    @Inject
    ProfitBricksComputeServiceAdapter(ProfitBricksApi api,
            @Named(POLL_PREDICATE_DATACENTER) Predicate<String> waitDcUntilAvailable,
            @Named(TIMEOUT_NODE_RUNNING) Predicate<VolumeRef> waitVolumeUntilAvailable,
            @Named(PROPERTY_USER_THREADS) ListeningExecutorService executorService,
            @Named(POLL_PREDICATE_SERVER) Predicate<ServerRef> waitServerUntilAvailable,
            @Named(POLL_PREDICATE_NIC) Predicate<NicRef> waitNICUntilAvailable,
            ProvisioningJob.Factory jobFactory,
            ProvisioningManager provisioningManager) {
        this.api = api;
        this.waitDcUntilAvailable = waitDcUntilAvailable;
        this.waitVolumeUntilAvailable = waitVolumeUntilAvailable;
        this.waitServerUntilAvailable = waitServerUntilAvailable;
        this.waitNICUntilAvailable = waitNICUntilAvailable;
        this.executorService = executorService;
        this.jobFactory = jobFactory;
        this.provisioningManager = provisioningManager;
    }

    private void SetDataCenterId(String id) {
        dataCenterId = id;
    }

    @Override
    public NodeAndInitialCredentials<ServerInDataCenter> createNodeWithGroupEncodedIntoName(String group, String name, Template template) {
        final String dataCenterId = template.getLocation().getId();
        SetDataCenterId(dataCenterId);

        Hardware hardware = template.getHardware();
        TemplateOptions options = template.getOptions();
        final String loginUser = isNullOrEmpty(options.getLoginUser()) ? "root" : options.getLoginUser();
//        final String password = options.hasLoginPassword() ? options.getLoginPassword() : Passwords.generate();
        final String password = "LTbAHNbcMt";

        final org.jclouds.compute.domain.Image image = template.getImage();

        // provision all volumes based on hardware
        List<? extends Volume> volumes = hardware.getVolumes();
        List<String> volumeIds = Lists.newArrayListWithExpectedSize(volumes.size());

        int i = 1;
        for (final Volume volume : volumes) {
            try {
                logger.trace("<< provisioning volume '%s'", volume);
                final org.apache.jclouds.profitbricks.rest.domain.Volume.Request.CreatePayload request = org.apache.jclouds.profitbricks.rest.domain.Volume.Request.creatingBuilder()
                        .dataCenterId(dataCenterId)
                        // put image to first volume
                        .image(image.getId())
                        .imagePassword(password)
                        .name(format("%s-disk-%d", name, i++))
                        .size(volume.getSize().intValue())
                        .type(VolumeType.HDD)
                        .build();

                String volumeId = (String) provisioningManager.provision(jobFactory.create(dataCenterId, new Supplier<Object>() {

                    @Override
                    public Object get() {
                        return api.volumeApi().createVolume(request).id();
                    }
                }));

                volumeIds.add(volumeId);
                logger.trace(">> provisioning complete for volume. returned id='%s'", volumeId);
            } catch (Exception ex) {
                if (i - 1 == 1) // if first volume (one with image) provisioning fails; stop method
                {
                    throw Throwables.propagate(ex);
                }
                logger.warn(ex, ">> failed to provision volume. skipping..");
            }
        }

        String volumeBootDeviceId = Iterables.get(volumeIds, 0); // must have atleast 1
        waitVolumeUntilAvailable.apply(VolumeRef.create(dataCenterId, volumeBootDeviceId));

        Double cores = ComputeServiceUtils.getCores(hardware);

        // provision server and connect boot volume (first provisioned)
        final String serverId;
        try {

            Server.BootVolume bootVolume = Server.BootVolume.create(volumeBootDeviceId);

            final Server.Request.CreatePayload serverRequest = Server.Request.creatingBuilder()
                    .dataCenterId(dataCenterId)
                    .name(name)
                    .bootVolume(bootVolume)
                    .cores(cores.intValue())
                    .ram(hardware.getRam())
                    .build();
            logger.trace("<< provisioning server '%s'", serverRequest);

            serverId = (String) provisioningManager.provision(jobFactory.create(dataCenterId, new Supplier<Object>() {

                @Override
                public Object get() {
                    return api.serverApi().createServer(serverRequest).id();
                }
            }));
            logger.trace(">> provisioning complete for server. returned id='%s'", serverId);

        } catch (Exception ex) {
            logger.error(ex, ">> failed to provision server. rollbacking..");
            destroyVolumes(volumeIds, dataCenterId);
            throw Throwables.propagate(ex);
        }

        waitServerUntilAvailable.apply(ServerRef.create(dataCenterId, serverId));
        waitDcUntilAvailable.apply(dataCenterId);
        //add a NIC to the server and assign an IP to the server

        int lanId = DEFAULT_LAN_ID;
        if (options.getNetworks() != null) {
            try {
                String networkId = Iterables.get(options.getNetworks(), 0);
                lanId = Integer.valueOf(networkId);
            } catch (Exception ex) {
                logger.warn("no valid network id found from options. using default id='%d'", DEFAULT_LAN_ID);
            }
        }
        IpBlock ipBlock = api.ipBlockApi().create(IpBlock.Request.creatingBuilder()
                .properties(PropertiesRequest.create(name + " block", template.getLocation().getParent().getId(), 1))
                .build());

        Nic nic = api.nicApi().create(Nic.Request.creatingBuilder()
                .dataCenterId(dataCenterId)
                .dhcp(Boolean.TRUE)
                .lan(lanId)
                .ips(ipBlock.properties().ips())
                .firewallActive(Boolean.FALSE)
                .serverId(serverId).
                build());

        waitNICUntilAvailable.apply(NicRef.create(dataCenterId, serverId, nic.id()));

//        connect the rest of volumes to server;delete if fails
        final int volumeCount = volumeIds.size();
        for (int j = 1; j < volumeCount; j++) { // skip first; already connected
            final String volumeId = volumeIds.get(j);
            try {
                logger.trace("<< connecting volume '%s' to server '%s'", volumeId, serverId);
                provisioningManager.provision(jobFactory.create(group, new Supplier<Object>() {

                    @Override
                    public Object get() {
                        return api.serverApi().attachVolume(
                                Server.Request.attachVolumeBuilder()
                                .dataCenterId(dataCenterId)
                                .serverId(serverId)
                                .volumeId(volumeId)
                                .build()
                        );
                    }
                }));

                logger.trace(">> volume connected.");
            } catch (Exception ex) {
                // delete unconnected volume
                logger.warn(ex, ">> failed to connect volume '%s'. deleting..", volumeId);
                destroyVolume(volumeId, dataCenterId);
            }
        }
        // Last paranoid check
        waitDcUntilAvailable.apply(dataCenterId);

        LoginCredentials serverCredentials = LoginCredentials.builder()
                .user(loginUser)
                .password(password)
                .build();
        String serverInDataCenterId = DataCenterAndId.fromDataCenterAndId(dataCenterId, serverId).slashEncode();
        rebootNode(serverInDataCenterId);
        waitServerUntilAvailable.apply(ServerRef.create(dataCenterId, serverId));

        try {
            Thread.sleep(30000);
        } catch (InterruptedException ex) {
            java.util.logging.Logger.getLogger(ProfitBricksComputeServiceAdapter.class.getName()).log(Level.SEVERE, null, ex);
        }
        ServerInDataCenter server = getNode(serverInDataCenterId);

        return new NodeAndInitialCredentials<ServerInDataCenter>(server, serverInDataCenterId, serverCredentials);
    }

    @Override
    public Iterable<Hardware> listHardwareProfiles() {
        // Max [cores=48] [disk size per volume=2048GB] [ram=200704 MB]
        List<Hardware> hardwares = Lists.newArrayList();
        for (int core = 1; core <= 48; core++) {
            for (int ram : new int[]{1024, 2 * 1024, 4 * 1024, 8 * 1024,
                10 * 1024, 16 * 1024, 24 * 1024, 28 * 1024, 32 * 1024}) {
                for (float size : new float[]{10, 20, 30, 50, 80, 100, 150, 200, 250, 500}) {
                    String id = String.format("cpu=%d,ram=%s,disk=%f", core, ram, size);
                    hardwares.add(new HardwareBuilder()
                            .ids(id)
                            .ram(ram)
                            .hypervisor("kvm")
                            .name(id)
                            .processor(new Processor(core, 1d))
                            .volume(new VolumeImpl(size, true, true))
                            .build());
                }
            }
        }
        return hardwares;
    }

    @Override
    public Iterable<Provisionable> listImages() {
        // fetch images..
        ListenableFuture<List<Image>> images = executorService.submit(new Callable<List<Image>>() {

            @Override
            public List<Image> call() throws Exception {
                logger.trace("<< fetching images..");
                // Filter HDD types only, since JClouds doesn't have a concept of "CD-ROM" anyway
                Iterable<Image> filteredImages = Iterables.filter(api.imageApi().getList(new DepthOptions().depth(1)), new Predicate<Image>() {

                    @Override
                    public boolean apply(Image image) {
                        return image.properties().imageType() == Image.Type.HDD;
                    }
                });
                logger.trace(">> images fetched.");

                return ImmutableList.copyOf(filteredImages);
            }

        });
        // and snapshots at the same time
        ListenableFuture<List<Snapshot>> snapshots = executorService.submit(new Callable<List<Snapshot>>() {

            @Override
            public List<Snapshot> call() throws Exception {
                logger.trace("<< fetching snapshots");
                List<Snapshot> remoteSnapshots = api.snapshotApi().list(new DepthOptions().depth(1));
                logger.trace(">> snapshots feched.");

                return remoteSnapshots;
            }

        });

        return Iterables.concat(getUnchecked(images), getUnchecked(snapshots));
    }

    @Override
    public Provisionable getImage(String id) {
        // try search images
        logger.trace("<< searching for image with id=%s", id);
        Image image = api.imageApi().getImage(id);
        if (image != null) {
            logger.trace(">> found image [%s].", image.properties().name());
            return image;
        }
        // try search snapshots
        logger.trace("<< not found from images. searching for snapshot with id=%s", id);
        Snapshot snapshot = api.snapshotApi().get(id);
        if (snapshot != null) {
            logger.trace(">> found snapshot [%s]", snapshot.properties().name());
            return snapshot;
        }
        throw new ResourceNotFoundException("No image/snapshot with id '" + id + "' was found");
    }

    @Override
    public Iterable<DataCenter> listLocations() {
        logger.trace("<< fetching datacenters..");
        final DataCenterApi dcApi = api.dataCenterApi();

        // Fetch all datacenters
        ListenableFuture<List<DataCenter>> futures = allAsList(transform(dcApi.list(),
                new Function<DataCenter, ListenableFuture<DataCenter>>() {

            @Override
            public ListenableFuture<DataCenter> apply(final DataCenter input) {
                // Fetch more details in parallel
                return executorService.submit(new Callable<DataCenter>() {
                    @Override
                    public DataCenter call() throws Exception {
                        logger.trace("<< fetching datacenter with id [%s]", input.id());
                        return dcApi.getDataCenter(input.id());
                    }

                });
            }
        }));

        return getUnchecked(futures);
    }

    @Override
    public ServerInDataCenter getNode(String id) {
        DataCenterAndId datacenterAndId = DataCenterAndId.fromSlashEncoded(id);
        logger.trace("<< searching for server with id=%s", id);

        Server server = api.serverApi().getServer(datacenterAndId.getDataCenter(), datacenterAndId.getId(), new DepthOptions().depth(5));
        if (server != null) {
            logger.trace(">> found server [%s]", server.properties().name());
        }
        return server == null ? null : new ServerInDataCenter(server, datacenterAndId.getDataCenter());
    }

    @Override
    public void destroyNode(String nodeId) {
        DataCenterAndId datacenterAndId = DataCenterAndId.fromSlashEncoded(nodeId);
        ServerApi serverApi = api.serverApi();
        Server server = serverApi.getServer(datacenterAndId.getDataCenter(), datacenterAndId.getId(), new DepthOptions().depth(5));
        if (server != null) {
            for (org.apache.jclouds.profitbricks.rest.domain.Volume volume : server.entities().volumes().items()) {
                destroyVolume(volume.id(), datacenterAndId.getDataCenter());
            }

            try {
                destroyServer(datacenterAndId.getId(), datacenterAndId.getDataCenter());
            } catch (Exception ex) {
                logger.warn(ex, ">> failed to delete server with id=%s", datacenterAndId.getId());
            }
        }
    }

    @Override
    public void rebootNode(final String id) {
        final DataCenterAndId datacenterAndId = DataCenterAndId.fromSlashEncoded(id);
        // Fail pre-emptively if not found
        final ServerInDataCenter node = getRequiredNode(id);
        provisioningManager.provision(jobFactory.create(datacenterAndId.getDataCenter(), new Supplier<Object>() {

            @Override
            public Object get() {
                api.serverApi().rebootServer(datacenterAndId.getDataCenter(), datacenterAndId.getId());
                return node;
            }
        }));
    }

    @Override
    public void resumeNode(final String id) {
        final DataCenterAndId datacenterAndId = DataCenterAndId.fromSlashEncoded(id);
        final ServerInDataCenter node = getRequiredNode(datacenterAndId.getId());
        if (node.getServer().properties().vmState() == Server.Status.RUNNING) {
            return;
        }

        provisioningManager.provision(jobFactory.create(datacenterAndId.getDataCenter(), new Supplier<Object>() {

            @Override
            public Object get() {
                api.serverApi().startServer(datacenterAndId.getDataCenter(), id);

                return node;
            }
        }));
    }

    @Override
    public void suspendNode(final String id) {
        final DataCenterAndId datacenterAndId = DataCenterAndId.fromSlashEncoded(id);

        final ServerInDataCenter node = getRequiredNode(datacenterAndId.getId());
        // Intentionally didn't include SHUTDOWN (only achieved via UI; soft-shutdown). 
        // A SHUTOFF server is no longer billed, so we execute method for all other status
        if (node.getServer().properties().vmState() == Server.Status.SHUTOFF) {
            return;
        }

        provisioningManager.provision(jobFactory.create(datacenterAndId.getDataCenter(), new Supplier<Object>() {

            @Override
            public Object get() {
                api.serverApi().stopServer(datacenterAndId.getDataCenter(), id);

                return node;
            }
        }));
    }

    @Override
    public Iterable<ServerInDataCenter> listNodes() {

        ImmutableSet.Builder<ServerInDataCenter> builder = ImmutableSet.builder();
        logger.trace(">> fetching all servers..");
        builder.addAll(FluentIterable.from(api.serverApi().getList(dataCenterId))
                .transform(new Function<Server, ServerInDataCenter>() {

                    @Override
                    public ServerInDataCenter apply(Server arg0) {
                        return new ServerInDataCenter(api.serverApi().getServer(dataCenterId, arg0.id()), dataCenterId);
                    }

                }));
        logger.trace(">> servers fetched.");

        return builder.build();
    }

    @Override
    public Iterable<ServerInDataCenter> listNodesByIds(final Iterable<String> ids) {
        // Only fetch the requested nodes. Do it in parallel.
        ListenableFuture<List<ServerInDataCenter>> futures = allAsList(transform(ids,
                new Function<String, ListenableFuture<ServerInDataCenter>>() {

            @Override
            public ListenableFuture<ServerInDataCenter> apply(final String input) {
                return executorService.submit(new Callable<ServerInDataCenter>() {

                    @Override
                    public ServerInDataCenter call() throws Exception {
                        return getNode(input);
                    }
                });
            }
        }));

        return getUnchecked(futures);
    }

    private void destroyServer(final String serverId, final String dataCenterId) {
        try {
            logger.trace("<< deleting server with id=%s", serverId);
            provisioningManager.provision(jobFactory.create(dataCenterId, new Supplier<Object>() {

                @Override
                public Object get() {
                    api.serverApi().deleteServer(dataCenterId, serverId);

                    return serverId;
                }
            }));
            logger.trace(">> server '%s' deleted.", serverId);
        } catch (Exception ex) {
            logger.warn(ex, ">> failed to delete server with id=%s", serverId);
        }
    }

    private void destroyVolumes(List<String> volumeIds, String dataCenterId) {
        for (String volumeId : volumeIds) {
            destroyVolume(volumeId, dataCenterId);
        }
    }

    private void destroyVolume(final String volumeId, final String dataCenterId) {
        try {
            logger.trace("<< deleting volume with id=%s", volumeId);
            provisioningManager.provision(jobFactory.create(dataCenterId, new Supplier<Object>() {

                @Override
                public Object get() {
                    api.volumeApi().deleteVolume(dataCenterId, volumeId);

                    return volumeId;
                }
            }));
            logger.trace(">> volume '%s' deleted.", volumeId);
        } catch (Exception ex) {
            logger.warn(ex, ">> failed to delete volume with id=%s", volumeId);
        }
    }

    private ServerInDataCenter getRequiredNode(String nodeId) {
        ServerInDataCenter node = getNode(nodeId);
        if (node == null) {
            throw new ResourceNotFoundException("Node with id'" + nodeId + "' was not found.");
        }
        return node;
    }

}
