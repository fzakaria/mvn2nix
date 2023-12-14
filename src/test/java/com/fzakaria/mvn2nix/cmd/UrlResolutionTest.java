package com.fzakaria.mvn2nix.cmd;

import com.fzakaria.mvn2nix.maven.Artifact;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Collection;

import static com.fzakaria.mvn2nix.cmd.Maven2nix.mavenNixInformation;
import static com.fzakaria.mvn2nix.cmd.Maven2nix.toPrettyJson;
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
        var repositories = new String[] {"https://repo-1/", "https://repo-2"};
        assertThat(toPrettyJson(mavenNixInformation(UrlResolutionTest::fakeArtifactResolver, UrlResolutionTest::fakeArtifactAnalysis, repositories)))
                .isEqualToIgnoringNewLines("{\n" +
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
