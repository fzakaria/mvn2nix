{ pkgs ? import <nixpkgs> { } }:
let
  # in a real scenario you would use
  # fetchTarball "https://github.com/fzakaria/mvn2nix/archive/master.tar.gz"
  mvn2nix = import ../.. { };
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
  src = ../../.;
}
