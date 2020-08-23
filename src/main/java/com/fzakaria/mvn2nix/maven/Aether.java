package com.fzakaria.mvn2nix.maven;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.logging.log4j.util.Strings;
import org.apache.maven.RepositoryUtils;
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
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.eclipse.aether.util.graph.visitor.FilteringDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
    private final DefaultRepositorySystemSession session;

    public Aether(ServiceLocator locator, RepositorySystem system) {
        this.locator = locator;
        this.system = system;
        this.session = Bootstrap.newRepositorySystemSession(system);
        // guard against mutating
        session.setReadOnly();
    }

    public List<RemoteRepository> remoteRepositories(File pom) {
        final Model model = Bootstrap.getEffectiveModel(locator, pom);
        List<RemoteRepository> repositories = model.getRepositories()
                .stream()
                .map(r -> Bootstrap.createRemoteRepository(r.getId(), null, r.getUrl()))
                .collect(Collectors.toList());
        repositories.add(Bootstrap.newCentralRepository());
        return repositories;
    }

    public Collection<Artifact> resolveParentPoms(File pom, Set<Artifact> artifacts) throws ArtifactResolutionException {

        Set<Artifact> parents = new HashSet<>();

        Map<String, Artifact> seenArtifacts = new HashMap<>();
        Queue<Artifact> queue = new ArrayDeque<>();
        queue.addAll(artifacts);

        while (!queue.isEmpty()) {

            Artifact artifact = queue.poll();

            // only look at the POM files
            if (!artifact.getExtension().equals("pom")) {
                continue;
            }

            // don't revisit seen artifacts
            if (seenArtifacts.containsKey(canonicalName(artifact))) {
                continue;
            }

            // only need to register parent artifacts
            seenArtifacts.put(canonicalName(artifact), artifact);

            // we will now resolve it
            ArtifactRequest request = new ArtifactRequest()
                    .setArtifact(artifact)
                    .setRepositories(remoteRepositories(pom));
            ArtifactResult result = system.resolveArtifact(session, request);

            /*
             * Parse this pom file to find any parents
             */
            final Model model = Bootstrap.getEffectiveModel(locator, result.getArtifact().getFile());

            // do nothing if no parent
            if (model.getParent() == null) {
                continue;
            }

            Artifact parent = new DefaultArtifact(model.getParent().getGroupId(),
                    model.getParent().getArtifactId(),
                    "pom",
                    model.getParent().getVersion());

            queue.add(parent);
            parents.add(parent);
        }

        return parents;
    }

    public List<Artifact> resolveTransitiveDependenciesFromPom(File pom) throws DependencyCollectionException {
        final Model model = Bootstrap.getEffectiveModel(locator, pom);

        Set<Artifact> solution = new HashSet<>();


        CollectRequest request = new CollectRequest();
        request.setRepositories(remoteRepositories(pom));

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
            request.setRepositories(remoteRepositories(pom));
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

    public static String canonicalName(Artifact artifact) {
        if (Strings.isBlank(artifact.getClassifier())) {
            return String.format("%s:%s:%s:%s",
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getExtension(),
                    artifact.getVersion());
        }
        return String.format("%s:%s:%s:%s:%s",
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getExtension(),
                artifact.getClassifier(),
                artifact.getVersion());
    }

}
