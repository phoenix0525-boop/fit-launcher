# Run locally outside the repository. Never paste passwords/base64 into chat, docs or commits.
$ErrorActionPreference = 'Stop'
$folder = 'G:\我的雲端硬碟\小工具指令\Honda Fit車機\簽章金鑰（勿上傳GitHub）\測試金鑰'
New-Item -ItemType Directory -Force -Path $folder | Out-Null
$keyPath = Join-Path $folder 'vehicle-test.p12'
if (Test-Path $keyPath) { throw 'Key already exists. Do not replace it.' }
$secure = Read-Host '設定測試金鑰密碼（請自行保存，不輸入聊天或文件）' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
  $env:VEHICLE_TEST_KEY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
  & keytool -genkeypair -keystore $keyPath -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 -alias vehicle-test -dname 'CN=Vehicle Launcher Test Only' -storepass:env VEHICLE_TEST_KEY_PASSWORD
  if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
  & keytool -list -v -keystore $keyPath -storetype PKCS12 -storepass:env VEHICLE_TEST_KEY_PASSWORD -alias vehicle-test
  # gh reads stdin; neither private key nor password is printed or passed as command-line text.
  [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)) | gh secret set FIT_TEST_KEYSTORE_BASE64 --repo phoenix0525-boop/fit-launcher
  if ($LASTEXITCODE -ne 0) { throw 'Keystore secret upload failed; retain the existing key and retry only upload.' }
  $env:VEHICLE_TEST_KEY_PASSWORD | gh secret set FIT_TEST_KEYSTORE_PASSWORD --repo phoenix0525-boop/fit-launcher
  if ($LASTEXITCODE -ne 0) { throw 'Password secret upload failed' }
  'vehicle-test' | gh secret set FIT_TEST_KEY_ALIAS --repo phoenix0525-boop/fit-launcher
  if ($LASTEXITCODE -ne 0) { throw 'Alias secret upload failed' }
} finally {
  Remove-Item Env:VEHICLE_TEST_KEY_PASSWORD -ErrorAction SilentlyContinue
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}
Write-Host '請只回報公開 SHA-256 指紋；不要回報密碼或金鑰內容。'
