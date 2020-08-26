<p align="center">
  <img src="logo.png" alt="mvn2nix" width="164" height="164">
</p>

![Build](https://github.com/fzakaria/mvn2nix/workflows/Build/badge.svg)
[![built with nix](https://builtwithnix.org/badge.svg)](https://builtwithnix.org)

> Easily package your Maven Java application with the Nix package manager.

<!--ts-->
* [How to use this](#how-to-use-this)
   * [Generating the Nix dependencies file](#generating-the-nix-dependencies-file)
   * [Building a Maven repository](#building-a-maven-repository)
   * [Sample Derivation](#sample-derivation)
* [How does it work?](#how-does-it-work)
* [Development](#development)
<!-- Added by: fmzakari, at: Mon Aug 24 21:40:20 PDT 2020 -->
<!--te-->

## How to use this

```bash
$ nix run -f https://github.com/fzakaria/mvn2nix/archive/master.tar.gz --command mvn2nix
```

If you have [cachix](https://cachix.org/) installed, you can leverage our prebuilt binary.
> cachix use fzakaria

### Generating the Nix dependencies file

In the same spirit of [bundix](https://github.com/nix-community/bundix), **mvn2nix** creates a Nix set with the
*transitive closure* of all dependencies required by the application.

```bash
$ nix run -f https://github.com/fzakaria/mvn2nix/archive/master.tar.gz \
        --command mvn2nix > dependencies.nix

$ head dependencies.nix
{
  "junit:junit:pom:3.8.1" = {
    url = "https://repo.maven.apache.org/maven2/junit/junit/3.8.1/junit-3.8.1.pom";
    layout = "junit/junit/3.8.1/junit-3.8.1.pom";
    sha256 = "e68f33343d832398f3c8aa78afcd808d56b7c1020de4d3ad8ce47909095ee904";
  };
  "org.sonatype.forge:forge-parent:pom:10" = {
    url = "https://repo.maven.apache.org/maven2/org/sonatype/forge/forge-parent/10/forge-parent-10.pom";
    layout = "org/sonatype/forge/forge-parent/10/forge-parent-10.pom";
    sha256 = "c14fb9c32b59cc03251f609416db7c0cff01f811edcccb4f6a865d6e7046bd0b";
```

You can then use this to download all the necessary dependencies to run your application.

### Building a Maven repository

Now that you have a **nix** dependencies file; we can re-construct a Maven repository using Nix!

```nix
let mvn2nix = import (fetchTarball https://github.com/fzakaria/mvn2nix/archive/master.tar.gz) { };
in
mvn2nix.buildMavenRepsitory { dependencies = import ./dependencies.nix; }
```

This creates a **/nix/store** path which is a Maven repository that can be used, such as in `mvn package --offline -Dmaven.repo.local=${mavenRepository}`

```bash
$ tree /nix/store/2ps43297g5nii2k15kfy8z46fam51d8x-buildMavenRepository | head

/nix/store/2ps43297g5nii2k15kfy8z46fam51d8x-buildMavenRepository
├── com
│   └── google
│       ├── code
│       │   └── findbugs
│       │       └── jsr305
│       │           └── 3.0.2
│       │               └── jsr305-3.0.2.jar -> /nix/store/w20lb1dk730v77qis8l6sjqpljwkyql7-jsr305-3.0.2.jar
│       ├── errorprone
│       │   └── error_prone_annotations
```

### Building a runnable JAR

If your project is an executable JAR; you can easily use the `buildMaven` derivation
to create a simple runnable JAR; which has all the necessary dependencies available on the
classpath

Here is the example that builds _this project_! You can also find it [here](./examples/mvn2nix/default.nix).
```nix
{ pkgs ? import <nixpkgs> {} }:
let
  mvn2nix = import
    (fetchTarball "https://github.com/fzakaria/mvn2nix/archive/master.tar.gz")
    { };
  buildMavenRepository = mvn2nix.buildMavenRepository;
  buildMaven = mvn2nix.buildMaven;
  mavenRepository =
    buildMavenRepository { generated = import ./dependencies.nix; };
in buildMaven {
  jdk = pkgs.jdk11_headless;
  generated = import ./dependencies.nix;
  repository = mavenRepository;
  # we set the source directory one level higher
  # this is just for this example
  src = ./.;
}
```

## How does it work?

**mvn2nix** relies on [maven-invoker](https://maven.apache.org/shared/maven-invoker/); which fires off
Maven in a separate JVM process.

Maven is executed with a temporary *ephemeral* local repository for the given goals provided (defaults to **package**).
The local repository is than traversed, and each encountered file is recorded in the dependencies list.

**mvn2nix** includes an [example](examples/mvn2nix/default.nix) output & derivation that builds itself!

## Development

If you are running *mvn2nix* from this repository, you can do so with **nix-build**

```bash
$ nix-build

./result/bin/mvn2nix > example/dependencies.nix
```

If you want to test **buildMavenRepository** you can run:
```bash
$ nix-build -A buildMavenRepository --arg dependencies "import ./dependencies.nix"
```
