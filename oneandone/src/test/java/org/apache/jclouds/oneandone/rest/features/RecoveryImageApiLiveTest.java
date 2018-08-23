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

import java.util.List;
import org.apache.jclouds.oneandone.rest.domain.RecoveryImage;
import org.apache.jclouds.oneandone.rest.domain.SingleRecoveryImage;
import org.apache.jclouds.oneandone.rest.domain.options.GenericQueryOptions;
import org.apache.jclouds.oneandone.rest.internal.BaseOneAndOneLiveTest;
import org.testng.Assert;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

@Test(groups = "live", testName = "RecoveryImageApiLiveTest")
public class RecoveryImageApiLiveTest extends BaseOneAndOneLiveTest {

   private RecoveryImage recoveryImage;
   private List<RecoveryImage> recoveryImages;

   private RecoveryImageApi recoveryImageApi() {

      return api.recoveryImageApi();
   }

   @Test
   public void testList() {
      recoveryImages = recoveryImageApi().list();
      Assert.assertTrue(recoveryImages.size() > 0);
      recoveryImage = recoveryImages.get(0);
   }

   public void testListWithOption() {
      GenericQueryOptions options = new GenericQueryOptions();
      options.options(0, 0, null, "linux", null);
      List<RecoveryImage> resultWithQuery = recoveryImageApi().list(options);

      Assert.assertTrue(resultWithQuery.size() > 0);
   }

   @Test(dependsOnMethods = "testList")
   public void testGet() {
      SingleRecoveryImage result = recoveryImageApi().get(recoveryImage.id());

      assertEquals(result.id(), recoveryImage.id());
   }

}
