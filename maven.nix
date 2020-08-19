{ lib, fetchurl, linkFarm }:
with lib;
# Create a maven environment from the output of the mvn2nix command
# the resulting store path can be used as the a .m2 repository for subsequent
# maven invocations.
# ex.
# 	mvn package --offline -Dmaven.repo.local=${dependencies}
{ name ? "buildMavenRepository", dependencies ? import ./dependencies.nix, }:
let
  dependenciesAsDrv = (forEach (attrValues dependencies) (dependency: {
    drv = fetchurl {
      url = dependency.url;
      sha256 = dependency.sha256;
    };
    layout = dependency.layout;
  }));
in linkFarm name (forEach dependenciesAsDrv (dependency: {
  name = dependency.layout;
  path = dependency.drv;
}))
