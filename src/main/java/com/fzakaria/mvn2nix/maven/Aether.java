package com.fzakaria.mvn2nix.maven;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.lifecycle.LifeCyclePluginAnalyzer;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.eclipse.aether.util.graph.visitor.FilteringDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Friendly utility around the aether library
 */
public class Aether {

    private static final Logger LOGGER = LoggerFactory.getLogger(Aether.class);

    /**
     * Artifact type for maven plugins.
     */
    private static final String MAVEN_PLUGIN_ARTIFACT_TYPE = "maven-plugin";

    private final RepositorySystem system;
    private final ServiceLocator locator;

    public Aether(ServiceLocator locator, RepositorySystem system) {
        this.locator = locator;
        this.system = system;
    }

    public List<Artifact> resolveTransitiveDependenciesFromPom(File pom) throws DependencyCollectionException {
        DefaultRepositorySystemSession session = Bootstrap.newRepositorySystemSession(system);
        final Model model = Bootstrap.getEffectiveModel(locator, pom);

        Set<Artifact> solution = new HashSet<>();


        CollectRequest request = new CollectRequest();
        request.setRepositories(Bootstrap.newRemoteRepositories());

        if (model.getDependencyManagement() != null) {
            List<Dependency> managedDependencies = model
                    .getDependencyManagement()
                    .getDependencies()
                    .stream()
                    .map(d -> RepositoryUtils.toDependency(d, session.getArtifactTypeRegistry()))
                    .collect(Collectors.toList());
            request.setManagedDependencies(managedDependencies);
        }

        List<Dependency> dependencies = model.getDependencies()
                .stream()
                .map(d -> RepositoryUtils.toDependency(d, session.getArtifactTypeRegistry()))
                .collect(Collectors.toList());
        request.setDependencies(dependencies);

        // Add all dependencies immediate to the solution set
        solution.addAll(dependencies.stream().map(Dependency::getArtifact).collect(Collectors.toList()));

        CollectResult result = system.collectDependencies(session, request);

        PreorderNodeListGenerator orderVisitor = new PreorderNodeListGenerator();
        FilteringDependencyVisitor filteringVisitor = new FilteringDependencyVisitor(
                orderVisitor,
                new ScopeDependencyFilter(Lists.newArrayList(JavaScopes.RUNTIME, JavaScopes.COMPILE), Collections.emptyList())
        );
        result.getRoot().accept(filteringVisitor);
        solution.addAll(orderVisitor.getArtifacts(true));

        return Lists.newArrayList(solution);
    }

    /**
     * Resolves the transitive dependencies necessary for the pom.xml file to function
     * Each plugin is treated as "runtime" scope; and the full closure for each plugin is further
     * restricted to only runtime dependencies.
     *
     * @param pom The pom file to read
     * @return The list of artifacts
     * @throws DependencyCollectionException
     */
    public List<Artifact> resolveTransitivePluginDependenciesFromPom(File pom) throws DependencyCollectionException {
        DefaultRepositorySystemSession session = Bootstrap.newRepositorySystemSession(system);
        ArtifactType artifactType = session.getArtifactTypeRegistry().get(MAVEN_PLUGIN_ARTIFACT_TYPE);

        final Model model = Bootstrap.getEffectiveModel(locator, pom);

        Set<Artifact> solution = new HashSet<>();

        List<Plugin> plugins = model.getBuild().getPlugins();

        /*
         * Maven includes some default plugins always!
         */
        List<Plugin> defaultPlugins = Bootstrap.defaultPlugins();
        plugins.addAll(defaultPlugins);

        for (Plugin plugin : Sets.newHashSet(plugins)) {
            Artifact artifact = new DefaultArtifact(plugin.getGroupId(),
                    plugin.getArtifactId(),
                    artifactType.getClassifier(),
                    artifactType.getExtension(),
                    plugin.getVersion(),
                    artifactType);

            CollectRequest request = new CollectRequest();
            request.setRepositories(Bootstrap.newRemoteRepositories());
            request.setRoot(new Dependency(artifact, JavaScopes.RUNTIME));

            if (model.getBuild().getPluginManagement() != null) {
                List<Dependency> managedPlugins = model
                        .getBuild()
                        .getPluginManagement()
                        .getPlugins()
                        .stream()
                        .map(p -> {
                            return new DefaultArtifact(p.getGroupId(),
                                    p.getArtifactId(),
                                    artifactType.getClassifier(),
                                    artifactType.getExtension(),
                                    p.getVersion(),
                                    artifactType);
                        })
                        .map(a -> new Dependency(a, JavaScopes.RUNTIME))
                        .collect(Collectors.toList());
                request.setManagedDependencies(managedPlugins);
            }

            for (org.apache.maven.model.Dependency dependency : plugin.getDependencies()) {
                Dependency pluginDep =
                        RepositoryUtils.toDependency(dependency, session.getArtifactTypeRegistry());
                if (!JavaScopes.SYSTEM.equals(pluginDep.getScope())) {
                    pluginDep = pluginDep.setScope(JavaScopes.RUNTIME);
                }
                request.addDependency(pluginDep);
            }


            CollectResult result = system.collectDependencies(session, request);

            PreorderNodeListGenerator orderVisitor = new PreorderNodeListGenerator();
            FilteringDependencyVisitor filteringVisitor = new FilteringDependencyVisitor(
                    orderVisitor,
                    new ScopeDependencyFilter(Lists.newArrayList(JavaScopes.RUNTIME, JavaScopes.COMPILE), Collections.emptyList())
            );

            result.getRoot().accept(filteringVisitor);
            solution.addAll(orderVisitor.getArtifacts(true));
        }

        return Lists.newArrayList(solution);
    }

}
