# mvn2nix

## How to use this

### Generating the Nix dependencies file

In the same spirit of [bundix](https://github.com/nix-community/bundix), **mvn2nix** creates a Nix set with the
*transitive closure* of all dependencies required by the application.

```bash
$ nix run -f https://github.com/fzakaria/mvn2nix/archive/master.tar.gz \
        --command mvn2nix > dependencies.nix

$ head dependencies.nix
{
  "org.jboss.shrinkwrap.resolver:shrinkwrap-resolver-impl-maven-archive:jar:3.1.4" = {
    url = "https://repo.maven.apache.org/maven2/org/jboss/shrinkwrap/resolver/shrinkwrap-resolver-impl-maven-archive/3.1.4/shrinkwrap-resolver-impl-maven-archive-3.1.4.jar";
    layout = "org/jboss/shrinkwrap/resolver/shrinkwrap-resolver-impl-maven-archive/3.1.4/shrinkwrap-resolver-impl-maven-archive-3.1.4.jar";
    sha256 = "fec296a3b5a8e2cd6fcf8050f5b956f903d1251f901ac6ab8c0355c4e6b27a1c";
    scope = "runtime";
  };
  "org.arquillian.spacelift:arquillian-spacelift:jar:1.0.2" = {
    url = "https://repo.maven.apache.org/maven2/org/arquillian/spacelift/arquillian-spacelift/1.0.2/arquillian-spacelift-1.0.2.jar";
    layout = "org/arquillian/spacelift/arquillian-spacelift/1.0.2/arquillian-spacelift-1.0.2.jar";
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
  pname = "my-dummy-derivation";
  version = "0.01";
  name = "${pname}-${version}";
  src = lib.cleanSource ./.;

  buildInputs = [ jdk11_headless maven makeWrapper ];
  buildPhase = ''
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

## Development

If you are running *mvn2nix* from this repository, you can do so with **nix-build**

```bash
$ nix-build

./result/bin/mvn2nix > dependencies.nix
```

If you want to test **buildMavenRepository** you can run:
```bash
$ nix-build -A buildMavenRepository --arg dependencies "import ./dependencies.nix"
```
