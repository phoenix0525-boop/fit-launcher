[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleTasks = @("testDebugUnitTest", "assembleDebug")
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$buildRoot = $repoRoot
$mappedDrive = $null

if ($repoRoot -match "[^\x00-\x7F]") {
    foreach ($letter in @("R", "Q", "P", "O", "N")) {
        $candidate = "${letter}:"
        if (-not (Test-Path -LiteralPath "$candidate\")) {
            $mappedDrive = $candidate
            break
        }
    }
    if (-not $mappedDrive) {
        throw "No free temporary drive letter is available for the Android build."
    }

    & cmd.exe /c "subst $mappedDrive `"$repoRoot`""
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to map the project to $mappedDrive."
    }
    $buildRoot = "$mappedDrive\"
}

try {
    $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
    $sdkHome = [Environment]::GetEnvironmentVariable("ANDROID_HOME", "User")
    if (-not $javaHome -or -not (Test-Path -LiteralPath "$javaHome\bin\java.exe")) {
        throw "JAVA_HOME is not configured for the current Windows user."
    }
    if (-not $sdkHome -or -not (Test-Path -LiteralPath "$sdkHome\platforms\android-36\android.jar")) {
        throw "ANDROID_HOME is not configured with Android API 36."
    }

    $env:JAVA_HOME = $javaHome
    $env:ANDROID_HOME = $sdkHome
    $env:ANDROID_SDK_ROOT = $sdkHome
    $env:Path = "$javaHome\bin;$sdkHome\platform-tools;$sdkHome\cmdline-tools\latest\bin;$env:Path"

    & "$buildRoot\gradlew.bat" -p $buildRoot --no-daemon @GradleTasks
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE."
    }
}
finally {
    if ($mappedDrive) {
        & cmd.exe /c "subst $mappedDrive /D" | Out-Null
    }
}

Write-Output "Android build completed successfully."
