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
package org.apache.jclouds.profitbricks.rest.features;

import com.google.common.base.Predicate;
import java.util.List;
import org.apache.jclouds.profitbricks.rest.domain.DataCenter;
import org.apache.jclouds.profitbricks.rest.domain.State;
import org.apache.jclouds.profitbricks.rest.domain.FirewallRule;
import org.apache.jclouds.profitbricks.rest.domain.Nic;
import org.apache.jclouds.profitbricks.rest.domain.Server;
import org.apache.jclouds.profitbricks.rest.ids.ServerRef;
import org.apache.jclouds.profitbricks.rest.internal.BaseProfitBricksLiveTest;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

@Test(groups = "live", testName = "FirewallApiLiveTest")
public class FirewallApiLiveTest extends BaseProfitBricksLiveTest {
   
   DataCenter dataCenter;
   Server testServer;
   Nic testNic;
   FirewallRule testFirewallRule;
  
   @BeforeClass
   public void setupTest() {
      dataCenter = createDataCenter();
      assertDataCenterAvailable(dataCenter);
            
      testServer = api.serverApi().createServer(
         Server.Request.creatingBuilder()
            .dataCenterId(dataCenter.id())
            .name("jclouds-node")
            .cores(1)
            .ram(1024)
            .build());
      
      assertNodeAvailable(ServerRef.create(dataCenter.id(), testServer.id()));
            
      testNic = nicApi().create(
              Nic.Request.creatingBuilder()
              .dataCenterId(dataCenter.id())
              .serverId(testServer.id())
              .name("jclouds-nic")
              .lan(1)
              .build());

      assertNicAvailable(testNic);
   }
   
   @AfterClass(alwaysRun = true)
   public void teardownTest() {
      if (dataCenter != null)
         deleteDataCenter(dataCenter.id());
   }
     
   @Test
   public void testCreateFirewallRule() {
      assertNotNull(dataCenter);
            
      testFirewallRule = firewallApi().createFirewallRule(
              FirewallRule.Request.creatingBuilder()
              .dataCenterId(dataCenter.id())
              .serverId(testServer.id())
              .nicId(testNic.id())
              .name("jclouds-firewall")
              .protocol(FirewallRule.Protocol.TCP)
              .portRangeStart(1)
              .portRangeEnd(600)
              .build());

      assertNotNull(testFirewallRule);
      assertEquals(testFirewallRule.properties().name(), "jclouds-firewall");
      assertFirewallRuleAvailable(testFirewallRule);
   }
   

   @Test(dependsOnMethods = "testCreateFirewallRule")
   public void testGetFirewallRule() {
      FirewallRule firewallRule = firewallApi().getFirewallRule(dataCenter.id(), testServer.id(), testNic.id(), testFirewallRule.id());

      assertNotNull(firewallRule);
      assertEquals(firewallRule.id(), testFirewallRule.id());
   }

   @Test(dependsOnMethods = "testCreateFirewallRule")
   public void testListRules() {
      List<FirewallRule> firewalls = firewallApi().getRuleList(dataCenter.id(), testServer.id(), testNic.id());

      assertNotNull(firewalls);
      assertFalse(firewalls.isEmpty());
      assertEquals(firewalls.size(), 1);
   }
   
   @Test(dependsOnMethods = "testGetFirewallRule")
   public void testUpdateFirewallRule() {
      assertDataCenterAvailable(dataCenter);
      
      firewallApi().updateFirewallRule(FirewallRule.Request.updatingBuilder()
              .dataCenterId(testFirewallRule.dataCenterId())
              .serverId(testServer.id())
              .nicId(testNic.id())
              .id(testFirewallRule.id())
              .name("apache-firewall")
              .build());

      assertFirewallRuleAvailable(testFirewallRule);
      
      FirewallRule firewallRule = firewallApi().getFirewallRule(dataCenter.id(), testServer.id(), testNic.id(), testFirewallRule.id());
      
      assertEquals(firewallRule.properties().name(), "apache-firewall");
   }
   

   @Test(dependsOnMethods = "testUpdateFirewallRule")
   public void testDeleteFirewallRule() {
      firewallApi().deleteFirewallRule(testFirewallRule.dataCenterId(), testServer.id(), testNic.id(), testFirewallRule.id());
      assertFirewallRuleRemoved(testFirewallRule);
   } 
   
   private void assertFirewallRuleAvailable(FirewallRule firewallRule) {
      assertPredicate(new Predicate<FirewallRule>() {
         @Override
         public boolean apply(FirewallRule testRule) {
            FirewallRule firewallRule = firewallApi().getFirewallRule(testRule.dataCenterId(), testRule.serverId(), testRule.nicId(), testRule.id());
            
            if (firewallRule == null || firewallRule.metadata() == null)
               return false;
            
            return firewallRule.metadata().state() == State.AVAILABLE;
         }
      }, firewallRule);
   }
   
   private void assertFirewallRuleRemoved(FirewallRule firewallRule) {
      assertPredicate(new Predicate<FirewallRule>() {
         @Override
         public boolean apply(FirewallRule testRule) {
            return firewallApi().getFirewallRule(testRule.dataCenterId(), testRule.serverId(), testRule.nicId(), testRule.id()) == null;
         }
      }, firewallRule);
   }
     
   private FirewallApi firewallApi() {
      return api.firewallApi();
   }   

   private void assertNicAvailable(Nic nic) {
      assertPredicate(new Predicate<Nic>() {
         @Override
         public boolean apply(Nic testNic) {
            Nic nic = nicApi().get(testNic.dataCenterId(), testNic.serverId(), testNic.id());
            
            if (nic == null || nic.metadata() == null)
               return false;
            
            return nic.metadata().state() == State.AVAILABLE;
         }
      }, nic);
   }
           
   private NicApi nicApi() {
      return api.nicApi();
   }
   
   
}
