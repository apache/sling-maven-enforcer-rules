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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.enforcer.rule.api.EnforcerRule2;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.enforcer.AbstractNonCacheableEnforcerRule;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.logging.MessageBuilder;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.selector.StaticDependencySelector;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;

/** Checks that the runtime classpath (e.g. used by Maven Plugins via the
 * <a href="https://maven.apache.org/guides/mini/guide-maven-classloading.html#3-plugin-classloaders">Plugin Classloader</a>) contains all
 * provided dependencies (also the transitive ones).
 * 
 * As those are not transitively inherited they need to be declared explicitly in the pom.xml of the using Maven project.
 * 
 * This check is useful to make sure that a Maven Plugin has access to all necessary classes at run time. 
 */
public class RequireProvidedDependenciesInRuntimeClasspath
        extends AbstractNonCacheableEnforcerRule implements EnforcerRule2 {

    /** 
     * Specify the banned dependencies. This is a list of artifacts in format {@code <groupId>[:<artifactId>[:<extension>[:<classifier>]]]}. 
     * Excluded dependencies are not traversed, i.e. their transitive dependencies are not considered.
     * 
     * @see {@link #setExcludes(List)} */
    private List<String> excludes = null;

    /**
     * Whether to include optional dependencies in the check. Default = false.
     * 
     * @see {@link #setIncludeOptionalDependencies(boolean)}
     */
    private boolean includeOptionals = false;

    /**
     * Whether to include direct (i.e. non transitive) provided dependencies in the check. Default = false.
     * 
     * @see {@link #setIncludeDirectDependencies(boolean)}
     */
    private boolean includeDirects = false;

    @SuppressWarnings("unchecked")
    @Override
    public void execute(@Nonnull EnforcerRuleHelper helper) throws EnforcerRuleException {
        MavenProject project;
        DefaultRepositorySystemSession newRepoSession;
        RepositorySystem repoSystem;
        List<RemoteRepository> remoteRepositories;
        try {
            project = (MavenProject) helper.evaluate("${project}");
            // get a new session to be able to tweak the dependency selector
            newRepoSession = new DefaultRepositorySystemSession(
                    (RepositorySystemSession) helper.evaluate("${repositorySystemSession}"));
            remoteRepositories = (List<RemoteRepository>) helper.evaluate("${project.remoteProjectRepositories}");
            repoSystem = helper.getComponent(RepositorySystem.class);
        } catch (ExpressionEvaluationException eee) {
            throw new EnforcerRuleException("Unable to retrieve Maven project or repository system sesssion", eee);
        } catch (ComponentLookupException cle) {
            throw new EnforcerRuleException("Unable to retrieve component RepositorySystem", cle);
        }
        Log log = helper.getLog();
        
        Collection<DependencySelector> depSelectors = new ArrayList<>();
        depSelectors.add(new ScopeDependencySelector("test")); // exclude transitive and direct "test" dependencies of the rootDependency (i.e. the current project)
        // add also the exclude patterns
        if (excludes != null && !excludes.isEmpty()) {
            Collection<Exclusion> exclusions = excludes.stream().map(RequireProvidedDependenciesInRuntimeClasspath::convertPatternToExclusion).collect(Collectors.toCollection(LinkedList::new));
            exclusions.add(new Exclusion("*", "*", "*", "pom"));
            depSelectors.add(new ExclusionDependencySelector(exclusions));
        }
        if (!includeOptionals) {
            depSelectors.add(new OptionalDependencySelector());
        }
        if (!includeDirects) {
            depSelectors.add(new LevelAndScopeExclusionSelector(1, "provided"));
        }
        newRepoSession.setDependencySelector(new AndDependencySelector(depSelectors));

        // use the ones for https://maven.apache.org/guides/mini/guide-maven-classloading.html#3-plugin-classloaders
        @SuppressWarnings("deprecation")
        List<org.eclipse.aether.artifact.Artifact> runtimeArtifacts = project.getRuntimeArtifacts().stream().map(RepositoryUtils::toArtifact).collect(Collectors.toList());
        if (log.isDebugEnabled()) {
            log.debug("Collected " + runtimeArtifacts.size()+ " runtime dependencies ");
            for (Artifact runtimeArtifact : runtimeArtifacts) {
                log.debug(runtimeArtifact.toString());
            }
        }

        Dependency rootDependency = RepositoryUtils.toDependency(project.getArtifact(), null);
        try {
            DependencyNode rootDependencyNode = collectTransitiveDependencies(
                    rootDependency, repoSystem, newRepoSession, remoteRepositories, log);
            int numViolations = checkForMissingArtifacts(rootDependencyNode, runtimeArtifacts, log);
            if (numViolations > 0) {
                throw new EnforcerRuleException("Found " + numViolations + " missing runtime dependencies. Look at the warnings emitted above for the details.");
            }
        } catch (DependencyCollectionException e) {
            // draw graph
            StringWriter writer = new StringWriter();
            DependencyVisitor depVisitor = new TreeDependencyVisitor(
                    new DependencyVisitorPrinter(new PrintWriter(writer)));
            e.getResult().getRoot().accept(depVisitor);
            throw new EnforcerRuleException("Could not retrieve dependency metadata for project  : "
                    + e.getMessage() + ". Partial dependency tree: " + writer.toString(), e);
        }
    }

    /**
     * 
     * @param pattern string in in the format {@code <groupId>[:<artifactId>[:<extension>[:<classifier>]]]}
     * @return the exclusion
     */
    private static Exclusion convertPatternToExclusion(String pattern) {
        String[] parts = pattern.split(":");
        if (parts.length > 4) {
            throw new IllegalArgumentException("Pattern must contain at most three colons, but contains " + parts + ": " + pattern);
        }
        String groupId = parts[0];
        String artifactId = "*";
        String extension = "*";
        String classifier = "*";
        if (parts.length > 1) {
            artifactId = parts[1];
        }
        if (parts.length > 2) {
            extension = parts[2];
        }
        if (parts.length > 3) {
            classifier = parts[3];
        }
        return new Exclusion(groupId, artifactId, classifier, extension);
    }

    private static final class LevelAndScopeExclusionSelector implements DependencySelector {

        private final int targetLevel;
        private final String targetScope;
        private final int currentLevel;

        private static final DependencySelector ALL_SELECTOR = new StaticDependencySelector(true);

        public LevelAndScopeExclusionSelector(int targetLevel, String targetScope) {
            this(targetLevel, targetScope, 0);
        }

        private LevelAndScopeExclusionSelector(int targetLevel, String targetScope, int currentLevel) {
            this.targetLevel = targetLevel;
            this.targetScope = Objects.requireNonNull(targetScope);
            this.currentLevel = currentLevel;
        }

        @Override
        public boolean selectDependency(Dependency dependency) {
            if (currentLevel == targetLevel) {
                return !targetScope.equals(dependency.getScope());
            }
            return true;
        }

        @Override
        public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
            if (currentLevel < targetLevel) {
                return new LevelAndScopeExclusionSelector(targetLevel, targetScope, currentLevel+1);
            } else {
                // org.eclipse.aether:aether-util:jar:0.9.0.M2 used at runtime doesn't yet support null for no restrictions
                return ALL_SELECTOR;
            }
        }
    }

    private static final class DependencyVisitorPrinter implements DependencyVisitor {
        private final PrintWriter printWriter;
        private String indent = "";

        DependencyVisitorPrinter(PrintWriter printWriter) {
            this.printWriter = printWriter;
        }

        @Override
        public boolean visitEnter(DependencyNode dependencyNode) {
            String scope;
            if (dependencyNode.getDependency() != null) {
                scope = " (" + dependencyNode.getDependency().getScope() + ")";
            } else {
                scope = "";
            }
            printWriter.println(indent + dependencyNode.getArtifact() + scope);
            indent += "    ";
            return true;
        }

        @Override
        public boolean visitLeave(DependencyNode dependencyNode) {
            indent = indent.substring(0, indent.length() - 4);
            return true;
        }
    }

    private static final class MissingArtifactsDependencyVisitor implements DependencyVisitor {
        private final List<Artifact> artifacts;
        private final Log log;
        private int numMissingArtifacts;
        private final Deque<DependencyNode> nodeStack; // all intermediate nodes (without the root node)
        private boolean isRoot;

        MissingArtifactsDependencyVisitor(List<Artifact> artifacts, Log log) {
            this.artifacts = artifacts;
            this.log = log;
            numMissingArtifacts = 0;
            nodeStack = new ArrayDeque<>();
            isRoot = true;
        }

        @Override
        public boolean visitEnter(DependencyNode dependencyNode) {
            if (isRoot) {
                isRoot = false;
            } else {
                try {
                    if (!isCompatibleArtifactContainedInList(dependencyNode.getArtifact(), artifacts, log)) {
                        MessageBuilder msgBuilder = MessageUtils.buffer();
                        log.warn(msgBuilder.a("Dependency ").strong(dependencyNode.getDependency()).mojo(dumpIntermediatePath(nodeStack)).a(" not found as runtime dependency!").toString());
                        numMissingArtifacts++;
                    }
                } catch (InvalidVersionSpecificationException e) {
                    log.error("Invalid version given for artifact " + dependencyNode.getArtifact(), e);
                }
                nodeStack.addLast(dependencyNode);
            }
            return true;
        }

        @Override
        public boolean visitLeave(DependencyNode dependencyNode) {
            if (!nodeStack.isEmpty()) {
                nodeStack.removeLast();
            }
            return true;
        }

        public int getNumMissingArtifacts() {
            return numMissingArtifacts;
        }

        private static String dumpIntermediatePath(Collection<DependencyNode> path) {
            if (path.isEmpty()) {
                return "";
            }
            return " via " + path.stream()
                    .map(n -> n.getArtifact().toString())
                    .collect(Collectors.joining(" -> "));
        }
    }

    protected DependencyNode collectTransitiveDependencies(
            org.eclipse.aether.graph.Dependency rootDependency,
            RepositorySystem repoSystem, RepositorySystemSession repoSession,
            List<RemoteRepository> remoteRepositories, Log log)
            throws DependencyCollectionException {
        CollectRequest collectRequest = new CollectRequest(rootDependency, remoteRepositories);
        CollectResult collectResult = repoSystem.collectDependencies(repoSession, collectRequest);
        if (log.isDebugEnabled()) {
            // draw full dependency graph
            StringWriter writer = new StringWriter();
            DependencyVisitor depVisitor = new TreeDependencyVisitor(
                    new DependencyVisitorPrinter(new PrintWriter(writer)));
            collectResult.getRoot().accept(depVisitor);
            log.debug("dependency tree: " + writer.toString());
        }
        return collectResult.getRoot();
    }

    protected int checkForMissingArtifacts(DependencyNode rootDependencyNode, List<Artifact> runtimeArtifacts,
            Log log) {
        MissingArtifactsDependencyVisitor depVisitor = new MissingArtifactsDependencyVisitor(runtimeArtifacts, log);
        rootDependencyNode.accept(depVisitor);
        return depVisitor.getNumMissingArtifacts();
    }

    protected static boolean isCompatibleArtifactContainedInList(Artifact artifact,
            List<Artifact> artifacts, Log log) throws InvalidVersionSpecificationException {
        for (Artifact artifactInList : artifacts) {
            if (areArtifactsEqualDisregardingVersion(artifact, artifactInList)) {
                // check version compatibility
                if (isVersionCompatible(artifact.getVersion(), artifactInList.getVersion())) {
                    return true;
                } else {
                    MessageBuilder msgBuilder = MessageUtils.buffer();
                    log.warn("Found provided dependency " + msgBuilder.strong(artifact).a(" only with potentially incompatible version ").strong(artifactInList.getVersion()).toString() + " in runtime classpath");
                }
            }
        }
        return false;
    }

    protected static boolean isVersionCompatible(String requiredVersion, String providedVersion) throws InvalidVersionSpecificationException {
       ArtifactVersion provided = new DefaultArtifactVersion(providedVersion);
       VersionRange required = VersionRange.createFromVersionSpec(requiredVersion);
       
       // is it really a range?
       if (required.getRecommendedVersion() == null) {
           return required.containsVersion(provided);
       } else {
           // if only one version assume that versions with a higher minor version are compatible
           return required.getRecommendedVersion().getMajorVersion() == provided.getMajorVersion() && required.getRecommendedVersion().getMinorVersion() <= provided.getMinorVersion();
       }
    }

    /**
     * 
     * @param artifact1 one artifact
     * @param artifact2 the other artifact
     * @return {@code true} in case both artifact's groupId, artifactId, classifier and extension are equals, otherwise {@code false}
     */
    protected static boolean areArtifactsEqualDisregardingVersion(Artifact artifact1, Artifact artifact2) {
        return (artifact1.getArtifactId().equals(artifact2.getArtifactId())
                && artifact1.getGroupId().equals(artifact2.getGroupId())
                && Objects.toString(artifact2.getClassifier(), "").equals(Objects.toString(artifact1.getClassifier(), ""))
                && artifact1.getExtension().equals(artifact2.getExtension()));
    }

    public void setExcludes(List<String> theExcludes) {
        this.excludes = theExcludes;
    }

    public void setIncludeOptionalDependencies(boolean includeOptionals) {
        this.includeOptionals = includeOptionals;
    }

    public void setIncludeDirectDependencies(boolean includeDirects) {
        this.includeDirects = includeDirects;
    }
}