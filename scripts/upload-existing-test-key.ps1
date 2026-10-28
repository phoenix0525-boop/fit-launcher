# Windows PowerShell 5.1 compatible. Uploads the EXISTING test key only.
# Never deletes, generates, renames or replaces a private key.
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Stop'
$repo = 'phoenix0525-boop/fit-launcher'
$expected = '945997E4EB8E3D19A79D5CAB2E841762DF5D9B0AB57AEF320BE0591BDC693917'
$folder = 'G:\我的雲端硬碟\小工具指令\Honda Fit車機\簽章金鑰（勿上傳GitHub）\測試金鑰'
$keyPath = Join-Path $folder 'vehicle-test.p12'
$ptr = [IntPtr]::Zero
$secure = $null
try {
    if (-not (Test-Path -LiteralPath $keyPath)) {
        throw 'Existing vehicle-test.p12 was not found. Keep all existing keys and check the folder path.'
    }
    foreach ($tool in @('gh', 'keytool')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            throw "$tool is not on PATH. Close this window and open a new PowerShell window."
        }
    }
    $ErrorActionPreference = 'Continue'
    & gh auth status --hostname github.com *> $null
    $authCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($authCode -ne 0) {
        throw 'GitHub CLI is not logged in. Run gh auth login and complete browser authorization first.'
    }
    $secure = Read-Host 'Enter the EXISTING test-key password (hidden, never shared)' -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $env:VEHICLE_TEST_KEY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $lines = & keytool -J-Duser.language=en -J-Duser.country=US -list -v -keystore $keyPath -storetype PKCS12 -storepass:env VEHICLE_TEST_KEY_PASSWORD -alias vehicle-test
    if ($LASTEXITCODE -ne 0) {
        throw 'Cannot open the existing test key. Check the password; no secrets were changed.'
    }
    $match = [regex]::Match(($lines -join [Environment]::NewLine), 'SHA256:\s*([0-9A-Fa-f:]+)')
    if (-not $match.Success) {
        throw 'No public certificate SHA256 found; no secrets were changed.'
    }
    $actual = $match.Groups[1].Value.Replace(':', '').ToUpperInvariant()
    Write-Host ('Public certificate SHA256: ' + $actual)
    if ($actual -ne $expected) {
        throw 'Public test fingerprint does not match. No secrets were changed. Share only the public SHA256 line.'
    }

    [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)) | gh secret set FIT_TEST_KEYSTORE_BASE64 --repo $repo
    if ($LASTEXITCODE -ne 0) { throw 'Keystore secret upload failed. Retain the existing key and retry only upload.' }

    $env:VEHICLE_TEST_KEY_PASSWORD | gh secret set FIT_TEST_KEYSTORE_PASSWORD --repo $repo
    if ($LASTEXITCODE -ne 0) { throw 'Password secret upload failed. Retain the existing key.' }

    'vehicle-test' | gh secret set FIT_TEST_KEY_ALIAS --repo $repo
    if ($LASTEXITCODE -ne 0) { throw 'Alias secret upload failed. Retain the existing key.' }

    $names = @(& gh secret list --repo $repo --json name --jq '.[].name')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot verify repository secret names.' }
    foreach ($name in @('FIT_TEST_KEYSTORE_BASE64', 'FIT_TEST_KEYSTORE_PASSWORD', 'FIT_TEST_KEY_ALIAS')) {
        if ($names -notcontains $name) { throw "Secret name was not confirmed: $name" }
    }
    Write-Host 'SUCCESS: all three repository secret names confirmed.' -ForegroundColor Green
    Write-Host 'No new key was generated. Tell the assistant SUCCESS; never share passwords or private key files.'
} catch {
    Write-Host ('STOP: ' + $_.Exception.Message) -ForegroundColor Red
} finally {
    Remove-Item Env:VEHICLE_TEST_KEY_PASSWORD -ErrorAction SilentlyContinue
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    if ($null -ne $secure) { $secure.Dispose() }
    $ErrorActionPreference = $previousErrorActionPreference
}
