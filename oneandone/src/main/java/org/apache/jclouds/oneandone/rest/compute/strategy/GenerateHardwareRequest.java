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
package org.apache.jclouds.oneandone.rest.compute.strategy;

import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import org.apache.jclouds.oneandone.rest.OneAndOneApi;
import org.apache.jclouds.oneandone.rest.domain.Hdd;
import org.apache.jclouds.oneandone.rest.domain.SingleServerAppliance;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.Volume;
import org.jclouds.compute.util.ComputeServiceUtils;

@Singleton
public class GenerateHardwareRequest {

   private final OneAndOneApi api;
   private final Supplier<Map<String, ? extends Hardware>> hardwareFlavors;
   private final String baremetalModelsKey = "BMC";

   @Inject
   GenerateHardwareRequest(OneAndOneApi oneandoneapi, Supplier<Map<String, ? extends Hardware>> hardwareFlavors) {
      this.api = oneandoneapi;
      this.hardwareFlavors = hardwareFlavors;

   }

   public org.apache.jclouds.oneandone.rest.domain.Hardware.CreateHardware getHardwareRequest(Template template) {
      org.apache.jclouds.oneandone.rest.domain.Hardware.CreateHardware hardwareRequest = null;
      boolean baremetal = template.getHardware().getName() == null ? false : template.getHardware().getName().contains(baremetalModelsKey);

      final org.jclouds.compute.domain.Image image = template.getImage();
      Hardware hardware = template.getHardware();
      Hardware hardwareModel = isFlavor(hardware.getId());
      if (hardwareModel != null) {
         String hardwareId = hardwareModel.getId().split(",")[0];
         if (baremetal) {
            hardwareRequest = org.apache.jclouds.oneandone.rest.domain.Hardware.CreateHardware.create(hardwareId, null, null, null, null, null);
         } else {
            hardwareRequest = org.apache.jclouds.oneandone.rest.domain.Hardware.CreateHardware.create(null, hardwareId, null, null, null, null);
         }
      } else {
         //prepare hdds to provision
         List<? extends Volume> volumes = hardware.getVolumes();
         List<Hdd.CreateHdd> hdds = new ArrayList<Hdd.CreateHdd>();

         for (final Volume volume : volumes) {
            try {
               //check if the bootable device has enough size to run the appliance(image).
               double minHddSize = volume.getSize();
               if (volume.isBootDevice()) {
                  SingleServerAppliance checkSize = api.serverApplianceApi().get(image.getId());
                  if (checkSize.minHddSize() != null && checkSize.minHddSize() != 0) {
                     if (checkSize.minHddSize() > volume.getSize()) {
                        minHddSize = checkSize.minHddSize();
                     }
                  } else {
                     minHddSize = 20;
                  }
               }
               Hdd.CreateHdd hdd = Hdd.CreateHdd.create(minHddSize, volume.isBootDevice());
               hdds.add(hdd);
            } catch (Exception ex) {
               throw Throwables.propagate(ex);
            }
         }
         Double cores = ComputeServiceUtils.getCores(hardware);
         Double ram = (double) hardware.getRam();
         if (ram < 1024) {
            ram = 0.5;
         } else {
            ram = ram / 1024;
         }
         hardwareRequest = org.apache.jclouds.oneandone.rest.domain.Hardware.CreateHardware.create(null, null, cores, (double) 1, ram, hdds);
      }
      return hardwareRequest;
   }

   public Hardware isFlavor(String hardwareId) {
      return hardwareFlavors.get().get(hardwareId);
   }

}
