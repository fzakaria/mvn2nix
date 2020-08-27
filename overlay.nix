self: super: {
  maven-jdk11 = super.maven.override { jdk = super.jdk11; };

  maven-jdk8 = super.maven.override { jdk = super.jdk8; };

  mvn2nix-jdk8 = self.callPackage ./derivation.nix {
    maven = self.maven-jdk8;
  };

  mvn2nix-jdk11 = self.callPackage ./derivation.nix {
    maven = self.maven-jdk11;
  };

  buildMavenRepository = self.callPackage ./maven.nix { };
}
