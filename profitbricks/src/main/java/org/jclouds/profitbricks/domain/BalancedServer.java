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
package org.jclouds.profitbricks.domain;

import com.google.auto.value.AutoValue;
import org.jclouds.javax.annotation.Nullable;

@AutoValue
public abstract class BalancedServer {

    /*
     * An attribute in the balancedServers list. It denotes if the server is active or not.
     */
    @Nullable
    public abstract boolean activate();
    /*
     * The NIC ID associated with the load balancer.
     */

    @Nullable
    public abstract String balancedNicId();
    /*
     * The ID of the server being load balanced.
     */

    @Nullable

    public abstract String serverId();

    /*
     * The name of the load balanced server.
     */
    @Nullable
    public abstract String serverName();

    public static BalancedServer create(boolean activate, String blanacedNicId, String serverId, String serverName) {
        return new AutoValue_BalancedServer(activate, blanacedNicId, serverId, serverName);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return builder().fromBalancedServer(this);
    }

    public static class Builder {

        private boolean activate;
        private String balancedNicId;
        private String serverId;
        private String serverName;

        public Builder activate(boolean activate) {
            this.activate = activate;
            return this;
        }

        public Builder balancedNicId(String balancedNicId) {
            this.balancedNicId = balancedNicId;
            return this;
        }

        public Builder serverId(String serverId) {
            this.serverId = serverId;
            return this;
        }

        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public BalancedServer build() {
            return BalancedServer.create(activate, balancedNicId, serverId, serverName);
        }

        public Builder fromBalancedServer(BalancedServer in) {
            return this.activate(in.activate()).balancedNicId(in.balancedNicId()).serverId(in.serverId()).serverName(in.serverName());
        }
    }
}
