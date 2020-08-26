self: super: {
  jdk = super.jdk11_headless;

  mvn2nix = self.callPackage ./derivation.nix { };

  buildMavenRepository =
    (self.callPackage ./maven.nix { }).buildMavenRepository;

  buildMaven = (self.callPackage ./maven.nix { }).buildMaven;
}
