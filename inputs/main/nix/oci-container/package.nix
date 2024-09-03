{ pkgs }:

# https://nixos.org/manual/nixpkgs/stable#sec-pkgs-dockerTools
pkgs.dockerTools.buildImage {
  name = "nix";
  tag = "latest";
  copyToRoot = pkgs.buildEnv {
    name = "image-root";
    paths = with pkgs; [
      bashInteractive
      coreutils
      nix
    ];
    pathsToLink = [
      "/bin"
    ];
  };
  includeNixDB = true;
}
