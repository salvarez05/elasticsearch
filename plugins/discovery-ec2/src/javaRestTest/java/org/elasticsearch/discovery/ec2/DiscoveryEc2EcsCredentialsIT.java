/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.discovery.ec2;

import fixture.aws.DynamicAwsCredentials;
import fixture.aws.ec2.AwsEc2HttpFixture;
import fixture.aws.imds.Ec2ImdsHttpFixture;
import fixture.aws.imds.Ec2ImdsServiceBuilder;
import fixture.aws.imds.Ec2ImdsVersion;

import org.elasticsearch.discovery.DiscoveryModule;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.List;
import java.util.Set;

public class DiscoveryEc2EcsCredentialsIT extends DiscoveryEc2ClusterFormationTestCase {

    private static final String PREFIX = getIdentifierPrefix("DiscoveryEc2EcsCredentialsIT");
    private static final String REGION = PREFIX + "-region";
    private static final String CREDENTIALS_ENDPOINT = "/ecs_credentials_endpoint_" + PREFIX;

    private static final DynamicAwsCredentials dynamicCredentials = new DynamicAwsCredentials(REGION, "ec2");

    private static final Ec2ImdsHttpFixture ec2ImdsHttpFixture = new Ec2ImdsHttpFixture(
        new Ec2ImdsServiceBuilder(Ec2ImdsVersion.V1).newCredentialsConsumer(dynamicCredentials::addValidCredentials)
            .alternativeCredentialsEndpoints(Set.of(CREDENTIALS_ENDPOINT))
    );

    private static final AwsEc2HttpFixture ec2ApiFixture = new AwsEc2HttpFixture(
        dynamicCredentials::isAuthorized,
        DiscoveryEc2EcsCredentialsIT::getAvailableTransportEndpoints
    );

    private static final ElasticsearchCluster cluster = ElasticsearchCluster.local()
        .nodes(2)
        .plugin("discovery-ec2")
        .setting(DiscoveryModule.DISCOVERY_SEED_PROVIDERS_SETTING.getKey(), Ec2DiscoveryPlugin.EC2_SEED_HOSTS_PROVIDER_NAME)
        .setting("logger." + AwsEc2SeedHostsProvider.class.getCanonicalName(), "DEBUG")
        .setting(Ec2ClientSettings.ENDPOINT_SETTING.getKey(), ec2ApiFixture::getAddress)
        .environment("AWS_CONTAINER_CREDENTIALS_FULL_URI", () -> ec2ImdsHttpFixture.getAddress() + CREDENTIALS_ENDPOINT)
        .environment("AWS_REGION", REGION)
        .build();

    private static List<String> getAvailableTransportEndpoints() {
        return cluster.getAvailableTransportEndpoints();
    }

    @ClassRule
    public static TestRule ruleChain = RuleChain.outerRule(ec2ImdsHttpFixture).around(ec2ApiFixture).around(cluster);

    @Override
    protected ElasticsearchCluster getCluster() {
        return cluster;
    }
}
