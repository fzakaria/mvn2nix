package com.fzakaria.mvn2nix.model;

/**
 * Information about this project
 */
public class Project implements Model {

    public final String group;
    public final String name;
    public final String version;

    public Project(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
