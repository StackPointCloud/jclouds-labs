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
package org.apache.jclouds.oneandone.rest.compute.config;

import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Predicate;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.jclouds.oneandone.rest.OneAndOneApi;
import org.apache.jclouds.oneandone.rest.compute.OneandoneComputeServiceAdapter;
import org.apache.jclouds.oneandone.rest.compute.function.DataCenterToLocation;
import org.apache.jclouds.oneandone.rest.compute.function.HardwareFlavourToHardware;
import org.apache.jclouds.oneandone.rest.compute.function.HddToVolume;
import org.apache.jclouds.oneandone.rest.compute.function.ServerToNodeMetadata;
import org.apache.jclouds.oneandone.rest.compute.function.SingleServerApplianceToImage;
import static org.apache.jclouds.oneandone.rest.config.OneAndOneProperties.POLL_PREDICATE_SERVER;
import org.apache.jclouds.oneandone.rest.domain.DataCenter;
import org.apache.jclouds.oneandone.rest.domain.HardwareFlavour;
import org.apache.jclouds.oneandone.rest.domain.Hdd;
import org.apache.jclouds.oneandone.rest.domain.Server;
import org.apache.jclouds.oneandone.rest.domain.SingleServerAppliance;
import org.apache.jclouds.oneandone.rest.domain.Types;
import org.jclouds.compute.ComputeServiceAdapter;
import org.jclouds.compute.config.ComputeServiceAdapterContextModule;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_NODE_SUSPENDED;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Volume;
import org.jclouds.compute.domain.internal.ArbitraryCpuRamTemplateBuilderImpl;
import org.jclouds.compute.domain.internal.TemplateBuilderImpl;
import org.jclouds.compute.functions.NodeAndTemplateOptionsToStatement;
import org.jclouds.compute.functions.NodeAndTemplateOptionsToStatementWithoutPublicKey;
import org.jclouds.compute.reference.ComputeServiceConstants.PollPeriod;
import org.jclouds.compute.reference.ComputeServiceConstants.Timeouts;
import org.jclouds.domain.Location;
import org.jclouds.util.PasswordGenerator;
import static org.jclouds.util.Predicates2.retry;

public class OneAndOneComputeServiceContextModule extends
        ComputeServiceAdapterContextModule<Server, HardwareFlavour, SingleServerAppliance, DataCenter> {

   @Override
   protected void configure() {
      super.configure();

      install(new LocationsFromComputeServiceAdapterModule<Server, HardwareFlavour, SingleServerAppliance, DataCenter>() {
      });

      bind(new TypeLiteral<ComputeServiceAdapter<Server, HardwareFlavour, SingleServerAppliance, DataCenter>>() {
      }).to(OneandoneComputeServiceAdapter.class);

      bind(TemplateBuilderImpl.class).to(ArbitraryCpuRamTemplateBuilderImpl.class);

      bind(NodeAndTemplateOptionsToStatement.class).to(NodeAndTemplateOptionsToStatementWithoutPublicKey.class);

      bind(new TypeLiteral<Function<Server, NodeMetadata>>() {
      }).to(ServerToNodeMetadata.class);

      bind(new TypeLiteral<Function<SingleServerAppliance, Image>>() {
      }).to(SingleServerApplianceToImage.class);

      bind(new TypeLiteral<Function<Hdd, Volume>>() {
      }).to(HddToVolume.class);

      bind(new TypeLiteral<Function<HardwareFlavour, Hardware>>() {
      }).to(HardwareFlavourToHardware.class);

      bind(new TypeLiteral<Function<DataCenter, Location>>() {
      }).to(DataCenterToLocation.class);
   }
   
   @Provides
   @Singleton
   protected PasswordGenerator.Config providePasswordGenerator() {
      // Guest passwords must contain more than 8 characters using upper case letters,
      // numbers and other special symbols.
      return new PasswordGenerator()
            .lower().min(2).max(5)
            .upper().min(2).max(5)
            .numbers().min(2).max(5)
            .symbols().min(2).max(5);
   }

   @Provides
   @Singleton
   @Named(POLL_PREDICATE_SERVER)
   Predicate<Server> provideServerAvailablePredicate(final OneAndOneApi api, Timeouts timeouts, PollPeriod pollPeriod) {
      return retry(new ServerAvailablePredicate(api),
              timeouts.nodeRunning, pollPeriod.pollInitialPeriod, pollPeriod.pollMaxPeriod, TimeUnit.SECONDS);
   }

   static class ServerAvailablePredicate implements Predicate<Server> {

      private final OneAndOneApi api;

      public ServerAvailablePredicate(OneAndOneApi api) {
         this.api = checkNotNull(api, "api must not be null");
      }

      @Override
      public boolean apply(Server server) {
         checkNotNull(server, "Server");
         server = api.serverApi().get(server.id());
         return !((server.status().state() != Types.ServerState.POWERED_OFF
                 && server.status().state() != Types.ServerState.POWERED_ON)
                 || server.status().percent() != 0);
      }
   }
   
   @Provides
   @Singleton
   @Named(TIMEOUT_NODE_SUSPENDED)
   Predicate<Server> provideServerDeletedPredicate(final OneAndOneApi api, Timeouts timeouts, PollPeriod pollPeriod) {
      return retry(new ServerDeletedPredicate(api),
              timeouts.nodeRunning, pollPeriod.pollInitialPeriod, pollPeriod.pollMaxPeriod, TimeUnit.SECONDS);
   }
   static class ServerDeletedPredicate implements Predicate<Server> {
      private final OneAndOneApi api;
      public ServerDeletedPredicate(OneAndOneApi api) {
         this.api = checkNotNull(api, "api must not be null");
      }
      @Override
      public boolean apply(Server server) {
         checkNotNull(server, "Server");
         Server result = api.serverApi().get(server.id());
         return result == null;
      }
   }
}
