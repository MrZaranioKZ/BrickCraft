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
$version = "8.10.2"
$launcher = Join-Path $root ".local-gradle\gradle-$version\bin\gradle.bat"
if (!(Test-Path $launcher)) {
  Write-Host "Téléchargement officiel de Gradle $version (uniquement pour la compilation)..."
  $dir = Join-Path $root ".local-gradle"
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $zip = Join-Path $dir "gradle-$version-bin.zip"
  Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$version-bin.zip" -OutFile $zip
  Expand-Archive -Path $zip -DestinationPath $dir -Force
  Remove-Item $zip
}
& $launcher $Goal --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Échec Gradle (code $LASTEXITCODE). Consulte les erreurs ci-dessus." }
if ($Goal -eq "build") { Write-Host "JAR : build\libs\$archiveName-$modVersion.jar" -ForegroundColor Green }
