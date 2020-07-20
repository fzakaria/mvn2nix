self: super: {
    mvn2nix = self.callPackage ./derivation.nix { };
}
