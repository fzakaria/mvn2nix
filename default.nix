{ pkgs ? import <nixpkgs> {
  config = { };
  overlays = [ (import ./overlay.nix) ];
} }: {
  mvn2nix = pkgs.mvn2nix;

  buildMavenRepository = pkgs.buildMavenRepository;
}
