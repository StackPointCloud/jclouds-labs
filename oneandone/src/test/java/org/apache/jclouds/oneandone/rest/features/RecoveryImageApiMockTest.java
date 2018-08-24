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
package org.apache.jclouds.oneandone.rest.features;

import com.squareup.okhttp.mockwebserver.MockResponse;
import java.util.List;
import org.apache.jclouds.oneandone.rest.domain.RecoveryImage;
import org.apache.jclouds.oneandone.rest.domain.options.GenericQueryOptions;
import org.apache.jclouds.oneandone.rest.internal.BaseOneAndOneApiMockTest;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import org.testng.annotations.Test;

@Test(groups = "unit", testName = "RecoveryImageApiMockTest", singleThreaded = true)
public class RecoveryImageApiMockTest extends BaseOneAndOneApiMockTest {

   private RecoveryImageApi recoveryImageApi() {
      return api.recoveryImageApi();
   }

   @Test
   public void testList() throws InterruptedException {
      server.enqueue(
              new MockResponse().setBody(stringFromResource("/recoveryimages/recoveryimages.json"))
      );

      List<RecoveryImage> images = recoveryImageApi().list();

      assertNotNull(images);
      assertEquals(images.size(), 4);

      assertEquals(server.getRequestCount(), 1);
      assertSent(server, "GET", "/recovery_appliances");
   }

   @Test
   public void testList404() throws InterruptedException {
      server.enqueue(
              new MockResponse().setResponseCode(404));

      List<RecoveryImage> images = recoveryImageApi().list();

      assertEquals(images.size(), 0);

      assertEquals(server.getRequestCount(), 1);
      assertSent(server, "GET", "/recovery_appliances");
   }

   @Test
   public void testListWithOption() throws InterruptedException {
      server.enqueue(
              new MockResponse().setBody(stringFromResource("/recoveryimages/recoveryimages.options.json"))
      );
      GenericQueryOptions options = new GenericQueryOptions();
      options.options(0, 0, null, "linux", null);
      List<RecoveryImage> images = recoveryImageApi().list(options);

      assertNotNull(images);
      assertEquals(images.size(), 4);

      assertEquals(server.getRequestCount(), 1);
      assertSent(server, "GET", "/recovery_appliances?q=linux");
   }

   @Test
   public void testListWithOption404() throws InterruptedException {
      server.enqueue(
              new MockResponse().setResponseCode(404));
      GenericQueryOptions options = new GenericQueryOptions();
      options.options(0, 0, null, "linux", null);
      List<RecoveryImage> images = recoveryImageApi().list(options);

      assertEquals(images.size(), 0);

      assertEquals(server.getRequestCount(), 1);
      assertSent(server, "GET", "/recovery_appliances?q=linux");
   }

   @Test
   public void testGet() throws InterruptedException {
      server.enqueue(
              new MockResponse().setBody(stringFromResource("/recoveryimages/get.json"))
      );
      RecoveryImage result = recoveryImageApi().get("imageId");

      assertNotNull(result);
      assertEquals(server.getRequestCount(), 1);
      assertSent(server, "GET", "/recovery_appliances/imageId");
   }

   @Test
   public void testGet404() throws InterruptedException {
      server.enqueue(
              new MockResponse().setResponseCode(404));
      RecoveryImage result = recoveryImageApi().get("imageId");

      assertEquals(result, null);
      assertEquals(server.getRequestCount(), 1);
      assertSent(server, "GET", "/recovery_appliances/imageId");
   }
}
