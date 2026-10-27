# Upload the EXISTING test key only. Never deletes, regenerates, or replaces a key.
$ErrorActionPreference = 'Stop'
$repo = 'phoenix0525-boop/fit-launcher'
$expected = ''
$folder = 'G:\我的雲端硬碟\小工具指令\Honda Fit車機\簽章金鑰（勿上傳GitHub）\測試金鑰'
$keyPath = Join-Path $folder 'vehicle-test.p12'
$ptr = [IntPtr]::Zero
try {
    $pinUrl = 'https://raw.githubusercontent.com/phoenix0525-boop/fit-launcher/codex/test-key-backup-v0415/docs/test-certificate.sha256'
    $expected = (Invoke-WebRequest -UseBasicParsing $pinUrl).Content.Trim().Replace(':','').ToUpperInvariant()
    if ($expected -notmatch '^[0-9A-F]{64} -LiteralPath $keyPath)) { throw 'Existing vehicle-test.p12 was not found. Do not create another key; check the folder first.' }
    foreach ($tool in @('gh','keytool')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is not on PATH. Close this window and open a new PowerShell window." }
    }
    & gh auth status --hostname github.com *> $null
    if ($LASTEXITCODE -ne 0) {
        & gh auth login --hostname github.com --git-protocol https --web
        if ($LASTEXITCODE -ne 0) { throw 'GitHub sign-in did not finish.' }
    }
    $secure = Read-Host 'Enter the EXISTING test-key password (hidden, never shared)' -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $env:VEHICLE_TEST_KEY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $lines = & keytool -J-Duser.language=en -J-Duser.country=US -list -v -keystore $keyPath -storetype PKCS12 -storepass:env VEHICLE_TEST_KEY_PASSWORD -alias vehicle-test
    if ($LASTEXITCODE -ne 0) { throw 'Cannot open the existing key. Check the password and alias; no secrets were changed.' }
    $match = [regex]::Match(($lines -join [Environment]::NewLine), 'SHA256:\s*([0-9A-Fa-f:]+)')
    if (-not $match.Success) { throw 'No public certificate SHA256 found; no secrets were changed.' }
    $actual = $match.Groups[1].Value.Replace(':','').ToUpperInvariant()
    Write-Host ('Public certificate SHA256: ' + $actual)
    if ($actual -ne $expected) { throw 'Public fingerprint differs from the one you supplied. No secrets were changed. Share ONLY the public SHA256 line.' }
    [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)) | gh secret set FIT_TEST_KEYSTORE_BASE64 --repo $repo
    if ($LASTEXITCODE -ne 0) { throw 'Keystore secret upload failed. Keep the existing key.' }
    $env:VEHICLE_TEST_KEY_PASSWORD | gh secret set FIT_TEST_KEYSTORE_PASSWORD --repo $repo
    if ($LASTEXITCODE -ne 0) { throw 'Password secret upload failed. Keep the existing key.' }
    'vehicle-test' | gh secret set FIT_TEST_KEY_ALIAS --repo $repo
    if ($LASTEXITCODE -ne 0) { throw 'Alias secret upload failed. Keep the existing key.' }
    $names = & gh secret list --repo $repo --json name --jq '.[].name'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot confirm repository secret names.' }
    foreach ($name in @('FIT_TEST_KEYSTORE_BASE64','FIT_TEST_KEYSTORE_PASSWORD','FIT_TEST_KEY_ALIAS')) {
        if ($names -notcontains $name) { throw "Secret missing after upload: $name" }
    }
    Write-Host 'SUCCESS: all three repository secret names confirmed. No new key was generated.' -ForegroundColor Green
    Write-Host 'Tell the assistant SUCCESS; do not send passwords, Base64, or private key files.'
} catch {
    Write-Host ('STOP: ' + $_.Exception.Message) -ForegroundColor Red
} finally {
    Remove-Item Env:VEHICLE_TEST_KEY_PASSWORD -ErrorAction SilentlyContinue
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
) { throw 'No confirmed test certificate is pinned yet. No secrets were changed.' }
    if ($expected -eq '52EE06F203BEA1460317B66170B99D07214B366E9A3A1E6F30D873E42C3835C6') { throw 'Production certificate cannot be used for test signing. No secrets were changed.' }
    if (-not (Test-Path -LiteralPath $keyPath)) { throw 'Existing vehicle-test.p12 was not found. Do not create another key; check the folder first.' }
    foreach ($tool in @('gh','keytool')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is not on PATH. Close this window and open a new PowerShell window." }
    }
    & gh auth status --hostname github.com *> $null
    if ($LASTEXITCODE -ne 0) {
        & gh auth login --hostname github.com --git-protocol https --web
        if ($LASTEXITCODE -ne 0) { throw 'GitHub sign-in did not finish.' }
    }
    $secure = Read-Host 'Enter the EXISTING test-key password (hidden, never shared)' -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $env:VEHICLE_TEST_KEY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $lines = & keytool -J-Duser.language=en -J-Duser.country=US -list -v -keystore $keyPath -storetype PKCS12 -storepass:env VEHICLE_TEST_KEY_PASSWORD -alias vehicle-test
    if ($LASTEXITCODE -ne 0) { throw 'Cannot open the existing key. Check the password and alias; no secrets were changed.' }
    $match = [regex]::Match(($lines -join [Environment]::NewLine), 'SHA256:\s*([0-9A-Fa-f:]+)')
    if (-not $match.Success) { throw 'No public certificate SHA256 found; no secrets were changed.' }
    $actual = $match.Groups[1].Value.Replace(':','').ToUpperInvariant()
    Write-Host ('Public certificate SHA256: ' + $actual)
    if ($actual -ne $expected) { throw 'Public fingerprint differs from the one you supplied. No secrets were changed. Share ONLY the public SHA256 line.' }
    [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)) | gh secret set FIT_TEST_KEYSTORE_BASE64 --repo $repo
    if ($LASTEXITCODE -ne 0) { throw 'Keystore secret upload failed. Keep the existing key.' }
    $env:VEHICLE_TEST_KEY_PASSWORD | gh secret set FIT_TEST_KEYSTORE_PASSWORD --repo $repo
    if ($LASTEXITCODE -ne 0) { throw 'Password secret upload failed. Keep the existing key.' }
    'vehicle-test' | gh secret set FIT_TEST_KEY_ALIAS --repo $repo
    if ($LASTEXITCODE -ne 0) { throw 'Alias secret upload failed. Keep the existing key.' }
    $names = & gh secret list --repo $repo --json name --jq '.[].name'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot confirm repository secret names.' }
    foreach ($name in @('FIT_TEST_KEYSTORE_BASE64','FIT_TEST_KEYSTORE_PASSWORD','FIT_TEST_KEY_ALIAS')) {
        if ($names -notcontains $name) { throw "Secret missing after upload: $name" }
    }
    Write-Host 'SUCCESS: all three repository secret names confirmed. No new key was generated.' -ForegroundColor Green
    Write-Host 'Tell the assistant SUCCESS; do not send passwords, Base64, or private key files.'
} catch {
    Write-Host ('STOP: ' + $_.Exception.Message) -ForegroundColor Red
} finally {
    Remove-Item Env:VEHICLE_TEST_KEY_PASSWORD -ErrorAction SilentlyContinue
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
