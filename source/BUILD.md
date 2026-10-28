Build on Windows with JDK 25:

```powershell
.\build.ps1 -JavaHome 'C:\Java\jdk-25'
```

If `JAVA_HOME` or `javac.exe` on PATH already points to JDK 25, `-JavaHome` is optional.
The script compiles the official, unobfuscated Minecraft 26.3 APIs and packages only this mod's classes and resources into `build\libs\mobslearn-<version>.jar`.

`tools\build-dependencies.json` pins Minecraft 26.3, Fabric Loader 0.19.5 and Fabric API 0.162.0+26.3, with download URLs and the SHA-1 checksums published by Mojang/Fabric. The script verifies every dependency before use. It reuses verified local Gradle caches where available, otherwise downloads the pinned files into `.build-deps`. Fabric API's nested JARs are extracted only for the compiler classpath. Dependencies are never bundled into the mod JAR.

Once dependencies are cached, build without network access:

```powershell
.\build.ps1 -JavaHome 'C:\Java\jdk-25' -Offline
```

The JAR timestamp is fixed for reproducibility. Install the resulting JAR alongside a compatible Fabric API in a Minecraft 26.3 Fabric instance (Loader 0.19.5, Java 25).
