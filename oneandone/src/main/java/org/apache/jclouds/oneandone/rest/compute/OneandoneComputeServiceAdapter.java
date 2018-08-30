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
package org.apache.jclouds.oneandone.rest.compute;

import static com.google.common.base.Preconditions.checkState;
import com.google.common.base.Predicate;
import static com.google.common.base.Strings.isNullOrEmpty;
import com.google.common.base.Throwables;
import static com.google.common.collect.Iterables.contains;
import static com.google.common.collect.Iterables.filter;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.jclouds.oneandone.rest.OneAndOneApi;
import org.apache.jclouds.oneandone.rest.compute.options.OneandoneTemplateOptions;
import org.apache.jclouds.oneandone.rest.compute.strategy.CleanupResources;
import static org.apache.jclouds.oneandone.rest.config.OneAndOneProperties.POLL_PREDICATE_SERVER;
import org.apache.jclouds.oneandone.rest.domain.BareMetalModel;
import org.apache.jclouds.oneandone.rest.domain.DataCenter;
import org.apache.jclouds.oneandone.rest.domain.FirewallPolicy;
import org.apache.jclouds.oneandone.rest.domain.HardwareFlavour;
import org.apache.jclouds.oneandone.rest.domain.Hdd;
import org.apache.jclouds.oneandone.rest.domain.Server;
import org.apache.jclouds.oneandone.rest.domain.ServerAppliance;
import org.apache.jclouds.oneandone.rest.domain.SingleServerAppliance;
import org.apache.jclouds.oneandone.rest.domain.Types;
import org.apache.jclouds.oneandone.rest.domain.options.GenericQueryOptions;
import org.jclouds.compute.ComputeServiceAdapter;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.Volume;
import org.jclouds.compute.reference.ComputeServiceConstants;
import org.jclouds.compute.util.ComputeServiceUtils;
import static org.jclouds.compute.util.ComputeServiceUtils.getPortRangesFromList;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.logging.Logger;
import org.jclouds.rest.ResourceNotFoundException;
import org.jclouds.util.PasswordGenerator;

@Singleton
public class OneandoneComputeServiceAdapter implements ComputeServiceAdapter<Server, HardwareFlavour, SingleServerAppliance, DataCenter> {

   @Resource
   @Named(ComputeServiceConstants.COMPUTE_LOGGER)
   protected Logger logger = Logger.NULL;

   private final CleanupResources cleanupResources;
   private final OneAndOneApi api;
   private final Predicate<Server> waitServerUntilAvailable;
   private final PasswordGenerator.Config passwordGenerator;
   PasswordGenerator generate = new PasswordGenerator();

   @Inject
   OneandoneComputeServiceAdapter(OneAndOneApi api, CleanupResources cleanupResources,
           @Named(POLL_PREDICATE_SERVER) Predicate<Server> waitServerUntilAvailable,
           PasswordGenerator.Config passwordGenerator) {
      this.api = api;
      this.cleanupResources = cleanupResources;
      this.waitServerUntilAvailable = waitServerUntilAvailable;
      this.passwordGenerator = passwordGenerator;
   }

   @Override
   public NodeAndInitialCredentials<Server> createNodeWithGroupEncodedIntoName(String group, String name, Template template) {
      OneandoneTemplateOptions options = template.getOptions().as(OneandoneTemplateOptions.class);
      if (Types.ServerType.BAREMETAL.equals(options.serverType)) {
         return CreateBaremetalServer(group, name, template, options);
      }
      return CreateCloudServer(group, name, template, options);
   }

