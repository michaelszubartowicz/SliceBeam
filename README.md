![Slice Beam logo](/.github/img/icon.png)
# Slice Beam - Portable 3D Model slicer
Slice Beam is a 3D model slicer/G-code generator for FFF 3D printers.

It is based on PrusaSlicer's core and well optimized for Android touchscreen interface.

# Links
- Telegram: https://t.me/ytkab0bp_channel
- Boosty (Patreon alternative): https://boosty.to/ytkab0bp
- K3D Chat for discussion & support (Russian language only): https://t.me/K_3_D

# Quick Start
[<img src="/.github/img/getongp.svg">](https://play.google.com/store/apps/details?id=ru.ytkab0bp.slicebeam)

Or download APK from [Releases tab](https://github.com/utkabobr/SliceBeam/releases/latest) and follow setup instructions.

# Where to get printer profiles?

It is recommended to use PrusaSlicer's profiles, Slice Beam profiles are using Prusa's format. You can import PrusaSlicer's profiles and vice versa.

But, there is an experimental OrcaSlicer profile support, please open Issue tickets with your .orca_printer files, so I can check what wents wrong on your specific case.

# Some screenshots
![Screenshot 1](/.github/img/screen1.png) ![Screenshot 2](/.github/img/screen2.png) ![Screenshot 3](/.github/img/screen3.png) ![Screenshot 4](/.github/img/screen4.png)

# Forks policy
I'm **VERY** convinced that you should **NOT** create forks just because you want a slicer with your name.

Re-skins are **ruining** community's efforts (You're just bundling outdated builds with a different name), please create pull requests to mainline, so **more** people will get to use **your** features.

If you wish your cloud services to be integrated into Slice Beam, just create a pull request, I'll try to merge them as soon, as I get some free time. Or you could provide me with your hardware/printers and documentation so I can make it on my own _(Who would even do this?)_

# No Kotlin?
![No Kotlin](/.github/img/nokt.jpg)

Yes, I'm **not** using Kotlin in my projects, Slice Beam is not an exception.

The build process already consumes a lot of time, using Kotlin will increase it drastically.

Please do not create pull requests with Kotlin source code, they will be declined.

# License
Slice Beam is licensed under the  _GNU Affero General Public License, version 3_.

It inherits license from PrusaSlicer, which is originally based on Slic3r by Alessandro Ranellucci.

It means that you **CAN NOT** just download all the source code, re-skin it and force your printer to work only with your slicer without releasing your source code.

You must **disclose your source code** as required by AGPL v3. Failing to do so will result in DMCA strikes on Google Play, GitHub releases (Hi Anycubic :P), Huawei AppGallery, etc.

# Building tips
## Boost
Build from this repo: https://github.com/moritz-wundke/Boost-for-Android, then copy libs from build/out into app/src/main/boost.

## oneTBB
Build from this repo: https://github.com/syoyo/openvdb-android, then copy .a libs into app/src/main/jniImports/oneTBB; .so libs into app/src/main/jniLibs

## GMP & MPFR
GMP & MPFR are bundled, but anyway they're from here: https://github.com/flaktack/android-mpfr

## OCCT
Use original repo: https://github.com/Open-Cascade-SAS/OCCT

Build using adm/scripts/android_build

Copy .so libs to app/src/main/occt/jniLibs; Include files to app/src/main/occt/include

You can also reduce size of libraries by removing unnecessary modules (Check app/src/main/occt/jniLibs/clean.py)

## Why not to include all of them in repo?
They're HUGE (More than 2 Gb) binary files, so you must build them yourself.
