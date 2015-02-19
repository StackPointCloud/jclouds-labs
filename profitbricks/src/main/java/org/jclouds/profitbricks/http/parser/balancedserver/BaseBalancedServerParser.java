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
package org.jclouds.profitbricks.http.parser.balancedserver;

import org.jclouds.date.DateCodec;
import org.jclouds.date.DateCodecFactory;
import org.jclouds.profitbricks.domain.BalancedServer;
import org.jclouds.profitbricks.http.parser.BaseProfitBricksResponseHandler;

public abstract class BaseBalancedServerParser<T> extends BaseProfitBricksResponseHandler<T> {

    protected BalancedServer.Builder builder;
    protected final DateCodec dateCodec;

    public BaseBalancedServerParser(DateCodecFactory dateCodec) {
        this.dateCodec = dateCodec.iso8601();
        builder = BalancedServer.builder();
    }

    @Override
    protected void setPropertyOnEndTag(String qName) {
        if ("activate".equals(qName)) {
            builder.activate(textToBooleanValue());
        }
        if ("balancedNicId".equals(qName)) {
            builder.balancedNicId(textToStringValue());
        }
        if ("serverId".equals(qName)) {
            builder.serverId(textToStringValue());
        }
        if ("serverName".equals(qName)) {
            builder.serverName(textToStringValue());
        }
    }

}
