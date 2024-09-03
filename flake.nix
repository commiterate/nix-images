#
# Nix flake.
#
# https://nix.dev/manual/nix/stable/command-ref/new-cli/nix3-flake.html#flake-format
# https://wiki.nixos.org/wiki/Flakes#Flake_schema
#
{
  description = "Nix flake.";

  inputs = {
    # https://search.nixos.org/packages?channel=unstable
    nixpkgs = {
      type = "github";
      owner = "NixOS";
      repo = "nixpkgs";
      ref = "nixos-unstable";
    };
  };

  outputs =
    { self, nixpkgs }:
    let
      # Systems to provide outputs for.
      systems = [
        "aarch64-linux"
        "x86_64-linux"
      ];

      # A function that creates an attribute set and provides a system-specific nixpkgs for each system.
      forEachSystem =
        f: nixpkgs.lib.genAttrs systems (system: f { pkgs = import nixpkgs { inherit system; }; });
    in
    {
      # Formatter.
      #
      # For `nix fmt`.
      formatter = forEachSystem ({ pkgs }: pkgs.nixfmt-rfc-style);

      # Development shells.
      #
      # For `nix develop` and direnv's `use flake`.
      devShells = forEachSystem (
        { pkgs }:
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              # Nix.
              #
              # Nix is dynamically linked on some systems. If we set LD_LIBRARY_PATH,
              # running Nix commands with the system-installed Nix may fail due to mismatched library versions.
              nix
              # Utilities.
              coreutils
              tree
              # Git.
              git
              git-lfs
              # Babashka.
              babashka
              # Clojure.
              cljfmt
              clj-kondo
              # Bun.
              bun
              # Podman.
              podman
              # Alias docker → podman with a shim program.
              #
              # The AWS CDK's undocumented `CDK_DOCKER` environment variable doesn't work in some places.
              #
              # https://github.com/aws/aws-cdk/issues/25657
              # https://github.com/aws/aws-cdk/issues/31317
              (writeShellScriptBin "docker" "${podman}/bin/podman $@")
            ];

            shellHook = ''
              echo "⚗️"
            '';
          };
        }
      );

      # Packages.
      #
      # For `nix build`.
      packages = forEachSystem (
        { pkgs }:
        {
          # OCI container image.
          oci-container = pkgs.callPackage ./inputs/main/nix/oci-container/package.nix { inherit pkgs; };

          # Virtual machine image.
          #
          # For creating an Amazon Machine Image (AMI).
          virtual-machine = pkgs.callPackage ./inputs/main/nix/virtual-machine/package.nix {
            inherit nixpkgs pkgs;
          };
        }
      );
    };
}
