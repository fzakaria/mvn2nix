package com.fzakaria.mvn2nix.cmd;

import com.fzakaria.mvn2nix.model.MavenArtifact;
import com.fzakaria.mvn2nix.model.MavenNixInformation;
import com.fzakaria.mvn2nix.model.PrettyPrintNixVisitor;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.io.ByteStreams;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.shrinkwrap.resolver.api.maven.*;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;
import org.jboss.shrinkwrap.resolver.impl.maven.MavenWorkingSessionContainer;
import org.jboss.shrinkwrap.resolver.impl.maven.MavenWorkingSessionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "mvn2nix", mixinStandardHelpOptions = true, version = "mvn2nix 0.1",
        description = "Converts Maven dependencies into a Nix expression.")
public class Maven2nix implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Maven2nix.class);

    @Mixin
    LoggingMixin loggingMixin;

    @Parameters(index = "0", paramLabel = "FILE", description = "The pom file to traverse.", defaultValue = "pom.xml")
    private File file;

    @Override
    public Integer call() {
        /*
         * Fetch the pom.xml that is relative to current directory.
         */
        final File pomFile = file;

        LOGGER.info("Reading {}", pomFile);

        final MavenResolverSystem resolver = Maven.resolver();
        /*
         * Resolve the pom.xml
         */
        final MavenResolvedArtifact[] artifacts = resolver
                .loadPomFromFile(pomFile)
                .importTestDependencies()
                // TODO: Consider making this a CLI argument
                //       consumers may not want to have system scopes
                .importDependencies(ScopeType.values())
                .resolve()
                .withTransitivity()
                .asResolvedArtifact();

        /*
         * Peek inside and get the RemoteRepositories
         */
        MavenWorkingSessionImpl session = (MavenWorkingSessionImpl) ((MavenWorkingSessionContainer) resolver).getMavenWorkingSession();
        final List<RemoteRepository> repositories = getRemoteRepositories(session);

        final MavenNixInformation information = new MavenNixInformation();

        /*
         * Go through every artifact
         */
        for (MavenArtifactInfo artifact : artifacts) {

            MavenCoordinate mavenCoordinate = artifact.getCoordinate();
            ScopeType scope = artifact.getScope();

            // Add the maven artifact as a dependency
            collectDependency(mavenCoordinate, scope, repositories, information);

            // maven needs the pom file in the repository as well
            // TODO: Figure out a cleaner way to support this
            //       right now it's just a separate entry in the model
            MavenCoordinate pomMavenCoordinate = MavenCoordinates.createCoordinate(
                    mavenCoordinate.getGroupId(),
                    mavenCoordinate.getArtifactId(),
                    mavenCoordinate.getVersion(),
                    PackagingType.POM,
                    mavenCoordinate.getClassifier()
            );
            collectDependency(pomMavenCoordinate, ScopeType.IMPORT, repositories, information);

        }

        PrettyPrintNixVisitor visitor = new PrettyPrintNixVisitor(System.out);
        information.accept(visitor);

        return 0;
    }

    public static void collectDependency(MavenCoordinate mavenCoordinate,
                                         ScopeType scope,
                                         List<RemoteRepository> repositories,
                                         MavenNixInformation information) {

        String canonical = mavenCoordinate.toCanonicalForm();

        String layout = getMavenCalculatedLayout(mavenCoordinate);

        /*
         * Find a valid URL for the artifact
         */
        URL url = repositories.stream()
                .map(repository -> getRepositoryArtifactUrl(mavenCoordinate, repository))
                .filter(Maven2nix::doesUrlExist)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(mavenCoordinate.getArtifactId() + " could not be found in any repository."));

        String sha256 = getSha256OfUrl(url);

        LOGGER.info("Resolved {} - {} - {}", canonical, url, sha256);

        information.addDependency(canonical, new MavenArtifact(url, layout, sha256, scope.toString()) );
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

    public static URL getRepositoryArtifactUrl(MavenCoordinate mavenCoordinate, RemoteRepository repository) {
        String url = repository.getUrl();

        if (!url.endsWith("/")) {
            url += "/";
        }

        try {
            return new URL(url + getMavenCalculatedLayout(mavenCoordinate));
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
