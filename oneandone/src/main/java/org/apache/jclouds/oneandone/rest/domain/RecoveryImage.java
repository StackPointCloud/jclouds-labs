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
package org.apache.jclouds.oneandone.rest.domain;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jclouds.javax.annotation.Nullable;
import org.jclouds.json.SerializedNames;

@AutoValue
public abstract class RecoveryImage {

   public abstract String id();

   public abstract String name();

   @Nullable
   public abstract List<String> availableDataCenters();
   
   public abstract Os os();

   @SerializedNames({"id", "name", "available_datacenters", "os"})
   public static RecoveryImage create(String id, String name, List<String> availableDataCenters, Os os) {
      return new AutoValue_RecoveryImage(id, name, availableDataCenters == null ? ImmutableList.<String>of() : ImmutableList.copyOf(availableDataCenters), os);
   }

   @AutoValue
   public abstract static class Os {

      public abstract int architecture();

      public abstract String family();

      public abstract String name();

      public abstract String subfamily();

      @SerializedNames({"architecture", "family", "name", "subfamily"})
      public static Os create(int architecture, String family, String name, String subfamily) {
         return new AutoValue_RecoveryImage_Os(architecture, family, name, subfamily);
      }
   }

}