   private NodeAndInitialCredentials<Server> CreateCloudServer(String group, String name, Template template, OneandoneTemplateOptions options) {
      Hardware hardware = template.getHardware();
      Server updateServer = null;

      String dataCenterId = template.getLocation().getId();
      final String loginUser = isNullOrEmpty(options.getLoginUser()) ? "root" : options.getLoginUser();
      final String password = options.hasLoginPassword() ? options.getLoginPassword() : generate.generate();
      final String privateKey = options.hasLoginPrivateKey() ? options.getPrivateKey() : null;
      final org.jclouds.compute.domain.Image image = template.getImage();
      final int[] inboundPorts = template.getOptions().getInboundPorts();
      ServerAppliance validImage = getValidCloudImage(image);

      //prepare hdds to provision
      List<? extends Volume> volumes = hardware.getVolumes();
      List<Hdd.CreateHdd> hdds = new ArrayList<>();

      for (final Volume volume : volumes) {
         try {
            //check if the bootable device has enough size to run the appliance(image).
            float minHddSize = volume.getSize();
            if (volume.isBootDevice()) {
               if (validImage.minHddSize() > volume.getSize()) {
                  minHddSize = validImage.minHddSize();
               }
            }
            Hdd.CreateHdd hdd = Hdd.CreateHdd.create(minHddSize, volume.isBootDevice());
            hdds.add(hdd);
         } catch (Exception ex) {
            throw Throwables.propagate(ex);

         }
      }

      //configuring Firewall rules
      Map<Integer, Integer> portsRange = getPortRangesFromList(inboundPorts);
      List<FirewallPolicy.Rule.CreatePayload> rules = new ArrayList<>();

      for (Map.Entry<Integer, Integer> range : portsRange.entrySet()) {
         FirewallPolicy.Rule.CreatePayload rule = FirewallPolicy.Rule.CreatePayload.builder()
                 .portFrom(range.getKey())
                 .portTo(range.getValue())
                 .protocol(Types.RuleProtocol.TCP)
                 .build();
         rules.add(rule);
      }
      String firewallPolicyId = "";
      if (inboundPorts.length > 0) {
         FirewallPolicy firewallPolicy = api.firewallPolicyApi().create(FirewallPolicy.CreateFirewallPolicy.create(name + " firewall policy", "desc", rules));
         firewallPolicyId = firewallPolicy.id();
      }

      // provision server
      Server server = null;
      Double cores = ComputeServiceUtils.getCores(hardware);
      Double ram = (double) hardware.getRam();
      if (ram < 1024) {
         ram = 0.5;
      } else {
         ram = ram / 1024;
      }

      try {
         org.apache.jclouds.oneandone.rest.domain.Hardware.CreateHardware hardwareRequest = null;
         if (hardware.getId() != null) {
            String hardwareId = hardware.getId().split(",")[0];
            hardwareRequest = org.apache.jclouds.oneandone.rest.domain.Hardware.CreateHardware.create(null, hardwareId, null, null, null, null);
         } else {
            hardwareRequest
                    = org.apache.jclouds.oneandone.rest.domain.Hardware.CreateHardware.create(null, null, cores, 1.0, ram, hdds);
         }

         final Server.CreateServer serverRequest = Server.CreateServer.builder()
                 .name(name)
                 .description(name)
                 .hardware(hardwareRequest)
                 .rsaKey(options.getPublicKey())
                 .firewallPolicyId(firewallPolicyId)
                 .password(privateKey == null ? password : null)
                 .applianceId(validImage.id())
                 .dataCenterId(dataCenterId)
                 .powerOn(Boolean.TRUE).build();

         logger.trace("<< provisioning server '%s'", serverRequest);

         server = api.serverApi().create(serverRequest);

         waitServerUntilAvailable.apply(server);

         updateServer = api.serverApi().get(server.id());

         logger.trace(">> provisioning complete for server. returned id='%s'", server.id());

      } catch (Exception ex) {
         logger.error(ex, ">> failed to provision server. rollbacking..");
         if (server != null) {
            destroyNode(server.id());
         }
         throw Throwables.propagate(ex);
      }

      LoginCredentials serverCredentials = LoginCredentials.builder()
              .user(loginUser)
              .password(password)
              .privateKey(privateKey)
              .build();

      return new NodeAndInitialCredentials<Server>(updateServer, updateServer.id(), serverCredentials);
   }

