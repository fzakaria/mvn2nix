# mvn2nix

![Build](https://github.com/fzakaria/mvn2nix/workflows/Build/badge.svg)
[![built with nix](https://builtwithnix.org/badge.svg)](https://builtwithnix.org)

## How to use this

### Generating the Nix dependencies file

In the same spirit of [bundix](https://github.com/nix-community/bundix), **mvn2nix** creates a Nix set with the
*transitive closure* of all dependencies required by the application.

```bash
$ nix run -f https://github.com/fzakaria/mvn2nix/archive/master.tar.gz \
        --command mvn2nix > dependencies.nix

$ head dependencies.nix
{
  "org.slf4j:jcl-over-slf4j:pom::1.5.6" = {
    url = "https://repo.maven.apache.org/maven2/org/slf4j/jcl-over-slf4j/1.5.6/jcl-over-slf4j-1.5.6.pom";
    layout = "org/slf4j/jcl-over-slf4j/1.5.6/jcl-over-slf4j-1.5.6.pom";
    sha256 = "d71d7748e68bb9cb7ad38b95d17c0466e31fc1f4d15bb1e635f3ebad34a38ff3";
  };
  "org.sonatype.sisu:sisu-inject-bean:pom::1.4.2" = {
    url = "https://repo.maven.apache.org/maven2/org/sonatype/sisu/sisu-inject-bean/1.4.2/sisu-inject-bean-1.4.2.pom";
    layout = "org/sonatype/sisu/sisu-inject-bean/1.4.2/sisu-inject-bean-1.4.2.pom";
    sha256 = "06d75dd6f2a0dc9ea6bf73a67491ba4790f92251c654bf4925511e5e4f48f1df";
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

### Sample Derivation

```nix
{ pkgs ? import <nixpkgs> {} }:
let
  mvn2nix = import
    (fetchTarball "https://github.com/fzakaria/mvn2nix/archive/add-pom.tar.gz")
    { };
  buildMavenRepository = mvn2nix.buildMavenRepository;
  mavenRepository = buildMavenRepository { dependencies = import ./dependencies.nix; };
inherit (pkgs) lib stdenv jdk11_headless maven makeWrapper;
inherit (stdenv) mkDerivation;
in mkDerivation rec {
  pname = "my-artifact";
  version = "0.01";
  name = "${pname}-${version}";
  src = lib.cleanSource ./.;

  buildInputs = [ jdk11_headless maven makeWrapper ];
  buildPhase = ''
    echo "Building with maven repository ${mavenRepository}"
    mvn package --offline -Dmaven.repo.local=${mavenRepository}
  '';

  installPhase = ''
    # create the bin directory
    mkdir -p $out/bin

    # create a symbolic link for the lib directory
    ln -s ${mavenRepository}/.m2 $out/lib

    # copy out the JAR
    # Maven already setup the classpath to use m2 repository layout
    # with the prefix of lib/
    cp target/${name}.jar $out/

    # create a wrapper that will automatically set the classpath
    # this should be the paths from the dependency derivation
    makeWrapper ${jdk11_headless}/bin/java $out/bin/${pname} \
          --add-flags "-jar $out/${name}.jar"
  '';
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
