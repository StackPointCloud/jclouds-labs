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
package org.apache.jclouds.profitbricks.rest.compute.function;

import java.util.regex.Pattern;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import org.apache.jclouds.profitbricks.rest.domain.LicenceType;
import org.apache.jclouds.profitbricks.rest.domain.Provisionable;
import org.apache.jclouds.profitbricks.rest.domain.Snapshot;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.ImageBuilder;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.domain.Location;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import org.apache.jclouds.profitbricks.rest.domain.State;

public class ProvisionableToImage implements Function<Provisionable, Image> {

   private final ImageToImage fnImageToImage;
   private final SnapshotToImage fnSnapshotToImage;

   @Inject
   ProvisionableToImage(Function<org.apache.jclouds.profitbricks.rest.domain.Location, Location> fnRegion) {
      this.fnImageToImage = new ImageToImage(fnRegion);
      this.fnSnapshotToImage = new SnapshotToImage(fnRegion);
   }

   @Override
   public Image apply(Provisionable input) {
      checkNotNull(input, "Cannot convert null input");

      if (input instanceof org.apache.jclouds.profitbricks.rest.domain.Image)
         return fnImageToImage.apply((org.apache.jclouds.profitbricks.rest.domain.Image) input);

      else if (input instanceof Snapshot)
         return fnSnapshotToImage.apply((Snapshot) input);

      else
         throw new UnsupportedOperationException("No implementation found for provisionable of concrete type '"
                 + input.getClass().getCanonicalName() + "'");
   }

   private static OsFamily mapOsFamily(LicenceType osType) {
      if (osType == null)
         return OsFamily.UNRECOGNIZED;
      switch (osType) {
         case WINDOWS:
            return OsFamily.WINDOWS;
         case LINUX:
            return OsFamily.LINUX;
         case UNRECOGNIZED:
         case OTHER:
         default:
            return OsFamily.UNRECOGNIZED;
      }
   }

   private static class ImageToImage implements Function<org.apache.jclouds.profitbricks.rest.domain.Image, Image> {

      private static final Pattern HAS_NUMBERS = Pattern.compile(".*\\d+.*");

      private final Function<org.apache.jclouds.profitbricks.rest.domain.Location, Location> fnRegion;

      ImageToImage(Function<org.apache.jclouds.profitbricks.rest.domain.Location, Location> fnRegion) {
         this.fnRegion = fnRegion;
      }

      @Override
      public Image apply(org.apache.jclouds.profitbricks.rest.domain.Image from) {
         String desc = from.properties().name();
         OsFamily osFamily = parseOsFamily(desc, from.properties().licenceType());

         OperatingSystem os = OperatingSystem.builder()
                 .description(osFamily.value())
                 .family(osFamily)
                 .version(parseVersion(desc))
                 .is64Bit(is64Bit(desc, from.properties().imageType()))
                 .build();

         return new ImageBuilder()
                 .ids(from.id())
                 .name(desc)
                 .location(fnRegion.apply(from.properties().location()))
                 .status(Image.Status.AVAILABLE)
                 .operatingSystem(os)
                 .build();
      }

      private OsFamily parseOsFamily(String from, LicenceType fallbackValue) {
         if (from != null)
            try {
               // ProfitBricks images names are usually in format:
               // [osType]-[version]-[subversion]-..-[date-created]
               String desc = from.toUpperCase().split("-")[0];
               OsFamily osFamily = OsFamily.fromValue(desc);
               checkArgument(osFamily != OsFamily.UNRECOGNIZED);

               return osFamily;
            } catch (Exception ex) {
               // do nothing
            }
         return mapOsFamily(fallbackValue);
      }

      private String parseVersion(String from) {
         if (from != null) {
            String[] split = from.toLowerCase().split("-");
            if (split.length >= 2) {
               int i = 1; // usually on second token
               String version = split[i];
               while (!HAS_NUMBERS.matcher(version).matches())
                  version = split[++i];
               return version;
            }
         }
         return "";
      }

      private boolean is64Bit(String from, org.apache.jclouds.profitbricks.rest.domain.Image.Type type) {
         switch (type) {
            case CDROM:
               if (!Strings.isNullOrEmpty(from))
                  return from.matches("x86_64|amd64");
            case HDD: // HDD provided by ProfitBricks are always 64-bit
            default:
               return true;
         }
      }
   }

   private static class SnapshotToImage implements Function<Snapshot, Image> {

      private final Function<org.apache.jclouds.profitbricks.rest.domain.Location, Location> fnRegion;

      SnapshotToImage(Function<org.apache.jclouds.profitbricks.rest.domain.Location, Location> fnRegion) {
         this.fnRegion = fnRegion;
      }

      @Override
      public Image apply(Snapshot from) {
         String textToParse = from.properties().name() + from.properties().description();
         OsFamily osFamily = parseOsFamily(textToParse, from.properties().licenceType());

         OperatingSystem os = OperatingSystem.builder()
                 .description(osFamily.value())
                 .family(osFamily)
                 .is64Bit(true)
                 .version("00.00")
                 .build();

         return new ImageBuilder()
                 .ids(from.id())
                 .name(from.properties().name())
                 .description(from.properties().description())
                 .location(fnRegion.apply(from.properties().location()))
                 .status(mapStatus(State.fromValue(from.metadata().state().toString())))
                 .operatingSystem(os)
                 .build();
      }

      private OsFamily parseOsFamily(String text, LicenceType fallbackValue) {
         if (text != null)
            try {
               // Attempt parsing OsFamily by scanning name and description
               // @see ProfitBricksComputeServiceAdapter#L190
               OsFamily[] families = OsFamily.values();
               for (OsFamily family : families)
                  if (text.contains(family.value()))
                     return family;
            } catch (Exception ex) {
               // do nothing
            }
         return mapOsFamily(fallbackValue);
      }

      static Image.Status mapStatus(State state) {
         if (state == null)
            return Image.Status.UNRECOGNIZED;
         switch (state) {
            case AVAILABLE:
               return Image.Status.AVAILABLE;
            case INACTIVE:
            case BUSY:
               return Image.Status.PENDING;
            default:
               return Image.Status.UNRECOGNIZED;
         }
      }
   }
}