   private NodeAndInitialCredentials<Server> CreateBaremetalServer(String group, String name, Template template, OneandoneTemplateOptions options) {
      Hardware hardware = template.getHardware();
      Server updateServer = null;

      final String dataCenterId = template.getLocation().getId();
      final String loginUser = isNullOrEmpty(options.getLoginUser()) ? "root" : options.getLoginUser();
      final String password = options.hasLoginPassword() ? options.getLoginPassword() : generate.generate();
      final String privateKey = options.hasLoginPrivateKey() ? options.getPrivateKey() : null;
      final org.jclouds.compute.domain.Image image = template.getImage();
      final int[] inboundPorts = template.getOptions().getInboundPorts();
      ServerAppliance validImage = getValidBaremetalImage(image);
      String firewallPolicyId = "";

      Map<Integer, Integer> portsRange = getPortRangesFromList(inboundPorts);
      List<FirewallPolicy.Rule.CreatePayload> rules = new ArrayList<FirewallPolicy.Rule.CreatePayload>();

      for (Map.Entry<Integer, Integer> range : portsRange.entrySet()) {
         FirewallPolicy.Rule.CreatePayload rule = FirewallPolicy.Rule.CreatePayload.builder()
                 .portFrom(range.getKey())
                 .portTo(range.getValue())
                 .protocol(Types.RuleProtocol.TCP)
                 .build();
         rules.add(rule);
      }
      if (inboundPorts.length > 0) {
         FirewallPolicy rule = api.firewallPolicyApi().create(FirewallPolicy.CreateFirewallPolicy.create(name + " firewall policy", "desc", rules));
         firewallPolicyId = rule.id();
      }

      // provision server
      Server server = null;
      try {
         String hardwareId = hardware.getId().split(",")[0];
         org.apache.jclouds.oneandone.rest.domain.Hardware.CreateHardware hardwareRequest
                 = org.apache.jclouds.oneandone.rest.domain.Hardware.CreateHardware.create(hardwareId, null, null, null, null, null);
         final Server.CreateServer serverRequest = Server.CreateServer.builder()
                 .name(name)
                 .description(name)
                 .hardware(hardwareRequest)
                 .rsaKey(options.getPublicKey())
                 .firewallPolicyId(firewallPolicyId)
                 .password(privateKey == null ? password : null)
                 .applianceId(validImage.id())
                 .dataCenterId(dataCenterId)
                 .serverType(Types.ServerType.BAREMETAL)
                 .powerOn(Boolean.TRUE).build();

         logger.trace("<< provisioning server '%s'", serverRequest);

         server = api.serverApi().create(serverRequest);

         waitServerUntilAvailable.apply(server);

         updateServer = api.serverApi().get(server.id());

         logger.trace(">> provisioning complete for server. returned id='%s'", server.id());

      } catch (Exception ex) {
         logger.error(ex, ">> failed to provision server. rollbacking..");
         if (server != null) {
            destroyNode(server.id());
         }
         throw Throwables.propagate(ex);
      }

      LoginCredentials serverCredentials = LoginCredentials.builder()
              .user(loginUser)
              .password(password)
              .privateKey(privateKey)
              .build();

      return new NodeAndInitialCredentials<Server>(updateServer, updateServer.id(), serverCredentials);
   }

   private ServerAppliance getValidCloudImage(Image image) {
      //check if image is of Type IMAGE
      //find a same OS appliance of the correct type
      List<ServerAppliance> allImages = api.serverApplianceApi().list();
      for (ServerAppliance app : allImages) {
         if (app.type() == Types.ApplianceType.IMAGE
                 && (app.osVersion() == null ? image.getOperatingSystem() == null : app.osVersion().contains(image.getOperatingSystem().getVersion()))
                 && Integer.toString(app.osArchitecture()).equals(image.getOperatingSystem().getArch())
                 && (app.osImageType() == Types.OSImageType.STANDARD || app.osImageType() == Types.OSImageType.MINIMAL)) {
            return app;
         }
      }
      return null;
   }

   private ServerAppliance getValidBaremetalImage(Image image) {
      //check if image is of Type IMAGE
      //find a same OS appliance of the correct type
      List<ServerAppliance> allImages = api.serverApplianceApi().list();
      for (ServerAppliance app : allImages) {
         if (app.type() == Types.ApplianceType.BAREMETAL
                 && (app.osVersion() == null ? image.getOperatingSystem() == null : app.osVersion().contains(image.getOperatingSystem().getVersion()))
                 && Integer.toString(app.osArchitecture()).equals(image.getOperatingSystem().getArch())) {
            return app;
         }
      }
      return null;
   }

   @Override
   public List<HardwareFlavour> listHardwareProfiles() {
      List<HardwareFlavour> result = new ArrayList<HardwareFlavour>();
      List<HardwareFlavour> cloudModels = api.serverApi().listHardwareFlavours();
      for (HardwareFlavour flavor : cloudModels) {
         result.add(flavor);
      }
      List<BareMetalModel> baremetalModels = api.serverApi().listBaremetalModels();
      List<HardwareFlavour.Hardware.Hdd> hdds = new ArrayList<HardwareFlavour.Hardware.Hdd>();
      for (BareMetalModel model : baremetalModels) {
         for (BareMetalModel.Hardware.Hdd bmHdd : model.hardware().hdds()) {
            HardwareFlavour.Hardware.Hdd toAdd = HardwareFlavour.Hardware.Hdd.create(bmHdd.unit(), bmHdd.size(), bmHdd.isMain());
            hdds.add(toAdd);
         }
         HardwareFlavour.Hardware hardware = HardwareFlavour.Hardware.create(null, model.hardware().core(), model.hardware().coresPerProcessor(), model.hardware().ram(), hdds);
         HardwareFlavour flavor = HardwareFlavour.create(model.id(), model.name(), hardware);
         result.add(flavor);
      }
      return result;
   }

