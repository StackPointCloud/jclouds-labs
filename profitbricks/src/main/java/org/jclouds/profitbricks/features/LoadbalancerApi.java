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

import javax.inject.Named;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import org.jclouds.Fallbacks;
import org.jclouds.http.filters.BasicAuthentication;
import org.jclouds.profitbricks.binder.loadbalancer.CreateLoadbalancerRequestBinder;
import org.jclouds.profitbricks.binder.loadbalancer.DeregisterLoadbalancerRequestBinder;
import org.jclouds.profitbricks.binder.loadbalancer.RegisterLoadbalancerRequestBinder;
import org.jclouds.profitbricks.binder.loadbalancer.UpdateLoadbalancerRequestBinder;
import org.jclouds.profitbricks.domain.Loadbalancer;
import org.jclouds.profitbricks.http.filters.ProfitBricksSoapMessageEnvelope;
import org.jclouds.profitbricks.http.parser.loadbalancer.LoadbalancerListResponseHandler;
import org.jclouds.profitbricks.http.parser.loadbalancer.LoadbalancerResponseHandler;
import org.jclouds.rest.annotations.Fallback;
import org.jclouds.rest.annotations.MapBinder;
import org.jclouds.rest.annotations.Payload;
import org.jclouds.rest.annotations.PayloadParam;
import org.jclouds.rest.annotations.RequestFilters;
import org.jclouds.rest.annotations.XMLResponseParser;

@RequestFilters({BasicAuthentication.class, ProfitBricksSoapMessageEnvelope.class})
@Consumes(MediaType.TEXT_XML)
@Produces(MediaType.TEXT_XML)
public interface LoadbalancerApi {

    @POST
    @Named("loadbalancer:getall")
    @Payload("<ws:getAllLoadBalancers/>")
    @XMLResponseParser(LoadbalancerListResponseHandler.class)
    @Fallback(Fallbacks.EmptyListOnNotFoundOr404.class)
    List<Loadbalancer> getAllLoadBalancers();

    @POST
    @Named("loadbalancer:get")
    @Payload("<ws:getLoadBalancer><loadBalancerId>{id}</loadBalancerId></ws:getLoadBalancer>")
    @XMLResponseParser(LoadbalancerResponseHandler.class)
    @Fallback(Fallbacks.NullOnNotFoundOr404.class)
    Loadbalancer getLoadBalancer(@PayloadParam("id") String identifier);

    @POST
    @Named("loadbalancer:create")
    @MapBinder(CreateLoadbalancerRequestBinder.class)
    @XMLResponseParser(LoadbalancerResponseHandler.class)
    Loadbalancer createLoadBalancer(@PayloadParam("loadbalancer") Loadbalancer.Request.CreatePayload payload);

    @POST
    @Named("loadbalancer:register")
    @MapBinder(RegisterLoadbalancerRequestBinder.class)
    @XMLResponseParser(LoadbalancerResponseHandler.class)
    Loadbalancer registerLoadBalancer(@PayloadParam("loadbalancer") Loadbalancer.Request.RegisterPayload payload);

    @POST
    @Named("loadbalancer:Deregister")
    @MapBinder(DeregisterLoadbalancerRequestBinder.class)
    @XMLResponseParser(LoadbalancerResponseHandler.class)
    Loadbalancer deregisterLoadBalancer(@PayloadParam("loadbalancer") Loadbalancer.Request.DeregisterPayload payload);

    @POST
    @Named("loadbalancer:delete")
    @Payload("<ws:deleteLoadBalancer><loadBalancerId>{id}</loadBalancerId></ws:deleteLoadBalancer>")
    boolean deleteLoadbalancer(@PayloadParam("id") String id);

    @POST
    @Named("loadbalancer:update")
    @MapBinder(UpdateLoadbalancerRequestBinder.class)
    @XMLResponseParser(LoadbalancerResponseHandler.class)
    Loadbalancer updateLoadBalancer(@PayloadParam("loadbalancer") Loadbalancer.Request.UpdatePayload payload);
}
