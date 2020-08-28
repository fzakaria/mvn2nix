{ pkgs ? import <nixpkgs> { } }:
let
  # in a real scenario you would use
  # fetchTarball "https://github.com/fzakaria/mvn2nix/archive/master.tar.gz"
  mvn2nix = import ../.. { };
  buildMavenRepositoryFromLockFile = mvn2nix.buildMavenRepositoryFromLockFile;
  mavenRepository =
    buildMavenRepositoryFromLockFile { file = ./mvn2nix-lock.json; };
  inherit (pkgs) lib stdenv jdk11_headless maven makeWrapper;
  inherit (stdenv) mkDerivation;
in mkDerivation rec {
  pname = "mvn2nix";
  version = "0.1";
  name = "${pname}-${version}";

  # we set the source directory one level higher
  # this is just for this example
  src = lib.cleanSource ../../.;

  buildInputs = [ jdk11_headless maven makeWrapper ];
  buildPhase = ''
    echo "Building with maven repository ${mavenRepository}"
    mvn package --offline -Dmaven.repo.local=${mavenRepository}
  '';

  installPhase = ''
    # create the bin directory
    mkdir -p $out/bin

    # create a symbolic link for the lib directory
    ln -s ${mavenRepository} $out/lib

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