   @Override
   public Iterable<SingleServerAppliance> listImages() {
      List<ServerAppliance> list = api.serverApplianceApi().list();
      List<SingleServerAppliance> results = new ArrayList<SingleServerAppliance>();
      for (ServerAppliance appliance : list) {
         results.add(SingleServerAppliance.builder()
                 .id(appliance.id())
                 .name(appliance.name())
                 .availableDataCenters(appliance.availableDataCenters())
                 .osInstallationBase(appliance.osInstallationBase())
                 .osFamily(appliance.osFamily())
                 .os(appliance.os())
                 .osVersion(appliance.osVersion())
                 .osArchitecture(appliance.osArchitecture())
                 .osImageType(appliance.osImageType())
                 .minHddSize(appliance.minHddSize())
                 .type(appliance.type())
                 .serverTypeCompatibility(appliance.serverTypeCompatibility())
                 .state(appliance.state())
                 .version(appliance.version())
                 .categories(appliance.categories())
                 .build());
      }
      return results;
   }

   @Override
   public SingleServerAppliance getImage(String id) {
      // try search images
      logger.trace("<< searching for image with id=%s", id);
      GenericQueryOptions options = new GenericQueryOptions();
      options.options(0, 0, null, id, null);
      try {
         List<ServerAppliance> list = api.serverApplianceApi().list(options);
         if (list.size() > 0) {
            ServerAppliance appliance = list.get(0);
            List<String> availableDatacenters = new ArrayList<String>();
            for (String dcId : appliance.availableDataCenters()) {
               availableDatacenters.add(dcId);
            }
            SingleServerAppliance image = SingleServerAppliance.create(appliance.id(), appliance.name(), availableDatacenters, appliance.osInstallationBase(),
                    appliance.osFamily(), appliance.os(), appliance.osVersion(), appliance.osArchitecture(), appliance.osImageType(), appliance.minHddSize(),
                    appliance.type(), appliance.state(), appliance.version(), appliance.categories(), appliance.serverTypeCompatibility());
            logger.trace(">> found image [%s].", image.name());
            return image;
         }
      } catch (Exception ex) {
         throw new ResourceNotFoundException("No image with id '" + id + "' was found");
      }
      throw new ResourceNotFoundException("No image with id '" + id + "' was found");
   }

   @Override
   public Iterable<DataCenter> listLocations() {
      return api.dataCenterApi().list();
   }

   @Override
   public Server getNode(String id) {
      return api.serverApi().get(id);
   }

   @Override
   public void destroyNode(String id) {
      checkState(cleanupResources.cleanupNode(id), "server(%s) and its resources still there after deleting!?", id);
   }

   @Override
   public void rebootNode(String id) {
      Server srv = api.serverApi().get(id);
      if (srv.status().state() == Types.ServerState.DEPLOYING) {
         return;
      }
      waitServerUntilAvailable.apply(getNode(id));
      api.serverApi().updateStatus(id, Server.UpdateStatus.create(Types.ServerAction.REBOOT, Types.ServerActionMethod.HARDWARE, false, null));
      waitServerUntilAvailable.apply(getNode(id));
   }

   @Override
   public void resumeNode(String id) {
      api.serverApi().updateStatus(id, Server.UpdateStatus.create(Types.ServerAction.POWER_ON, Types.ServerActionMethod.HARDWARE, false, null));
      waitServerUntilAvailable.apply(getNode(id));
   }

   @Override
   public void suspendNode(String id) {
      waitServerUntilAvailable.apply(getNode(id));
      api.serverApi().updateStatus(id, Server.UpdateStatus.create(Types.ServerAction.POWER_OFF, Types.ServerActionMethod.HARDWARE, false, null));
   }

   @Override
   public Iterable<Server> listNodes() {
      return api.serverApi().list();
   }

   @Override
   public Iterable<Server> listNodesByIds(final Iterable<String> ids) {
      return filter(listNodes(), new Predicate<Server>() {
         @Override
         public boolean apply(Server server) {
            return contains(ids, server);
         }
      });
   }

}
