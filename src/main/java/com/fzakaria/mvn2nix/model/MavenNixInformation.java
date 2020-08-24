package com.fzakaria.mvn2nix.model;

import java.util.HashMap;
import java.util.Map;

public class MavenNixInformation implements Model {

    public final Map<String, MavenArtifact> dependencies;

    public MavenNixInformation() {
        this.dependencies = new HashMap<>();
    }

    public MavenNixInformation(Map<String, MavenArtifact> dependencies) {
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
