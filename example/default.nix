{ pkgs ? import <nixpkgs> {} }:
  let buildMavenRepository = import (
      fetchTarball https://github.com/fzakaria/mvn2nix/archive/master.tar.gz
    ).buildMavenRepository;
  mavenRepository = buildMavenRepository { dependencies = import ./dependencies.nix; };
inherit (pkgs) lib stdenv;
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