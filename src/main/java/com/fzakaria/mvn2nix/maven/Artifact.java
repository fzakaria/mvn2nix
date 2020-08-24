package com.fzakaria.mvn2nix.maven;

import com.google.common.base.Strings;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Artifact {

    private final String group;
    private final String name;
    private final String version;
    private final String classifier;
    private final String extension;

    public Artifact(String group, String name, String version, String classifier, String extension) {
        this.group = group;
        this.name = name;
        this.version = version;
        this.classifier = classifier;
        this.extension = extension;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getExtension() {
        return extension;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Artifact artifact = (Artifact) o;
        return Objects.equals(group, artifact.group) &&
                Objects.equals(name, artifact.name) &&
                Objects.equals(version, artifact.version) &&
                Objects.equals(classifier, artifact.classifier) &&
                Objects.equals(extension, artifact.extension);
    }

    /**
     * Gets the maven repository layout for this artifact
     * @return
     */
    public String getLayout() {
        return group.replaceAll("\\.", "/")
                + "/"
                + name
                + "/"
                + version
                + "/"
                + name + "-" + version
                + (classifier.isBlank() ? "" : "-" + classifier)
                + "."
                + extension;
    }

    /**
     * Return the canonical name for this artifact.
     * ex. org.apache.commons:commons-parent:pom:22
     */
    public String getCanonicalName() {
        final String[] parts = new String[] {
          group, name, extension, classifier, version
        };
        return Stream.of(parts)
                .map(Strings::emptyToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(":"));
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, name, version, classifier, extension);
    }

    @Override
    public String toString() {
        return getCanonicalName();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String group;
        private String name;
        private String version;
        private String classifier;
        private String extension;

        public Builder setGroup(String group) {
            this.group = group;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setVersion(String version) {
            this.version = version;
            return this;
        }

        public Builder setClassifier(String classifier) {
            this.classifier = classifier;
            return this;
        }

        public Builder setExtension(String extension) {
            this.extension = extension;
            return this;
        }

        public Artifact build() {
            return new Artifact(group, name, version, classifier, extension);
        }
    }
}
