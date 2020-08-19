package com.fzakaria.mvn2nix.model;

/**
 * Base interface for all model objects.
 */
public interface Model {

    /**
     * Entry point for the visitor pattern.
     */
    void accept(Visitor visitor);
}
