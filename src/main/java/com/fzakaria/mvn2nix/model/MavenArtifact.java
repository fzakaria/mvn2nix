package com.fzakaria.mvn2nix.model;

import java.net.URL;

public class MavenArtifact implements Model{

    public final URL url;
    public final String layout;
    public final String sha256;
    public final String scope;

    public MavenArtifact(URL url, String layout, String sha256, String scope) {
        this.url = url;
        this.layout = layout;
        this.sha256 = sha256;
        this.scope = scope;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
