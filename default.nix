{ nixpkgs ? (import ./nix/sources.nix).nixpkgs }:
let
  sources = import ./nix/sources.nix;
  pkgs = import nixpkgs {
    overlays = [
      (_: super: {
        niv = (import sources.niv { }).niv;
        # include local sources in your Nix projects, while taking gitignore files into account
        # https://github.com/hercules-ci/gitignore.nix
        gitignoreSource = (import sources.gitignore { }).gitignoreSource;
      })
      (import ./overlay.nix)
    ];
  };
in {
  mvn2nix = pkgs.mvn2nix;

  buildMavenRepository = pkgs.buildMavenRepository;

  buildMaven = pkgs.buildMaven;
}
