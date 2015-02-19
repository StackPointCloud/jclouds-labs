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
package org.jclouds.profitbricks.features;

import com.google.common.collect.Iterables;
import java.util.List;
import org.assertj.core.util.Lists;
import org.jclouds.profitbricks.BaseProfitBricksLiveTest;
import org.jclouds.profitbricks.domain.DataCenter;
import org.jclouds.profitbricks.domain.LoadBalancer;
import org.jclouds.profitbricks.domain.LoadBalancerAlgorithm;
import org.testng.Assert;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

@Test(groups = "unit", testName = "LoadBalancerApiLiveTest")
public class LoadBalancerApiLiveTest extends BaseProfitBricksLiveTest {

    String dataCenterId = "8900ec7a-f39a-430f-9f18-dcf3f381a471";
    String loadBalancerID = "db2ad881-ef74-4f9c-b681-78b4302f3a14";
    String serverId = "ce48c54a-3e61-482a-8345-988431893607";

    @Override
    protected void initialize() {
        super.initialize();
        List<DataCenter> dataCenters = api.dataCenterApi().getAllDataCenters();
        assertFalse(dataCenters.isEmpty(), "Must atleast have 1 datacenter available for server testing.");

        dataCenterId = Iterables.getFirst(dataCenters, null).id();
    }

    @Test
    public void testCreateLoadBalancer() {
        LoadBalancer.Request.CreatePayload payload = LoadBalancer.Request.creatingBuilder()
                .dataCenterId(dataCenterId)
                .loadBalancerName("testName")
                .loadBalancerAlgorithm(LoadBalancerAlgorithm.ROUND_ROBIN)
                .ip("0.0.0.1")
                .lanId("1")
                .serverIds(serverId)
                .build();

        LoadBalancer loadBalancer = api.loadBalancerApi().createLoadBalancer(payload);

        System.out.println(loadBalancer);
        assertNotNull(loadBalancer);

    }

    @Test
    public void testGetAllLoadBalancers() {
        List<LoadBalancer> loadBalancers = api.loadBalancerApi().getAllLoadBalancers();

        assertNotNull(loadBalancers);
        assertFalse(loadBalancers.isEmpty());
    }

    @Test
    public void testGetLoadBalancer() {
        LoadBalancer loadBalancer = api.loadBalancerApi().getLoadBalancer(loadBalancerID);

        System.out.println(loadBalancer);
        assertNotNull(loadBalancer);
    }

    @Test
    public void testRegisterLoadBalancer() {
        List<String> serverIds = Lists.newArrayList();
        serverIds.add(serverId);

        LoadBalancer.Request.RegisterPayload payload = LoadBalancer.Request.registerBuilder()
                .id(loadBalancerID)
                .serverIds(serverIds)
                .build();

        LoadBalancer loadBalancer = api.loadBalancerApi().registerLoadBalancer(payload);

        assertNotNull(loadBalancer);
    }

    @Test
    public void testDeregisterLoadBalancer() {
        List<String> serverIds = Lists.newArrayList();
        serverIds.add(serverId);

        LoadBalancer.Request.DeregisterPayload payload = LoadBalancer.Request.deregisterBuilder()
                .id(loadBalancerID)
                .serverIds(serverIds)
                .build();

        LoadBalancer loadBalancer = api.loadBalancerApi().deregisterLoadBalancer(payload);

        assertNotNull(loadBalancer);
    }

    @Test
    public void testUpdateLoadBalancer() {
        LoadBalancer.Request.UpdatePayload payload = LoadBalancer.Request.updatingBuilder()
                .id(loadBalancerID)
                .loadBalancerName("whatever")
                .build();

        LoadBalancer loadBalancer = api.loadBalancerApi().updateLoadBalancer(payload);

        assertNotNull(loadBalancer);
    }

    @AfterClass(alwaysRun = true)
    public void testDeleteLoadBalancer() {
        boolean result = api.loadBalancerApi().deleteLoadbalancer(loadBalancerID);

        Assert.assertTrue(result);
    }
}
