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
package org.jclouds.profitbricks.http.parser.loadbalancer;

import com.google.common.collect.Lists;
import java.util.Date;
import java.util.List;
import org.jclouds.date.DateCodec;
import org.jclouds.date.DateCodecFactory;
import org.jclouds.profitbricks.domain.BalancedServer;
import org.jclouds.profitbricks.domain.Firewall;
import org.jclouds.profitbricks.domain.LoadBalancer;
import org.jclouds.profitbricks.domain.LoadBalancerAlgorithm;
import org.jclouds.profitbricks.domain.ProvisioningState;
import org.jclouds.profitbricks.http.parser.BaseProfitBricksResponseHandler;
import org.jclouds.profitbricks.http.parser.balancedserver.BalancedServerResponseHandler;
import org.jclouds.profitbricks.http.parser.firewall.FirewallResponseHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public abstract class BaseLoadBalancerResponseHandler<T> extends BaseProfitBricksResponseHandler<T> {

    protected final BalancedServerResponseHandler balancedServerResponseHandler;
    protected final FirewallResponseHandler firewallResponseHandler;
    protected final List<BalancedServer> balancedServers;
    protected final List<Firewall> firewalls;

    protected LoadBalancer.Builder builder;
    protected final DateCodec dateCodec;

    protected boolean useBalancedServerParser = false;
    protected boolean useFirewallParser = false;

    protected BaseLoadBalancerResponseHandler(DateCodecFactory dateCodec, BalancedServerResponseHandler balancedServerResponseHandler, FirewallResponseHandler firewallResponseHandler) {
        this.dateCodec = dateCodec.iso8601();
        this.builder = LoadBalancer.builder();

        this.balancedServerResponseHandler = balancedServerResponseHandler;
        this.firewallResponseHandler = firewallResponseHandler;

        this.balancedServers = Lists.newArrayList();
        this.firewalls = Lists.newArrayList();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if ("balancedServers".equals(qName)) {
            useBalancedServerParser = true;
        }
        if ("firewall".equals(qName)) {
            useFirewallParser = true;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (useBalancedServerParser) {
            balancedServerResponseHandler.characters(ch, start, length);
        }
        if (useFirewallParser) {
            firewallResponseHandler.characters(ch, start, length);
        } else {
            super.characters(ch, start, length);
        }
    }

    protected final Date textToIso8601Date() {
        return dateCodec.toDate(textToStringValue());
    }

    @Override
    protected void setPropertyOnEndTag(String qName) {
        if ("loadBalancerId".equals(qName)) {
            builder.id(textToStringValue());
        } else if ("loadBalancerName".equals(qName)) {
            builder.name(textToStringValue());
        } else if ("loadBalancerAlgorithm".equals(qName)) {
            builder.loadBalancerAlgorithm(LoadBalancerAlgorithm.fromValue(textToStringValue()));
        } else if ("dataCenterId".equals(qName)) {
            builder.dataCenterId(textToStringValue());
        } else if ("dataCenterVersion".equals(qName)) {
            builder.dataCenterVersion(textToStringValue());
        } else if ("internetAccess".equals(qName)) {
            builder.internetAccess(textToBooleanValue());
        } else if ("ip".equals(qName)) {
            builder.ip(textToStringValue());
        } else if ("lanId".equals(qName)) {
            builder.lanId(textToStringValue());
        } else if ("provisioningState".equals(qName)) {
            builder.provisioningState(ProvisioningState.fromValue(textToStringValue()));
        } else if ("creationTime".equals(qName)) {
            builder.creationTime(textToIso8601Date());
        } else if ("lastModificationTime".equals(qName)) {
            builder.lastModificationTime(textToIso8601Date());
        }
    }
}
