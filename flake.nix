{
  description = "child-monitor - Android-based Child Monitor.";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/23.11";
    devshell.url = "github:numtide/devshell";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, devshell, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        inherit (nixpkgs) lib;
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
          overlays = [
            devshell.overlays.default
          ];
        };
        buildToolsVersion = "34.0.0";
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ buildToolsVersion ];
          #platformVersions = [ "26" "30" "31" "33" "28" ];
          platformVersions = [ "34" ];
          abiVersions = [ "armeabi-v7a" "arm64-v8a" ];
        };
        androidSdk = androidComposition.androidsdk;
      in
      {
        devShell = import ./devshell.nix { pkgs = pkgs; androidSdk = androidSdk; };
      });
}
