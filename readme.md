# Nix Images

> 🚧 Under construction.

__Experimental__ Nix images and image distribution infrastructure in AWS.

## Layout

```text
Key:
🤖 = Generated

.
│   # Build inputs.
├── inputs/
│   └── main/
│       │   # Image distribution infrastructure managed with the AWS CDK.
│       ├── clojure/
│       │   └── *.cljs
│       │
│       │   # Image definitions.
│       └── nix/
│           └── *.nix
│
│   # Build outputs.
├── outputs/ 🤖
│   └── main/
│       │   # Image distribution infrastructure templates.
│       ├── infrastructure/
│       │   └── *.json
│       │
│       │   # OCI container images.
│       ├── oci-container/
│       │   └── {system}/
│       │       └── nix.tar.gz
│       │
│       │   # Virtual machine images.
│       └── virtual-machine/
│           └── {system}/
│               └── nixos.img
│
│   # Reproducible development shell and Nix package definitions.
├── flake.nix
├── flake.lock 🤖
│
│   # JavaScript path configuration.
├── package.json
├── bun.lockb 🤖
│
│   # AWS CDK configuration.
├── cdk.json
│
│   # Build recipes.
└── bb.edn
```

## Tools

* AArch64 or x86-64 Linux (any distribution)
* Git
* Nix
* Babashka
	* Clojure scripting runtime. For build recipes.
* cljmt
	* Clojure formatter.
* clj-kondo
	* Clojure linter.
* Bun
	* JavaScript runtime.
* Podman
	* Container runtime.

