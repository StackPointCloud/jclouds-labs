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
package org.apache.jclouds.profitbricks.rest.binder.firewall;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Supplier;
import com.google.inject.Inject;
import java.net.URI;
import org.apache.jclouds.profitbricks.rest.binder.BaseProfitBricksRequestBinder;
import org.apache.jclouds.profitbricks.rest.domain.FirewallRule;
import org.jclouds.http.HttpRequest;
import org.jclouds.json.Json;
import org.jclouds.location.Provider;

public class UpdateFirewallRuleRequestBinder extends BaseProfitBricksRequestBinder<FirewallRule.Request.UpdatePayload> {

   private String dataCenterId;
   private String serverId;
   private String nicId;
   private String id;

   @Inject
   UpdateFirewallRuleRequestBinder(Json jsonBinder,  @Provider Supplier<URI> endpointSupplier) {
      super("firewallRule", jsonBinder, endpointSupplier);
   }

   @Override
   protected String createPayload(FirewallRule.Request.UpdatePayload payload) {

      checkNotNull(payload, "payload");
      checkNotNull(payload.dataCenterId(), "dataCenterId");
      checkNotNull(payload.serverId(), "serverId");
      checkNotNull(payload.nicId(), "nicId");
      checkNotNull(payload.id(), "id");
      
      dataCenterId = payload.dataCenterId();
      serverId = payload.serverId();
      nicId = payload.nicId();
      id = payload.id();
      
      if (payload.name() != null)
         requestBuilder.put("name", payload.name());
      
      if (payload.sourceMac() != null)
        requestBuilder.put("sourceMac", payload.sourceMac());

      if (payload.sourceIp() != null)
        requestBuilder.put("sourceIp", payload.sourceIp());

      if (payload.targetIp() != null)
        requestBuilder.put("targetIp", payload.targetIp());

      if (payload.icmpCode() != null)
        requestBuilder.put("icmpCode", payload.icmpCode());

      if (payload.icmpType() != null)
        requestBuilder.put("icmpType", payload.icmpType());

      if (payload.portRangeStart() != null)
        requestBuilder.put("portRangeStart", payload.portRangeStart());

      if (payload.portRangeEnd() != null)
        requestBuilder.put("portRangeEnd", payload.portRangeEnd());

      return jsonBinder.toJson(requestBuilder);
   }

   @Override
   protected <R extends HttpRequest> R createRequest(R fromRequest, String payload) {
      return super.createRequest(genRequest(String.format("datacenters/%s/servers/%s/nics/%s/firewallrules/%s", dataCenterId, serverId, nicId, id), fromRequest), payload);
   }

}

