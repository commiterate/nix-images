{ nixpkgs, pkgs }:

# https://nixos.org/manual/nixpkgs/stable#sec-make-disk-image
import "${nixpkgs}/nixos/lib/make-disk-image.nix" {
  name = "nixos";
  lib = pkgs.lib;
  pkgs = pkgs;
  config =
    (nixpkgs.lib.nixosSystem {
      system = pkgs.system;
      modules = [
        (
          {
            config,
            lib,
            pkgs,
            ...
          }:
          {
            nix = {
              settings = {
                experimental-features = [
                  "nix-command"
                  "flakes"
                ];
              };
            };

            fileSystems = {
              "/" = {
                device = "/dev/disk/by-label/nixos";
                # Amazon Linux uses XFS.
                #
                # https://docs.aws.amazon.com/linux/al2023/ug/continuing-al2-filesystem.html
                fsType = "xfs";
                autoResize = true;
              };

              # AWS Nitro System requires EFI for AWS Graviton processors.
              #
              # https://docs.aws.amazon.com/ec2/latest/instancetypes/ec2-nitro-instances.html#nitro-requirements
              "/boot/efi" = {
                device = "/dev/disk/by-label/ESP";
                fsType = "vfat";
              };
            };

            boot = {
              loader = {
                systemd-boot = {
                  enable = true;
                  # Keep up to 100 Nix generations. Matches the default NixOS configuration for GRUB.
                  configurationLimit = 100;
                };
              };

              initrd = {
                availableKernelModules = [
                  # AWS Nitro System exposes storage volumes as NVMe block devices.
                  #
                  # https://docs.aws.amazon.com/ec2/latest/instancetypes/ec2-nitro-instances.html#nitro-requirements
                  "nvme"
                ];
              };

              extraModulePackages = [
                # AWS Nitro System uses Elastic Network Adapter (ENA) for networking.
                #
                # https://docs.aws.amazon.com/ec2/latest/instancetypes/ec2-nitro-instances.html#nitro-requirements
                config.boot.kernelPackages.ena
              ];

              growPartition = true;
            };

            # TODO: Configurations to support EC2 Serial Console. Set up ttyS0.
            #
            # https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/connect-to-serial-console.html

            security = {
              # AWS Nitro Trusted Platform Module (NitroTPM) conforms to TPM 2.0.
              #
              # https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/enable-nitrotpm-prerequisites.html
              tpm2 = {
                enable = true;

                pkcs11 = {
                  enable = true;
                };

                tctiEnvironment = {
                  enable = true;
                };
              };
            };

            services = {
              udev = {
                packages = with pkgs; [
                  # Has udev rules for consistent device naming.
                  amazon-ec2-utils
                ];
              };

              # systemd-timesyncd.
              timesyncd = {
                enable = true;
              };

              # AWS Systems Manager (SSM) Agent.
              #
              # Included with most AWS-provided AMIs and needed for some AWS services like Systems Manager and EC2 Image Builder.
              #
              # https://docs.aws.amazon.com/systems-manager/latest/userguide/ssm-agent.html
              amazon-ssm-agent = {
                enable = true;
              };

              openssh = {
                enable = true;

                settings = {
                  # Require using the EC2 key pair or EC2 Instance Connect.
                  PermitRootLogin = "prohibit-password";
                };
              };
            };

            networking = {
              # Use the EC2-provided hostname.
              hostName = "";

              # Amazon Time Sync.
              #
              # https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configure-ec2-ntp.html
              timeServers = [
                "169.254.169.123"
                "fd00:ec2::123"
              ];
            };
          }
        )
      ];
    }).config;
  # AWS Nitro System requires EFI for AWS Graviton processors.
  #
  # https://docs.aws.amazon.com/ec2/latest/instancetypes/ec2-nitro-instances.html#nitro-requirements
  partitionTableType = "efi";
  format = "raw";
}
