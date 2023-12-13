package com.fzakaria.mvn2nix.cmd;

import com.fzakaria.mvn2nix.maven.Artifact;
import com.fzakaria.mvn2nix.maven.Maven;
import com.fzakaria.mvn2nix.model.MavenArtifact;
import com.fzakaria.mvn2nix.model.MavenNixInformation;
import com.fzakaria.mvn2nix.model.URLAdapter;
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
import java.util.Collection;
import java.util.Map;
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

    public Maven2nix() {
        this.resolver = Maven2nix::doesUrlExist;
    }
    public Maven2nix(ArtifactResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Integer call() {
        LOGGER.info("Reading {}", file);

        final Maven maven = Maven.withTemporaryLocalRepository();
        maven.executeGoals(file, javaHome, goals);

        Collection<Artifact> artifacts = maven.collectAllArtifactsInLocalRepository();
        Map<String, MavenArtifact> dependencies = artifacts.parallelStream()
                .collect(Collectors.toMap(
                            Artifact::getCanonicalName,
                            artifact -> new MavenArtifact(artifactUrl(artifact), artifact.getLayout(), artifact.getSha256())));


        final MavenNixInformation information = new MavenNixInformation(dependencies);
        spec.commandLine().getOut().println(toPrettyJson(information));

        return 0;
    }

    private URL artifactUrl(Artifact artifact) {
        for (String repository : repositories) {
            URL url = getRepositoryArtifactUrl(artifact, repository);
            if (!resolver.doesExist(url)) {
                LOGGER.info("URL does not exist: {}", url);
                continue;
            }
            return url;
        }
        throw new RuntimeException(String.format("Could not find artifact %s in any repository", artifact));
    }

    @FunctionalInterface
    public interface ArtifactResolver {
        boolean doesExist(URL artifact);
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
