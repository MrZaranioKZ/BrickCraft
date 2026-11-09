param([string]$Goal = "build")
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
# Read the real version instead of displaying a stale hardcoded build name.
$projectProperties = @{}
Get-Content (Join-Path $root "gradle.properties") | ForEach-Object {
  if ($_ -match '^\s*([^#=\s]+)\s*=(.*)$') { $projectProperties[$Matches[1]] = $Matches[2].Trim() }
}
$modVersion = $projectProperties['mod_version']
$archiveName = $projectProperties['archives_base_name']
if (-not $modVersion -or -not $archiveName) { throw "gradle.properties : version ou nom du JAR absent." }
Write-Host "BrickCraft $modVersion / Fabric 1.21.1" -ForegroundColor Cyan
if (!(Get-Command java -ErrorAction SilentlyContinue)) {
  throw "Java 21 absent. Installe JDK 21 (par exemple Eclipse Temurin) avant de construire."
}
$wrapper = Join-Path $root "gradle\wrapper\gradle-wrapper.jar"
$expected = "2DB75C40782F5E8BA1FC278A5574BAB070ADCCB2D21CA5A6E5ED840888448046"
if ((Get-FileHash -LiteralPath $wrapper -Algorithm SHA256).Hash -ne $expected) {
  throw "Somme SHA-256 du Gradle Wrapper incorrecte."
}
$launcher = Join-Path $root "gradlew.bat"
& $launcher $Goal --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Échec Gradle (code $LASTEXITCODE). Consulte les erreurs ci-dessus." }
if ($Goal -eq "build") { Write-Host "JAR : build\libs\$archiveName-$modVersion.jar" -ForegroundColor Green }
