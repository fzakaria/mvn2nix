{ lib, stdenv, jdk, maven, makeWrapper, gitignoreSource
, buildMavenRepositoryFromLockFile }:
let
  repository = buildMavenRepositoryFromLockFile { file = ./mvn2nix-lock.json; };
in stdenv.mkDerivation rec {
  pname = "mvn2nix";
  version = "0.1";
  name = "${pname}-${version}";
  src = gitignoreSource ./.;
  buildInputs = [ jdk maven makeWrapper ];
  buildPhase = ''
    # 'maven.repo.local' must be writable so copy it out of nix store
    mvn package --offline -Dmaven.repo.local=${repository}
  '';

  installPhase = ''
    # create the bin directory
    mkdir -p $out/bin

    # create a symbolic link for the lib directory
    ln -s ${repository} $out/lib

    # copy out the JAR
    # Maven already setup the classpath to use m2 repository layout
    # with the prefix of lib/
    cp target/${name}.jar $out/

    # create a wrapper that will automatically set the classpath
    # this should be the paths from the dependency derivation
    makeWrapper ${jdk}/bin/java $out/bin/${pname} \
          --add-flags "-jar $out/${name}.jar" \
          --set M2_HOME ${maven} \
          --set JAVA_HOME ${jdk}
  '';

  meta = with stdenv.lib; {
    description =
      "Easily package your Java applications for the Nix package manager.";
    homepage = "https://github.com/fzakaria/mvn2nix";
    license = licenses.mit;
    maintainers = [ "farid.m.zakaria@gmail.com" ];
    platforms = platforms.all;
  };
}
