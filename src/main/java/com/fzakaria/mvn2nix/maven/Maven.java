package com.fzakaria.mvn2nix.maven;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.io.IoBuilder;
import org.apache.logging.log4j.io.LoggerPrintStream;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Small utility wrapper around maven-invoker.
 * https://maven.apache.org/shared/maven-invoker/index.html
 */
public class Maven {

    private final Invoker invoker;
    private final File localRepository;

    /**
     * Constructor.
     * The local repository must be a directory.
     * @param localRepository The location of the local repository
     */
    public Maven(File localRepository) {
        Preconditions.checkArgument(localRepository.isDirectory());
        this.localRepository = localRepository;
        this.invoker = new DefaultInvoker();
        this.invoker.setLocalRepositoryDirectory(localRepository);
        this.invoker.setLogger(new Log4j2Logger());

        // send all of maven's output to log4j2 which goes to STDERR
        PrintStreamHandler handler = new PrintStreamHandler(
                IoBuilder.forLogger("maven-invoker").setAutoFlush(true).buildPrintStream(),
                true
        );

        this.invoker.setOutputHandler(handler);
        this.invoker.setErrorHandler(handler);
    }

    /**
     * Create a {@link Maven} object with a temporary local repository.
     * The local repository is deleted upon JVM exit.
     */
    public static Maven withTemporaryLocalRepository() {
        try {
            File tempLocalRepository = Files.createTempDirectory("mvn2nix").toFile();
            tempLocalRepository.deleteOnExit();
            return new Maven(tempLocalRepository);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void executeGoals(String... goals) throws MavenInvocationException {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setGoals(Lists.newArrayList(goals));
        request.setBatchMode(true);

        InvocationResult result = this.invoker.execute(request);
        if (result.getExitCode() != 0) {
            throw new MavenInvocationException(
                    String.format("Failed to execute goals [%s]. Exit code: %s", Arrays.toString(goals), result.getExitCode()),
                    result.getExecutionException()
            );
        }
    }

    /**
     * Walk the local repository and collect all the artifacts downloaded
     * https://cwiki.apache.org/confluence/display/MAVEN/Remote+repository+layout#:~:text=Repository%20metadata%20layout-,Introduction,versioned%20releases%20of%20dependent%20projects
     * The layout is assumed to be:
     * ${groupId.replace('.','/')}/${artifactId}/${version}/${artifactId}-${version}${classifier==null?'':'-'+classifier}.${type}
     * @return
     */
    public Collection<Artifact> collectAllArtifactsInLocalRepository() {
        try {
            return Files.walk(localRepository.toPath())
                 .filter(Files::isRegularFile)
                 .filter(f -> !f.endsWith("maven-metadata-local.xml"))
                 .filter(f -> !f.toFile().getName().endsWith("sha1"))
                 .filter(f -> !f.toFile().getName().equals("_remote.repositories"))
                 .map(file -> {
                     Path layout = localRepository.toPath().relativize(file);

                     String extension = com.google.common.io.Files.getFileExtension(layout.toString());
                     String nameAndVersionAndClassifier = com.google.common.io.Files.getNameWithoutExtension(layout.toFile().getName());
                     String version = layout.getParent().toFile().getName();
                     String name = layout.getParent().getParent().toFile().getName();
                     String classifier = nameAndVersionAndClassifier
                             .replaceAll(name + "-" + version, "")
                             .replaceFirst("^-", "");
                     String group = StreamSupport.stream(
                             layout.getParent().getParent().getParent().spliterator(),
                             false)
                             .map(Path::toString)
                             .collect(Collectors.joining("."));
                     return Artifact.builder()
                             .setGroup(group)
                             .setName(name)
                             .setClassifier(classifier)
                             .setVersion(version)
                             .setExtension(extension)
                             .build();
                 }).collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
