/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.c2c.test.samples;

import io.ballerina.c2c.KubernetesConstants;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES_SELECTOR_KEY;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.deployK8s;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getExposedPorts;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.loadImage;

/**
 * Test cases for sample 3.
 */
public class Sample3Test extends SampleTest {

    private static final Path SOURCE_DIR_PATH = SAMPLE_DIR.resolve("kubernetes-resources-autoscaling");
    private static final Path DOCKER_TARGET_PATH =
            SOURCE_DIR_PATH.resolve("target").resolve(DOCKER).resolve("scaling");
    private static final Path KUBERNETES_TARGET_PATH =
            SOURCE_DIR_PATH.resolve("target").resolve(KUBERNETES).resolve("scaling");
    private static final String DOCKER_IMAGE = "anuruddhal/math:sample3";
    private Deployment deployment;
    private Service service;
    private HorizontalPodAutoscaler hpa;

    @BeforeClass
    public void compileSample() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH)
                , 0);
        File artifactYaml = KUBERNETES_TARGET_PATH.resolve("scaling.yaml").toFile();
        Assert.assertTrue(artifactYaml.exists());
        KubernetesClient client = new KubernetesClientBuilder().build();
        List<HasMetadata> k8sItems = client.load(new FileInputStream(artifactYaml)).items();
        for (HasMetadata data : k8sItems) {
            switch (data.getKind()) {
                case "Deployment":
                    deployment = (Deployment) data;
                    break;
                case "Service":
                    service = (Service) data;
                    break;
                case "Secret":
                    break;
                case "HorizontalPodAutoscaler":
                    hpa = (HorizontalPodAutoscaler) data;
                    break;
                default:
                    Assert.fail("Unexpected k8s resource found: " + data.getKind());
                    break;
            }
        }
    }

    @Test
    public void validateDeployment() {
        Assert.assertNotNull(deployment);
        Assert.assertEquals(deployment.getMetadata().getName(), "scaling-deployment");
        Assert.assertEquals(deployment.getSpec().getReplicas().intValue(), 1);
        Assert.assertEquals(deployment.getMetadata().getLabels().get(KubernetesConstants
                .KUBERNETES_SELECTOR_KEY), "scaling");
        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().size(), 1);

        // Assert Containers
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assert.assertEquals(container.getImage(), DOCKER_IMAGE);
        Assert.assertEquals(container.getResources().getLimits().get("memory").toString(), "256Mi");
        Assert.assertEquals(container.getResources().getLimits().get("cpu").toString(), "500m");
        Assert.assertEquals(container.getResources().getRequests().get("cpu").toString(), "200m");
        Assert.assertEquals(container.getResources().getRequests().get("memory").toString(), "100Mi");
        Assert.assertEquals(container.getPorts().size(), 1);
    }

    @Test
    public void validateService() {
        Assert.assertNotNull(service);
        Assert.assertEquals(1, service.getMetadata().getLabels().size());
        Assert.assertEquals("scaling", service.getMetadata().getLabels().get(KUBERNETES_SELECTOR_KEY));
        Assert.assertEquals("scaling-svc", service.getMetadata().getName());
        Assert.assertEquals("ClusterIP", service.getSpec().getType());
        Assert.assertEquals(1, service.getSpec().getPorts().size());
        Assert.assertEquals(9090, service.getSpec().getPorts().get(0).getPort().intValue());
        Assert.assertEquals(9090, service.getSpec().getPorts().get(0).getTargetPort().getIntVal().intValue());
        Assert.assertEquals("TCP", service.getSpec().getPorts().get(0).getProtocol());
    }

    @Test
    public void validateHPA() {
        Assert.assertNotNull(hpa);
        Assert.assertEquals(1, hpa.getMetadata().getLabels().size());
        Assert.assertEquals("scaling", hpa.getMetadata().getLabels().get(KUBERNETES_SELECTOR_KEY));
        Assert.assertEquals("scaling-hpa", hpa.getMetadata().getName());
        Assert.assertEquals(5, hpa.getSpec().getMaxReplicas().intValue());
        Assert.assertEquals(2, hpa.getSpec().getMinReplicas().intValue());
        MetricSpec metricSpec = hpa.getSpec().getMetrics().get(0);
        Assert.assertEquals(metricSpec.getType(), "Resource");
        Assert.assertEquals(metricSpec.getResource().getName(), "cpu");
        Assert.assertEquals(metricSpec.getResource().getTarget().getAverageUtilization().intValue(), 50);
        Assert.assertEquals(metricSpec.getResource().getTarget().getType(), "Utilization");

        MetricSpec metricSpec1 = hpa.getSpec().getMetrics().get(1);
        Assert.assertEquals(metricSpec1.getType(), "Resource");
        Assert.assertEquals(metricSpec1.getResource().getName(), "memory");
        Assert.assertEquals(metricSpec1.getResource().getTarget().getAverageUtilization().intValue(), 60);
        Assert.assertEquals(metricSpec1.getResource().getTarget().getType(), "Utilization");
        Assert.assertEquals("Deployment", hpa.getSpec().getScaleTargetRef().getKind());
        Assert.assertEquals("scaling-deployment", hpa.getSpec().getScaleTargetRef().getName());

    }

    @Test
    public void validateDockerfile() {
        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());
    }

    @Test
    public void validateDockerImage() {
        List<String> ports = getExposedPorts(DOCKER_IMAGE);
        Assert.assertEquals(ports.size(), 1);
        Assert.assertEquals(ports.get(0), "9090/tcp");
    }

    @Test(groups = {"integration"})
    public void deploySample() throws IOException, InterruptedException {
        Assert.assertEquals(0, loadImage(DOCKER_IMAGE));
        Assert.assertEquals(0, deployK8s(KUBERNETES_TARGET_PATH));
        KubernetesTestUtils.deleteK8s(KUBERNETES_TARGET_PATH);
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(KUBERNETES_TARGET_PATH);
        KubernetesUtils.deleteDirectory(DOCKER_TARGET_PATH);
        KubernetesTestUtils.deleteDockerImage(DOCKER_IMAGE);
    }
}
