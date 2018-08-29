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
import java.util.List;
import org.jclouds.json.SerializedNames;

@AutoValue
public abstract class BareMetalModel {

   public abstract String id();

   public abstract String name();

   public abstract Hardware hardware();

   @SerializedNames({"id", "name", "hardware"})
   public static BareMetalModel create(String id, String name, Hardware hardware) {
      return new AutoValue_BareMetalModel(id, name, hardware);
   }

   @AutoValue
   public abstract static class Hardware {

      public abstract double core();

      public abstract double coresPerProcessor();

      public abstract double ram();

      public abstract String unit();

      public abstract List<Hdd> hdds();

      @SerializedNames({"core", "cores_per_processor", "ram", "hdds", "unit"})
      public static Hardware create(double core, double coresPerProcessor, double ram, List<Hdd> hdds, String unit) {
         return new AutoValue_BareMetalModel_Hardware(core, coresPerProcessor, ram, unit, hdds);
      }

      @AutoValue
      public abstract static class Hdd {

         public abstract String unit();

         public abstract int size();

         public abstract boolean isMain();

         @SerializedNames({"unit", "size", "is_main"})
         public static Hdd create(String unit, int size, boolean isMain) {
            return new AutoValue_BareMetalModel_Hardware_Hdd(unit, size, isMain);
         }
      }
   }

}
