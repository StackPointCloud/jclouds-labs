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
package org.apache.jclouds.oneandone.rest.compute.options;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Objects;
import static com.google.common.base.Objects.equal;
import org.apache.jclouds.oneandone.rest.domain.Types;
import org.jclouds.compute.options.TemplateOptions;

/**
 * Custom options for the 1&1 API.
 */
public class OneandoneTemplateOptions extends TemplateOptions implements Cloneable {

   public Types.ServerType serverType = Types.ServerType.CLOUD;

   /**
    * Choose bare metal or cloud servers.
    */
   public OneandoneTemplateOptions serverType(Types.ServerType serverType) {
      this.serverType = serverType;
      return this;
   }

   public Types.ServerType getServerType() {
      return serverType;
   }

   @Override
   public OneandoneTemplateOptions clone() {
      OneandoneTemplateOptions options = new OneandoneTemplateOptions();
      copyTo(options);
      return options;
   }

   @Override
   public void copyTo(TemplateOptions to) {
      super.copyTo(to);
      if (to instanceof OneandoneTemplateOptions) {
         OneandoneTemplateOptions eTo = OneandoneTemplateOptions.class.cast(to);
         eTo.serverType(serverType);
      }
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(super.hashCode(), serverType);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (getClass() != obj.getClass()) {
         return false;
      }
      OneandoneTemplateOptions other = (OneandoneTemplateOptions) obj;
      return super.equals(other) && equal(this.serverType, other.serverType);
   }

   @Override
   public ToStringHelper string() {
      ToStringHelper toString = super.string().omitNullValues();
      toString.add("serverType", serverType);
      return toString;
   }

   public static class Builder {

      /**
       * @see OneandoneTemplateOptions#baremetalServer
       */
      public static OneandoneTemplateOptions baremetalServer(Types.ServerType serverType) {
         OneandoneTemplateOptions options = new OneandoneTemplateOptions();
         return options.serverType(serverType);
      }
   }
}
