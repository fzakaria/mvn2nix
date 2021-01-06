{
  description = "Easily package your Maven Java application with the Nix package manager";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-20.09";
  };

  outputs = { self, nixpkgs }:
  let
    system = "x86_64-linux";
    derivations = import ./default.nix {inherit nixpkgs; inherit system;};
  in
  rec {
    legacyPackages.x86_64-linux = {
      mvn2nix = derivations.mvn2nix;
      mvn2nix-bootstrap = derivations.mvn2nix-bootstrap;
      buildMavenRepository = derivations.buildMavenRepository;
      buildMavenRepositoryFromLockFile = derivations.buildMavenRepositoryFromLockFile;
    };

    defaultPackage.x86_64-linux = legacyPackages.x86_64-linux.mvn2nix;
  };
}
