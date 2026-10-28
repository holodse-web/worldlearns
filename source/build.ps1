param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$sourceRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$cacheRoot = Join-Path $sourceRoot '.build-deps'
$buildRoot = Join-Path $sourceRoot 'build'
$classesRoot = Join-Path $buildRoot 'classes'
$lock = Get-Content -LiteralPath (Join-Path $sourceRoot 'tools/build-dependencies.json') -Raw | ConvertFrom-Json

if (-not $JavaHome) {
    $javaCommand = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javaCommand) { $JavaHome = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent }
}
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { '' }
$jar = if ($JavaHome) { Join-Path $JavaHome 'bin/jar.exe' } else { '' }
if (-not $javac -or -not (Test-Path -LiteralPath $javac) -or -not (Test-Path -LiteralPath $jar)) {
    throw 'JDK 25 is required. Set JAVA_HOME or run .\build.ps1 -JavaHome C:\Java\jdk-25'
}
$compilerVersion = (& $javac -version 2>&1 | Out-String).Trim()
if ($compilerVersion -notmatch '^javac (\d+)' -or [int]$Matches[1] -lt 25) {
    throw "JDK 25 or newer is required; found $compilerVersion"
}

New-Item -ItemType Directory -Path $cacheRoot, $buildRoot -Force | Out-Null
$script:localJarIndex = $null
function Get-VerifiedDependency($dependency) {
    $destination = [IO.Path]::GetFullPath((Join-Path $cacheRoot $dependency.path))
    if (-not $destination.StartsWith($cacheRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Invalid dependency path: $($dependency.path)"
    }
    if ((Test-Path -LiteralPath $destination) -and
        (Get-FileHash -LiteralPath $destination -Algorithm SHA1).Hash -eq $dependency.sha1) { return $destination }

    New-Item -ItemType Directory -Path (Split-Path $destination -Parent) -Force | Out-Null
    if ($null -eq $script:localJarIndex) {
        $script:localJarIndex = @{}
        $gradleRoot = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle/caches'
        foreach ($searchRoot in @((Join-Path $gradleRoot 'modules-2/files-2.1'), (Join-Path $gradleRoot "fabric-loom/$($lock.minecraft)"))) {
            if (Test-Path -LiteralPath $searchRoot) {
                Get-ChildItem -LiteralPath $searchRoot -Filter '*.jar' -File -Recurse | ForEach-Object {
                    if (-not $script:localJarIndex.ContainsKey($_.Name)) { $script:localJarIndex[$_.Name] = @() }
                    $script:localJarIndex[$_.Name] += $_.FullName
                }
            }
        }
    }
    $names = @([IO.Path]::GetFileName($destination))
    if ($dependency.path -eq "minecraft/client-$($lock.minecraft).jar") { $names += 'minecraft-client.jar' }
    foreach ($name in $names) {
        foreach ($candidate in @($script:localJarIndex[$name])) {
            if ($candidate -and (Get-FileHash -LiteralPath $candidate -Algorithm SHA1).Hash -eq $dependency.sha1) {
                Copy-Item -LiteralPath $candidate -Destination $destination -Force
                return $destination
            }
        }
    }
    if ($Offline) { throw "Missing verified dependency in offline mode: $($dependency.path)" }
    Write-Host "Downloading $([IO.Path]::GetFileName($destination))"
    $temporary = $destination + '.download'
    try {
        Invoke-WebRequest -Uri $dependency.url -OutFile $temporary -UseBasicParsing
        if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA1).Hash -ne $dependency.sha1) {
            throw "Dependency checksum mismatch: $($dependency.path)"
        }
        Move-Item -LiteralPath $temporary -Destination $destination -Force
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
    return $destination
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$classPath = [Collections.Generic.List[string]]::new()
foreach ($dependency in $lock.dependencies) {
    $dependencyFile = Get-VerifiedDependency $dependency
    $classPath.Add($dependencyFile)
    if ($dependency.extractNested) {
        $archive = [IO.Compression.ZipFile]::OpenRead($dependencyFile)
        try {
            foreach ($entry in $archive.Entries) {
                if ($entry.FullName -match '^META-INF/jars/([^/]+\.jar)$') {
                    $nestedFile = Join-Path $cacheRoot ('fabric-api-expanded/' + $Matches[1])
                    New-Item -ItemType Directory -Path (Split-Path $nestedFile -Parent) -Force | Out-Null
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $nestedFile, $true)
                    $classPath.Add($nestedFile)
                }
            }
        } finally { $archive.Dispose() }
    }
}

# Only this generated directory is cleaned; source and previous release JARs are preserved.
$resolvedClasses = [IO.Path]::GetFullPath($classesRoot)
if (-not $resolvedClasses.StartsWith($buildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing to clean a directory outside the build directory.'
}
if (Test-Path -LiteralPath $resolvedClasses) { Remove-Item -LiteralPath $resolvedClasses -Recurse -Force }
New-Item -ItemType Directory -Path $classesRoot, (Join-Path $buildRoot 'libs') -Force | Out-Null
$javaSources = @(Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'src/main/java') -Filter '*.java' -Recurse -File | Sort-Object FullName)
if ($javaSources.Count -eq 0) { throw 'No Java sources found.' }
$arguments = @('--release', '25', '-encoding', 'UTF-8', '-proc:none', '-Xlint:-options', '-nowarn', '-classpath',
    ('"' + ($classPath -join ';').Replace('\', '/') + '"'), '-d', ('"' + $classesRoot.Replace('\', '/') + '"'))
$arguments += $javaSources | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
$argumentFile = Join-Path $buildRoot 'javac.args'
[IO.File]::WriteAllLines($argumentFile, $arguments, [Text.UTF8Encoding]::new($false))
Write-Host "Compiling $($javaSources.Count) sources with $compilerVersion"
& $javac ('@' + $argumentFile)
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed with exit code $LASTEXITCODE" }
Copy-Item -Path (Join-Path $sourceRoot 'src/main/resources/*') -Destination $classesRoot -Recurse -Force
$metadata = Get-Content -LiteralPath (Join-Path $classesRoot 'fabric.mod.json') -Raw | ConvertFrom-Json
$outputFile = Join-Path $buildRoot "libs/mobslearn-$($metadata.version).jar"
& $jar --create --file $outputFile --date=2026-10-28T00:00:00Z -C $classesRoot .
if ($LASTEXITCODE -ne 0) { throw "JAR packaging failed with exit code $LASTEXITCODE" }
$classCount = @(Get-ChildItem -LiteralPath $classesRoot -Filter '*.class' -Recurse -File).Count
Write-Host "Built $outputFile ($classCount classes)"
Write-Host "SHA256: $((Get-FileHash -LiteralPath $outputFile -Algorithm SHA256).Hash.ToLowerInvariant())"
