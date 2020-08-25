{ lib, stdenv, jdk, maven, makeWrapper, gitignoreSource }:
with stdenv;
let
  version = "0.1";
  dependencies = mkDerivation {
    name = "mvn2nix-${version}-dependencies";
    buildInputs = [ jdk maven ];
    src = gitignoreSource ./.;
    buildPhase = ''
      while mvn package -Dmaven.repo.local=$out/.m2 -Dmaven.wagon.rto=5000; [ $? = 1 ]; do
        echo "timeout, restart maven to continue downloading"
      done
    '';
    # keep only *.{pom,jar,sha1,nbm} and delete all ephemeral files with lastModified timestamps inside
    installPhase = ''
      find $out/.m2 -type f \
        -name \*.lastUpdated -or \
        -name resolver-status.properties -or \
        -name _remote.repositories \
        -delete
    '';
    outputHashAlgo = "sha256";
    outputHashMode = "recursive";
    outputHash = "1mkd03hwaviqzrs6gfaq6axnqbdv23i2jaclqr0nn5m7230g53im";
  };
in mkDerivation rec {
  pname = "mvn2nix";
  inherit version;
  name = "${pname}-${version}";
  src = gitignoreSource ./.;
  buildInputs = [ jdk maven makeWrapper ];
  buildPhase = ''
    # 'maven.repo.local' must be writable so copy it out of nix store
    mvn package --offline -Dmaven.repo.local=${dependencies}/.m2
  '';

  installPhase = ''
    # create the bin directory
    mkdir -p $out/bin

    # create a symbolic link for the lib directory
    ln -s ${dependencies}/.m2 $out/lib

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
