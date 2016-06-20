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

import static org.testng.Assert.assertEquals;

import java.net.URI;

import org.jclouds.domain.Location;
import org.jclouds.domain.LocationBuilder;
import org.jclouds.domain.LocationScope;
import org.jclouds.location.suppliers.all.JustProvider;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.okhttp.mockwebserver.MockResponse;
import org.apache.jclouds.profitbricks.rest.ProfitBricksProviderMetadata;
import org.apache.jclouds.profitbricks.rest.domain.DataCenter;
import org.apache.jclouds.profitbricks.rest.internal.BaseProfitBricksApiMockTest;

@Test(groups = "unit", testName = "DataCenterToLocationTest", singleThreaded = true)
public class DataCenterToLocationTest extends BaseProfitBricksApiMockTest {

   private DataCenterToLocation fnLocation;
   private LocationToLocation fnRegion;

   @BeforeTest
   public void setup() {
      ProfitBricksProviderMetadata metadata = new ProfitBricksProviderMetadata();
      JustProvider justProvider = new JustProvider(metadata.getId(), Suppliers.<URI>ofInstance(
              URI.create(metadata.getEndpoint())), ImmutableSet.<String>of());
      this.fnRegion = new LocationToLocation(justProvider);
      this.fnLocation = new DataCenterToLocation(fnRegion);
   }

   @Test
   public void testDataCenterToLocation() {
      server.enqueue(
         new MockResponse().setBody(stringFromResource("/compute/datacenter.json"))
      );
      
      DataCenter dataCenter = api.dataCenterApi().getDataCenter("some-id");
     
      Location actual = fnLocation.apply(dataCenter);

      Location expected = new LocationBuilder()
              .id(dataCenter.id())
              .description(dataCenter.properties().name())
              .scope(LocationScope.ZONE)
              .metadata(ImmutableMap.<String, Object>of(
                              "version", dataCenter.properties().version(),
                              "state", dataCenter.metadata().state()))
              .parent(fnRegion.apply(org.apache.jclouds.profitbricks.rest.domain.Location.DE_FRA))
              .build();

      assertEquals(actual, expected);
   }

}
