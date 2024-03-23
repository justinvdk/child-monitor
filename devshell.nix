{ pkgs, androidSdk }:

with pkgs;

devshell.mkShell {
  name = "baby-monitor";
  motd =
    let
      steps = [
        "./gradlew assembleRelease"
        "\\\$ANDROID_SDK_ROOT/build-tools/*/zipalign 4 ./app/build/outputs/apk/release/app-release-unsigned.apk ./release-unsigned-aligned.apk"
        "\\\$ANDROID_SDK_ROOT/build-tools/*/apksigner sign --ks /data/secrets/android.keystore --ks-key-alias baby-monitor --in ./release-unsigned-aligned.apk --out ./release-signed.apk"
      ];
    in ''
      Entered devenv for:
      baby-monitor - Android-based Child Monitor

      steps: $steps

      Go to ~/Code/baby-monitor to start working.

      Building a new signed apk:
      1. ${builtins.elemAt steps 0}
      2. ${builtins.elemAt steps 1}
      3. ${builtins.elemAt steps 2}
         password for store in password manager under android.keystore.
      4. (from somewhere else): rsync --progress woodhouse:Code/child-monitor/release-signed.apk ~/Share/APKs/child-monitor.apk

      copy-pastable:
      rm -f release-unsigned-aligned.apk release-signed.apk && ${builtins.elemAt steps 0} && ${builtins.elemAt steps 1} && ${builtins.elemAt steps 2} && echo 'Okay, done with all. Now grab the apk and put it somewhere.'

      android-tools are available.'';
  env = [
    {
      name = "GRADLE_OPTS";
      value = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/34.0.0/aapt2";
    }
    {
      name = "ANDROID_SDK_ROOT";
      value = "${androidSdk}/libexec/android-sdk";
    }
  ];
  packages = [
    androidSdk
    jdk21
  ];
}
