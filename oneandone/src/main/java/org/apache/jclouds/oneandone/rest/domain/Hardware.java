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
import org.apache.jclouds.oneandone.rest.domain.Hdd.CreateHdd;
import org.jclouds.javax.annotation.Nullable;
import org.jclouds.json.SerializedNames;

@AutoValue
public abstract class Hardware {

   @Nullable
   public abstract String fixedInstanceSizeId();

   @Nullable
   public abstract String baremetalModelId();

   public abstract double vcore();

   public abstract double coresPerProcessor();

   public abstract double ram();

   public abstract List<Hdd> hdds();

   @SerializedNames({"fixed_instance_size_id", "baremetal_model_id", "vcore", "cores_per_processor", "ram", "hdds"})
   public static Hardware create(String fixedInstanceSizeId, String baremetalModelId, double vcore, double coresPerProcessor, double ram, List<Hdd> hdds) {
      return new AutoValue_Hardware(fixedInstanceSizeId, baremetalModelId, vcore, coresPerProcessor, ram, hdds);
   }

   @AutoValue
   public abstract static class CreateHardware {

      @Nullable
      public abstract String baremetalModelId();

      @Nullable
      public abstract String fixedInstanceSizeId();

      @Nullable
      public abstract Double vcore();

      @Nullable
      public abstract Double coresPerProcessor();

      @Nullable
      public abstract Double ram();

      @Nullable
      public abstract List<CreateHdd> hdds();

      @SerializedNames({"baremetal_model_id", "fixed_instance_size_id", "vcore", "cores_per_processor", "ram", "hdds"})
      public static CreateHardware create(String baremetalModelId, String fixedInstanceSizeId, Double vcore, Double coresPerProcessor, Double ram, List<CreateHdd> hdds) {
         return new AutoValue_Hardware_CreateHardware(baremetalModelId, fixedInstanceSizeId, vcore, coresPerProcessor, ram, hdds);
      }
   }

   @AutoValue
   public abstract static class UpdateHardware {

      public abstract double vcore();

      public abstract double coresPerProcessor();

      public abstract double ram();

      @SerializedNames({"vcore", "cores_per_processor", "ram"})
      public static UpdateHardware create(double vcore, double coresPerProcessor, double ram) {
         return new AutoValue_Hardware_UpdateHardware(vcore, coresPerProcessor, ram);
      }
   }

}
