package com.fzakaria.mvn2nix.model;

import java.util.HashMap;
import java.util.Map;

public class MavenNixInformation implements Model {

    public final Map<String, MavenArtifact> dependencies;
    public final Project project;

    public MavenNixInformation(Project project, Map<String, MavenArtifact> dependencies) {
        this.project = project;
        this.dependencies = new HashMap<>(dependencies);
    }

    public void addDependency(String name, MavenArtifact artifact) {
        dependencies.put(name, artifact);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
