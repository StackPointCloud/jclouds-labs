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
package org.apache.jclouds.profitbricks.rest.compute;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import java.util.Objects;
import org.apache.jclouds.profitbricks.rest.ProfitBricksApi;
import static org.apache.jclouds.profitbricks.rest.config.ProfitBricksComputeProperties.POLL_PREDICATE_DATACENTER;
import org.apache.jclouds.profitbricks.rest.domain.DataCenter;
import org.apache.jclouds.profitbricks.rest.domain.Location;
import org.apache.jclouds.profitbricks.rest.domain.options.DepthOptions;
import org.apache.jclouds.profitbricks.rest.features.DataCenterApi;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.internal.BaseComputeServiceLiveTest;
import org.jclouds.logging.config.LoggingModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;

@Test(groups = "live", singleThreaded = true, testName = "ProfitBricksComputeServiceLiveTest")
public class ProfitBricksComputeServiceLiveTest extends BaseComputeServiceLiveTest {

//    private static final String TEST_DC_NAME = "computeServiceLiveTest" + System.currentTimeMillis();
    private static final String TEST_DC_NAME = "computeServiceLiveTest";

    private DataCenter dataCenter;

    public ProfitBricksComputeServiceLiveTest() {
        provider = "profitbricks-rest";
//        System.setProperty("http.proxyHost", "127.0.0.1");
//        System.setProperty("https.proxyHost", "127.0.0.1");
//        System.setProperty("http.proxyPort", "8888");
//        System.setProperty("https.proxyPort", "8888");
//        System.setProperty("javax.net.ssl.trustStore", "C:\\Program Files\\Java\\jdk1.8.0_73\\jre\\lib\\security\\FiddlerKeystore");
//        System.setProperty("javax.net.ssl.trustStorePassword", "testme");
    }

    @BeforeGroups(groups = {"integration", "live"})
    @Override
    public void setupContext() {
        super.setupContext();

        final DataCenterApi api = dataCenterApi();
        final Predicate<String> predicate = getDataCenterPredicate();

        dataCenter = FluentIterable.from(api.list(new DepthOptions().depth(3))).firstMatch(new Predicate<DataCenter>() {

            @Override
            public boolean apply(DataCenter input) {
                boolean match = Objects.equals(input.properties().name(), TEST_DC_NAME);
                if (match && input.properties().location() == Location.US_LAS) {
                    return predicate.apply(input.id());
                }
                return match;
            }
        }).or(new Supplier<DataCenter>() {

            @Override
            public DataCenter get() {
                DataCenter dataCenter = api.create(TEST_DC_NAME, "desc,,,", Location.US_LAS.getId());
                predicate.apply(dataCenter.id());

                return api.getDataCenter(dataCenter.id());
            }
        });

    }

    @AfterClass(groups = {"integration", "live"}, alwaysRun = true)
    @Override
    protected void tearDownContext() {
        super.tearDownContext();
        if (dataCenter != null) {
            dataCenterApi().delete(dataCenter.id());
        }
    }

    private Predicate<String> getDataCenterPredicate() {
        return client.getContext().utils().injector().getInstance(Key.get(new TypeLiteral<Predicate<String>>() {
        }, Names.named(POLL_PREDICATE_DATACENTER)));
    }

    private DataCenterApi dataCenterApi() {
        return client.getContext().unwrapApi(ProfitBricksApi.class).dataCenterApi();
    }

    @Override
    protected Module getSshModule() {
        return new SshjSshClientModule();
    }

    @Override
    protected LoggingModule getLoggingModule() {
        return new SLF4JLoggingModule();
    }

    @Override
    public void testOptionToNotBlock() throws Exception {
        // ProfitBricks implementation intentionally blocks until the node is 'AVAILABLE'
    }

    @Override
    protected void checkTagsInNodeEquals(NodeMetadata node, ImmutableSet<String> tags) {
        // ProfitBricks doesn't support tags
    }

    @Override
    protected void checkUserMetadataContains(NodeMetadata node, ImmutableMap<String, String> userMetadata) {
        // ProfitBricks doesn't support user metadata
    }

    @Override
    protected void checkResponseEqualsHostname(ExecResponse execResponse, NodeMetadata node1) {
        // ProfitBricks doesn't support hostname
    }

    @Override
    protected void checkOsMatchesTemplate(NodeMetadata node) {
        // Not enough description from API to match template
    }

}
