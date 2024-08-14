package com.fzakaria.mvn2nix.cmd;

import com.fzakaria.mvn2nix.maven.Artifact;
import com.fzakaria.mvn2nix.maven.Maven;
import com.fzakaria.mvn2nix.model.MavenArtifact;
import com.fzakaria.mvn2nix.model.MavenNixInformation;
import com.fzakaria.mvn2nix.model.URLAdapter;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.io.ByteStreams;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "mvn2nix", mixinStandardHelpOptions = true, version = "mvn2nix 0.1",
        description = "Converts Maven dependencies into a Nix expression.")
public class Maven2nix implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Maven2nix.class);

    @Spec
    CommandSpec spec;

    @Mixin
    LoggingMixin loggingMixin;

    @Parameters(index = "0", paramLabel = "FILE", description = "The pom file to traverse.", defaultValue = "pom.xml")
    private File file = null;

    @Option(names = "--goals",
            arity = "0..*",
            description = "The goals to execute for maven to collect dependencies. Defaults to ${DEFAULT-VALUE}",
            defaultValue = "package")
    private String[] goals;

    @Option(names = "--repositories",
            arity = "0..*",
            description = "The maven repositories to try fetching artifacts from. Defaults to ${DEFAULT-VALUE}",
            defaultValue = "https://repo.maven.apache.org/maven2/")
    private String[] repositories;

    @Option(names = "--jdk",
            arity = "0..*",
            description = "The JDK to use when running Maven",
            defaultValue = "${java.home}")
    private File javaHome;
    
    private ArtifactResolver resolver;

    private ArtifactAnalysis analysis;

    public Maven2nix() {
        this.resolver = Maven2nix::sha256OfUrl;
        this.analysis = this::collectArtifactsFromTempLocalRepository;
    }

    public Maven2nix(ArtifactResolver resolver, ArtifactAnalysis analysis) {
        this.resolver = resolver;
        this.analysis = analysis;
    }

    @Override
    public Integer call() {
        LOGGER.info("Reading {}", file);
        spec.commandLine().getOut().println(toPrettyJson(mavenNixInformation(resolver, analysis, repositories)));
        return 0;
    }

    static MavenNixInformation mavenNixInformation(
            ArtifactResolver resolver,
            ArtifactAnalysis analysis,
            String[] repositories
    ) {
        Collection<Artifact> artifacts = analysis.analyze();
        Map<String, MavenArtifact> dependencies = artifacts.parallelStream()
                .collect(Collectors.toMap(
                        Artifact::getCanonicalName,
                        artifact -> new MavenArtifact(artifactUrl(resolver, repositories, artifact), artifact.getLayout(), artifact.getSha256())));
        return new MavenNixInformation(dependencies);
    }

    private static URL artifactUrl(ArtifactResolver resolver, String[] repositories, Artifact artifact) {
        return Arrays.stream(repositories)
                .map(r -> getRepositoryArtifactUrl(artifact, r))
                .filter(u -> resolver.sha256(u).map(artifact.getSha256()::equals).orElse(false))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("Could not find artifact %s in any repository", artifact)));
    }

    @FunctionalInterface
    interface ArtifactAnalysis {
        /**
         * @return The artifacts needed to run the goals on the given pom.
         */
        Collection<Artifact> analyze();
    }

    @FunctionalInterface
    public interface ArtifactResolver {
        /**
         * @return The sha256 of the file referred to by the given URL or
         * {@link Optional#empty()} if the URL does not exist.
         */
        Optional<String> sha256(URL artifact);
    }


    private Collection<Artifact> collectArtifactsFromTempLocalRepository() {
        final Maven maven = Maven.withTemporaryLocalRepository();
        maven.executeGoals(file, javaHome, goals);
        return maven.collectAllArtifactsInLocalRepository();
    }

    /**
     * Convert this object to a pretty JSON representation.
     *
     * The artifacts will appear in the order induced by {@link String#compareTo(String)}.
     */
    public static String toPrettyJson(MavenNixInformation information) {
        final Moshi moshi = new Moshi.Builder()
                .add(new URLAdapter())
                .build();
        JsonAdapter<MavenNixInformation> jsonAdapter = moshi
                .adapter(MavenNixInformation.class)
                .indent("  ")
                .nonNull();
        return jsonAdapter.toJson(information);
    }

    public static URL getRepositoryArtifactUrl(Artifact artifact, String repository) {
        String url = repository;
        if (!url.endsWith("/")) {
            url += "/";
        }
        try {
            return new URL(url + artifact.getLayout());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not contact repository: " + url);
        }
    }

    public static Optional<String> sha256OfUrl(URL url) {
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
                throw new RuntimeException("Fetching the url failed with status code: " + code);
            }
            var inputStream = new HashingInputStream(Hashing.sha256(), connection.getInputStream());
            ByteStreams.exhaust(inputStream);
            return Optional.of(inputStream.hash().toString());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

}
