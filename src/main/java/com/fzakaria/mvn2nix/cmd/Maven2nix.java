package com.fzakaria.mvn2nix.cmd;

import com.fzakaria.mvn2nix.maven.Aether;
import com.fzakaria.mvn2nix.maven.Bootstrap;
import com.fzakaria.mvn2nix.model.MavenArtifact;
import com.fzakaria.mvn2nix.model.MavenNixInformation;
import com.fzakaria.mvn2nix.model.PrettyPrintNixVisitor;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.io.ByteStreams;
import org.apache.logging.log4j.util.Strings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(name = "mvn2nix", mixinStandardHelpOptions = true, version = "mvn2nix 0.1",
        description = "Converts Maven dependencies into a Nix expression.")
public class Maven2nix implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Maven2nix.class);

    @Mixin
    LoggingMixin loggingMixin;

    @Parameters(index = "0", paramLabel = "FILE", description = "The pom file to traverse.", defaultValue = "pom.xml")
    private File file = null;

    @Override
    public Integer call() throws Exception {
        LOGGER.info("Reading {}", file);

        ServiceLocator locator = Bootstrap.serviceLocator();

        RepositorySystem system = Bootstrap.newRepositorySystem(locator);

        Aether aether = new Aether(locator, system);

        /*
         * Collect all the artifacts
         */
        Set<Artifact> artifacts = new HashSet<>();
        artifacts.addAll(aether.resolveTransitiveDependenciesFromPom(file));
        artifacts.addAll(aether.resolveTransitivePluginDependenciesFromPom(file));

        /*
         * Transform each artifact into it's pom as well
         */
        artifacts = artifacts.stream().flatMap(artifact ->
                Stream.of(artifact, new SubArtifact(artifact, "", "pom")))
                .collect(Collectors.toSet());

        artifacts.addAll(
                aether.resolveParentPoms(file, artifacts)
        );

        RepositorySystemSession session = Bootstrap.newRepositorySystemSession(system);

        final MavenNixInformation information = new MavenNixInformation();

        for (Artifact artifact : artifacts) {

            /*
             * Try each remote repository. Early exit when found.
             */
            boolean found = false;
            for (RemoteRepository repository : Bootstrap.newRemoteRepositories()) {
                URL url = getRepositoryArtifactUrl(artifact, repository, session);
                if (!doesUrlExist(url)) {
                    continue;
                }
                registerArtifact(artifact, repository, session, information);
                found = true;
            }

            if (!found) {
                throw new RuntimeException(
                        String.format("Could not find %s in any remote repository.", artifact)
                );
            }
        }

        PrettyPrintNixVisitor visitor = new PrettyPrintNixVisitor(System.out);
        information.accept(visitor);

        return 0;
    }

    public static void registerArtifact(Artifact artifact,
                                        RemoteRepository repository,
                                        RepositorySystemSession session,
                                        MavenNixInformation information) {
        URL url = getRepositoryArtifactUrl(artifact, repository, session);
        String relativePath = session.getLocalRepositoryManager().getPathForRemoteArtifact(artifact, repository, "");
        information.addDependency(
                Aether.canonicalName(artifact),
                new MavenArtifact(url,
                        relativePath,
                        getSha256OfUrl(url)
                )
        );
    }

    public static URL getRepositoryArtifactUrl(Artifact artifact,
                                               RemoteRepository repository,
                                               RepositorySystemSession session) {
        String url = repository.getUrl();

        if (!url.endsWith("/")) {
            url += "/";
        }

        try {
            String relativePath = session.getLocalRepositoryManager().getPathForRemoteArtifact(artifact, repository, "");
            return new URL(url + relativePath);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not contact repository: " + url);
        }
    }

    public static String getSha256OfUrl(URL url) {
        try {
            URLConnection urlConnection = url.openConnection();
            if (!(urlConnection instanceof HttpURLConnection)) {
                throw new RuntimeException("The url is not of type http provided.");
            }

            HttpURLConnection connection = (HttpURLConnection) urlConnection;
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.connect();

            int code = connection.getResponseCode();
            if (code >= 400) {
                throw new RuntimeException("Getching the url failed with status code: " + code);
            }

            LOGGER.info("calculating sha256 for {}", url);

            final HashingInputStream inputStream = new HashingInputStream(Hashing.sha256(), connection.getInputStream());
            ByteStreams.exhaust(inputStream);

            return inputStream.hash().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Check whether a given URL
     *
     * @param url The URL for the pom file.
     * @return
     */
    public static boolean doesUrlExist(URL url) {
        try {
            URLConnection urlConnection = url.openConnection();
            if (!(urlConnection instanceof HttpURLConnection)) {
                return false;
            }

            HttpURLConnection connection = (HttpURLConnection) urlConnection;
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(true);
            connection.connect();

            int code = connection.getResponseCode();
            if (code == 200) {
                return true;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return false;
    }

}
