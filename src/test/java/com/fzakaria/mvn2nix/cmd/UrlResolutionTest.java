package com.fzakaria.mvn2nix.cmd;

import com.fzakaria.mvn2nix.maven.Artifact;
import com.fzakaria.mvn2nix.util.Resources;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class UrlResolutionTest {

    /**
     * This test checks that the artifacts are resolved against all the repositories.
     *
     * In particular the artifact A is not found in repository 1, but is found in repository 2.
     * Therefore the URL of this artifact should point to repository 2.
     */
    @Test
    public void artifactUrlsWithMultipleRepositories() {
        Maven2nix app = new Maven2nix(UrlResolutionTest::fakeArtifactResolver, UrlResolutionTest::fakeArtifactAnalysis);
        CommandLine cmd = new CommandLine(app);
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        File pom = Resources.export("samples/basic/pom.xml");
        cmd.execute(pom.getPath(), "--repositories", "https://repo-1/", "https://repo-2");

        String actual = sw.getBuffer().toString();
        assertThat(actual).isEqualToIgnoringNewLines("{\n" +
                "  \"dependencies\": {\n" +
                "    \"group-a:name-a:jar:classifier-a:version-a\": {\n" +
                "      \"layout\": \"group-a/name-a/version-a/name-a-version-a-classifier-a.jar\",\n" +
                "      \"sha256\": \"sha-a\",\n" +
                "      \"url\": \"https://repo-2/group-a/name-a/version-a/name-a-version-a-classifier-a.jar\"\n" +
                "    },\n" +
                "    \"group-b:name-b:jar:classifier-b:version-b\": {\n" +
                "      \"layout\": \"group-b/name-b/version-b/name-b-version-b-classifier-b.jar\",\n" +
                "      \"sha256\": \"sha-b\",\n" +
                "      \"url\": \"https://repo-1/group-b/name-b/version-b/name-b-version-b-classifier-b.jar\"\n" +
                "    }\n" +
                "  }\n" +
                "}");
    }

    private static boolean fakeArtifactResolver(URL artifact) {
        return !"https://repo-1/group-a/name-a/version-a/name-a-version-a-classifier-a.jar".equals(artifact.toString());
    }

    private static Collection<Artifact> fakeArtifactAnalysis() {
        return ImmutableList.of(
                new Artifact("group-a", "name-a", "version-a", "classifier-a", "jar", "sha-a"),
                new Artifact("group-b", "name-b", "version-b", "classifier-b", "jar", "sha-b")
        );
    }
}
