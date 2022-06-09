package org.apache.sling.maven.enforcer;

import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Assertions;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import org.junit.jupiter.api.Test;

class RequireProvidedDependenciesInRuntimeClasspathTest {

    @Test
    void testIsVersionCompatible() throws InvalidVersionSpecificationException {
        Assertions.assertTrue(RequireProvidedDependenciesInRuntimeClasspath.isVersionCompatible("1.0.0", "1.0.0"));
        Assertions.assertTrue(RequireProvidedDependenciesInRuntimeClasspath.isVersionCompatible("[,1.0.0]", "1.0.0"));
        Assertions.assertTrue(RequireProvidedDependenciesInRuntimeClasspath.isVersionCompatible("[1,2)", "1.1.0"));
        Assertions.assertTrue(RequireProvidedDependenciesInRuntimeClasspath.isVersionCompatible("1.0.0", "1.1.0"));
        Assertions.assertFalse(RequireProvidedDependenciesInRuntimeClasspath.isVersionCompatible("[1,2)", "2.0"));
        Assertions.assertFalse(RequireProvidedDependenciesInRuntimeClasspath.isVersionCompatible("1.1", "1.0"));
        Assertions.assertFalse(RequireProvidedDependenciesInRuntimeClasspath.isVersionCompatible("1.1", "2.0"));
    }

    @Test
    void testAreArtifactsEqualDisregardingVersion() {
        Assertions.assertTrue(RequireProvidedDependenciesInRuntimeClasspath.areArtifactsEqualDisregardingVersion(new DefaultArtifact("myArtifact:myGroup:1.0.0"), new DefaultArtifact("myArtifact:myGroup:2.0.0")));
        Assertions.assertTrue(RequireProvidedDependenciesInRuntimeClasspath.areArtifactsEqualDisregardingVersion(new DefaultArtifact("myArtifact:myGroup:myClassifier:myExtension:1.0.0"), new DefaultArtifact("myArtifact:myGroup:myClassifier:myExtension:2.0.0")));
        Assertions.assertFalse(RequireProvidedDependenciesInRuntimeClasspath.areArtifactsEqualDisregardingVersion(new DefaultArtifact("myArtifact:myGroup:myClassifier:myExtension:1.0.0"), new DefaultArtifact("myArtifact:myGroup:myClassifier:myExtension1:2.0.0")));
        Assertions.assertFalse(RequireProvidedDependenciesInRuntimeClasspath.areArtifactsEqualDisregardingVersion(new DefaultArtifact("myArtifact:myGroup:myClassifier:myExtension:1.0.0"), new DefaultArtifact("myArtifact:myGroup:myClassifier1:myExtension:2.0.0")));
    }
}
