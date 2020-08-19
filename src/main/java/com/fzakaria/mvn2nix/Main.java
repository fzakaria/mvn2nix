package com.fzakaria.mvn2nix;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.io.ByteStreams;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenArtifactInfo;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.impl.maven.MavenWorkingSessionContainer;
import org.jboss.shrinkwrap.resolver.impl.maven.MavenWorkingSessionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    private static final String NIX_ATTR_TEMPLATE = "  \"%s\" = {\n" +
            "    url = \"%s\";\n" +
            "    layout = \"%s\";\n" +
            "    sha256 = \"%s\";\n" +
            "  };";

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);


    public static void main(String[] args) {
        /*
         * Fetch the pom.xml that is relative to current directory.
         */
        final File pomFile = Paths.get("", "pom.xml").toAbsolutePath().toFile();

        LOGGER.info("Reading {}", pomFile);

        final MavenResolverSystem resolver = Maven.resolver();
        /*
         * Resolve the pom.xml
         */
        final MavenResolvedArtifact[] artifacts = resolver
                .loadPomFromFile(pomFile)
                .importCompileAndRuntimeDependencies()
                .resolve()
                .withTransitivity()
                .asResolvedArtifact();

        /*
         * Peek inside and get the RemoteRepositories
         */
        MavenWorkingSessionImpl session = (MavenWorkingSessionImpl) ((MavenWorkingSessionContainer) resolver).getMavenWorkingSession();
        final List<RemoteRepository> repositories = getRemoteRepositories(session);

        /*
         * The start of our nix attribute set
         */
        System.out.println("{");

        /*
         * Go through every artifact
         */
        for (MavenArtifactInfo artifact : artifacts) {

            String canonical = artifact.getCoordinate().toCanonicalForm();

            String layout = getMavenCalculatedLayout(artifact.getCoordinate());

            /*
             * Find a valid URL for the artifact
             */
            URL url = repositories.stream()
                    .map(repository -> getRepositoryArtifactUrl(artifact, repository))
                    .filter(Main::doesUrlExist)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(artifact + " could not be found in any repository."));

            String sha256 = getSha256OfUrl(url);

            LOGGER.info("Resolved {} - {} - {}", canonical, url, sha256);

            System.out.println(String.format(NIX_ATTR_TEMPLATE, canonical, url, layout, sha256));
        }

        /*
         * The end of our nix attribute set
         */
        System.out.println("}");
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

            final HashingInputStream inputStream = new HashingInputStream(Hashing.sha256(), connection.getInputStream());
            ByteStreams.exhaust(inputStream);

            return inputStream.hash().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static URL getRepositoryArtifactUrl(MavenArtifactInfo artifact, RemoteRepository repository) {
        String url = repository.getUrl();

        if (!url.endsWith("/")) {
            url += "/";
        }

        try {
            return new URL(url + getMavenCalculatedLayout(artifact.getCoordinate()));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not contact repository: " + url);
        }
    }

    /**
     * Check whether a given URL
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

    public static String getMavenCalculatedLayout(MavenCoordinate coordinate) {
        String classifier = coordinate.getClassifier().isBlank() ? "" : "-" + coordinate.getClassifier();

        return coordinate.getGroupId().replaceAll("\\.", "/")
                + "/"
                + coordinate.getArtifactId()
                + "/"
                + coordinate.getVersion()
                + "/"
                + coordinate.getArtifactId() + "-" + coordinate.getVersion()
                + classifier
                + "."
                + coordinate.getType();
    }

    public static List<RemoteRepository> getRemoteRepositories(MavenWorkingSessionImpl session) {
        try {
            Method getRemoteRepositoriesMethod = session.getClass().getDeclaredMethod("getRemoteRepositories", null);
            getRemoteRepositoriesMethod.setAccessible(true);
            return (List<RemoteRepository>) getRemoteRepositoriesMethod.invoke(session, null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
