package org.apache.sling.maven.enforcer;

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


import java.text.ChoiceFormat;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.maven.enforcer.rule.api.EnforcerRule2;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.InputLocation;
import org.apache.maven.plugins.enforcer.AbstractNonCacheableEnforcerRule;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.logging.MessageBuilder;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;

/** 
 * Checks that all dependencies have an explicitly declared scope in the non-effective pom (i.e. without taking inheritance or dependency management into account)/
 */
public class RequireExplicitDependencyScope extends AbstractNonCacheableEnforcerRule implements EnforcerRule2 {

    @Override
    public void execute(@Nonnull EnforcerRuleHelper helper) throws EnforcerRuleException {
        try {
            int numMissingDependencyScopes = 0;
            MavenProject project = (MavenProject) helper.evaluate("${project}");
            if (project == null) {
                throw new ExpressionEvaluationException("${project} is null");
            }
            List<Dependency> dependencies = project.getOriginalModel().getDependencies(); // this is the non-effective model but the original one without inheritance and interpolation resolved
            // check scope without considering inheritance
            for (Dependency dependency : dependencies) {
                helper.getLog().debug("Found dependency " + dependency);
                if (dependency.getScope() == null) {
                    MessageBuilder msgBuilder = MessageUtils.buffer();
                    helper.getLog().warn(msgBuilder.a("Dependency ").strong(dependency.getManagementKey()).a(" @ ").strong(formatLocation(dependency.getLocation(""))).a(" does not have an explicit scope defined!").toString());
                    numMissingDependencyScopes++;
                }
            }
            if (numMissingDependencyScopes > 0) {
                ChoiceFormat scopesFormat = new ChoiceFormat("1#scope|1<scopes");
                throw new EnforcerRuleException("Found " + numMissingDependencyScopes + " missing dependency " + scopesFormat.format(numMissingDependencyScopes) + ". Look at the warnings emitted above for the details.");
            }
        } catch (ExpressionEvaluationException eee) {
            throw new EnforcerRuleException("Cannot resolve expression: " + eee.getCause(), eee);
        }
    }

    /**
     * Creates a string with line/column information for problems originating directly from this POM.
     * Inspired by <a href="https://github.com/apache/maven/blob/d82ab09ae106131609efb8b98b67dae17b0780d0/maven-model-builder/src/main/java/org/apache/maven/model/building/ModelProblemUtils.java#L136-L173">ModelProblemUtils.formatLocation(...)</a>
     *
     * @param location The location which should be formatted, must not be {@code null}.
     * @return The formatted problem location or an empty string if unknown, never {@code null}.
     */
    protected static String formatLocation(InputLocation location) {
        StringBuilder buffer = new StringBuilder();
        if (location.getLineNumber() > 0) {
            if (buffer.length() > 0) {
                buffer.append( ", " );
            }
            buffer.append("line ").append(location.getLineNumber());
        }
        if (location.getColumnNumber() > 0) {
            if (buffer.length() > 0) {
                buffer.append( ", " );
            }
            buffer.append("column ").append(location.getColumnNumber());
        }
        return buffer.toString();
    }

}
