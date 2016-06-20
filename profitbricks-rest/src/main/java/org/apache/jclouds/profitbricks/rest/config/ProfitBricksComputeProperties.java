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
package org.apache.jclouds.profitbricks.rest.config;

public class ProfitBricksComputeProperties {

   public static final String POLL_PREDICATE_DATACENTER = "jclouds.profitbricks.rest.predicate.datacenter";
   public static final String POLL_PREDICATE_SNAPSHOT = "jclouds.profitbricks.rest.predicate.snapshot";

   public static final String POLL_TIMEOUT = "jclouds.profitbricks.rest.poll.timeout";
   public static final String POLL_PERIOD = "jclouds.profitbricks.rest.operation.poll.initial-period";
   public static final String POLL_MAX_PERIOD = "jclouds.profitbricks.rest.operation.poll.max-period";
   
   public static final String TIMEOUT_NODE_RUNNING = "jclouds.profitbricks.rest.operation.poll.node.running";
   public static final String TIMEOUT_NODE_SUSPENDED = "jclouds.profitbricks.rest.operation.poll.node.suspended";

   private ProfitBricksComputeProperties() {
      throw new AssertionError("Intentionally unimplemented");
   }

}
