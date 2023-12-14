package com.fzakaria.mvn2nix.cmd;

import com.fzakaria.mvn2nix.maven.Artifact;
import com.fzakaria.mvn2nix.util.Resources;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.Collection;

import static com.fzakaria.mvn2nix.cmd.Maven2nix.mavenNixInformation;
import static com.fzakaria.mvn2nix.cmd.Maven2nix.toPrettyJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests whether the sha256 determined by the artifact analysis matches the
 * sha256 of the artifact in the url associated with the artifact.
 *
 * In this test an artifact is found with the sha of the file in repository 2. However, the mvn2nix
 * resolver associates the artifact with repository 1 where it has a different SHA-256.
 */
@Nested
class Sha256MatchTest {

    private static Artifact artifactA = new Artifact("group-a", "name-a", "version-a", "classifier-a", "jar", "sha-a-in-repo-2");

    @Disabled("Hashes do not match")
    @Test
    public void artifactUrlsWithMultipleRepositories() {
        var repositories = new String[]{"https://repo-1/", "https://repo-2"};
        var artifactInfo = mavenNixInformation(Sha256MatchTest::fakeArtifactResolver, Sha256MatchTest::fakeArtifactAnalysis, repositories)
                .byCanonicalName(artifactA.getCanonicalName());
        assertEquals(artifactInfo.getSha256(), sha256(artifactInfo.getUrl()));
    }

    private static String sha256(URL url) {
        switch (url.toString()) {
            case "https://repo-1/group-a/name-a/version-a/name-a-version-a-classifier-a.jar":
                return "sha-a-in-repo-1";
            case "https://repo-2/group-a/name-a/version-a/name-a-version-a-classifier-a.jar":
                return "sha-a-in-repo-2";
            default: throw new IllegalStateException("All cases covered!");
        }
    }

    private static boolean fakeArtifactResolver(URL artifact) {
        return true;
    }

    private static Collection<Artifact> fakeArtifactAnalysis() {
        return ImmutableList.of(artifactA);
    }
}
