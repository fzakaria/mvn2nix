package com.fzakaria.mvn2nix.model;

/**
 * Basic visitor pattern to allow double dispatch.
 */
public interface Visitor {

    void visit(MavenNixInformation info);

    void visit(MavenArtifact artifact);

    void visit(Project project);
}

