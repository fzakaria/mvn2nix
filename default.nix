{ pkgs ? import <nixpkgs> {
  config = { };
  overlays = [ (import ./overlay.nix) ];
} }:
pkgs.mvn2nix