A reproducible shell can be created with [Nix](https://nixos.org) (described by the `flake.nix` + `flake.lock` files).

Nix can be installed with the [Determinate Nix Installer](https://github.com/DeterminateSystems/nix-installer) ([guide](https://zero-to-nix.com/start/install)).

Afterwards, you can change into the project directory and create the reproducible shell with `nix develop`.

You can also install the [direnv](https://direnv.net) shell extension to automatically load and unload the reproducible shell when you enter and leave the project directory.

Unlike `nix develop` which drops you in a nested Bash shell, direnv extracts the environment variables from the nested Bash shell into your current shell (e.g. Bash, Zsh, Fish).

## Developing

Common build recipes are provided as Babashka tasks. To list them, run:

```shell
bb tasks
```

### OCI Containers

OCI container images package a Linux user space. This project defines a minimal image with Nix (not NixOS) which can be used as development and CI/CD build environments.

To build an image, run:

```shell
bb oci-container
```

To load the image into the local container storage of a container runtime like `podman`, run:

```shell
podman load --input outputs/main/oci-container/${system}/nix.tar.gz
```

You may want to manually inspect the image contents with a tool like [`dive`](https://search.nixos.org/packages?channel=unstable&show=dive) (not included in the Nix shell).

To inspect the image with `dive`, load the image into local container storage and then run:

```shell
dive localhost/nix:latest
```

### Virtual Machines

> ℹ️ Requires virtualization support.
>
> This uses a virtual machine to build a NixOS disk image.

Virtual machine images package an entire system disk (bootloader, Linux kernel + user space). This project defines a minimal image with NixOS intended to be converted into an Amazon Machine Image (AMI).

To build an image, run:

```shell
bb virtual-machine
```

You may want to manually inspect the image contents by mounting it to your local file system.

Most file managers natively support mounting `.img` files, letting you browse them graphically.

### Emulated Native Compilation

While this project isn't configured for cross-compilation (see [_Bootstrap_](#bootstrap)), you can build images for other systems through emulated native compilation.

NixOS users can set up [emulated native compilation](https://wiki.nixos.org/wiki/QEMU#Run_binaries_of_different_architecture) with the [`boot.binfmt.emulatedSystems`](https://search.nixos.org/options?channel=unstable&show=boot.binfmt.emulatedSystems) NixOS option.

Once it's set up, you can use the `--system` option in the Babashka tasks to build system-specific outputs.

```shell
bb oci-container --system aarch64-linux
bb virtual-machine --system aarch64-linux
bb images --system aarch64-linux
```

## Notes

### Bootstrap

Image building (like building other native binaries) is typically tied to the host system's instruction set architecture (ISA, e.g. AArch64 or x86-64).

While builds with matching host and target systems are straightforward, builds using cross-compilation typically aren't. Cross-compilation can be tricky without proper support from toolchains.

Builds using emulation (e.g. with `binfmt`) for emulated native compilation also have their own difficulties. These mostly stem from procuring a CI/CD build host that supports it (usually requires self-hosting).

This project doesn't use cross-compilation or emulated native compilation (though we support the latter for easier local development) to avoid their associated complexity.

Instead, we try to shift some of the complexity to the CI/CD pipeline itself. We assume the following for CI/CD image build pipeline bootstrap:

* __Bootstrap Machines__
	* __Hardware__
		* __ISA__ (1+ machines each)
			* AArch64
			* x86-64
	* __Software__
		* __Operating System__
			* __Kernel__
				* Linux (provided by any distribution)
			* __Programs__
				* Nix
				* Nixpkgs (can be used to procure other programs)
				* [Nixpkgs stdenv programs](https://nixos.org/manual/nixpkgs/stable#sec-tools-of-stdenv), in particular:
					* Bash
					* GNU coreutils
				* Git (for Nix. May require other dependencies procured through the Linux distribution's included package manager)

[GitHub-hosted runners](https://docs.github.com/en/actions/using-github-hosted-runners/using-github-hosted-runners) fit these requirements with their [larger runners](https://docs.github.com/en/actions/using-github-hosted-runners/using-larger-runners/about-larger-runners).

We can use [`nix-installer-action`](https://github.com/DeterminateSystems/nix-installer-action) from Determinate Systems to procure Nix in the GitHub-hosted runner environment.

The GitHub Actions workflow will do the following:

1. Use both x86-64 and AArch64 Linux runners to build system-specific artifacts (i.e. images) and upload them as artifacts.
2. Use an x86-64 or AArch64 Linux runner to download system-specific artifacts, build system-agnostic artifacts (i.e. infrastructure templates), and perform deployments.

### Distribution

Images are (or will be) distributed by AWS EC2 Image Builder. OCI container images are stored in an AWS ECR public repository while virtual machine images are converted into Amazon Machine Images (AMIs) and copied to multiple regions.

Unlike us manually handling distribution, using both AWS CloudFormation and AWS EC2 Image Builder gives us automatic rollbacks and declarative lifecycle management.

AWS CloudFormation stack update is transactional. When a stack is updated, resource creates + updates are done before deletes. If creates + updates fail, the changes are rolled back.

AWS EC2 Image Builder is the only declarative AWS-provided option (due to AWS CloudFormation support) for building, distributing, and handling lifecycle management for both OCI containers in AWS ECR public repositories (only private repositories support AWS ECR lifecycle rules) and AMIs.

### To-Do

* Figure out how to handle GitHub size limits for caches ([10 GB](https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/caching-dependencies-to-speed-up-workflows#usage-limits-and-eviction-policy)) and artifacts (plan-specific).
	* The virtual machine images are ~5 GB each. Since they're Nix derivations, they'll be cached by [`magic-nix-cache`](https://github.com/DeterminateSystems/magic-nix-cache) from Determinate Systems and thus purge the GitHub Actions cache.
* Finalize distribution infrastructure with AWS EC2 Image Builder.
	* Pending these feature requests:
		* https://github.com/aws/ec2-image-builder-roadmap/issues/102
		* https://github.com/aws/ec2-image-builder-roadmap/issues/103
* Set up a periodic dependency update workflow (e.g. Dependabot, Renovate).
* (Optional) Optimize NixOS image size.
	* https://sidhion.com/blog/posts/nixos_server_issues
