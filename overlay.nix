self: super: {
  mvn2nix = self.callPackage ./derivation.nix { };

  buildMavenRepository = self.callPackage ./maven.nix { };
}
