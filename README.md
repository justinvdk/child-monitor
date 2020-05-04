# Child Monitor
An Open Source Child Monitor for Android

_Child Monitor_ allows two Android devices to act as a baby monitor. The first device,
left in the room with the baby, will advertise itself on the network and stream audio
to a connected client. The second device, with the parent, will connect to the monitoring
device and receive an audio stream.

The project is a fork of _Protect Baby Monitor_ which is declared as "on hiatus" by its developer.

_Child Monitor_ works on Android 4.4 (KitKat) and newer, i.e. Android SDK 19.

The current version of _Child Monitor_ is rudimentary at best. It is capable
of successfully advertising itself on the network, allows clients to connect,
and streams audio. Room for improvement includes:

1. Robust usage of the AudioTrack API
2. Handle dropped packets gracefully
3. Use of GSM when no internet connectivity is available

At the time this project was forked from _Protect Baby Monitor_ there was no obvious open source solution for a
baby monitor for Android in F-Droid.

# Thanks
Audio file originals from [freesound](https://freesound.org).
This whole project originally created by [brarcher](https://github.com/brarcher/protect-baby-monitor).
