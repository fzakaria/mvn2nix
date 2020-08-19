self: super: {
    mvn2nix = self.callPackage ./derivation.nix { };

   	buildMavenRepsitory = self.callPackage ./maven.nix {};
}
