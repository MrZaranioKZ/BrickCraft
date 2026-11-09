param([string]$MinecraftFolder = "$env:APPDATA\.minecraft")
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$targetDir = Join-Path $MinecraftFolder "config\brickcraft"
$dest = Join-Path $targetDir "complete.zip"
$remote = "https://library.ldraw.org/library/updates/complete.zip"
Write-Host "BrickCraft - installation de la bibliotheque officielle LDraw" -ForegroundColor Cyan
Write-Host "Source : $remote"
Write-Host "Destination : $dest"
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
if(Test-Path $dest) {
    Write-Host "Une bibliotheque est deja installee. Le fichier sera mis a jour." -ForegroundColor Yellow
}
$tmp = "$dest.partial"
try {
    Invoke-WebRequest -Uri $remote -OutFile $tmp -UseBasicParsing
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[System.IO.Compression.ZipFile]::OpenRead($tmp)
    try {
        $found=$false; $count=0
        foreach($e in $zip.Entries) {
            $name=$e.FullName.Replace('\','/').ToLowerInvariant()
            if($name -match '^(ldraw/)?parts/[^/]+\.dat$'){$found=$true;$count++}
        }
        if(-not $found -or $count -lt 1000) {throw "Archive ZIP inattendue : $count pieces a la racine de parts/"}
    } finally {$zip.Dispose()}
    Move-Item -Force $tmp $dest
    Write-Host "Termine : $count fichiers de pieces accessibles dans $dest" -ForegroundColor Green
    Write-Host "Relance Minecraft pour charger le nouveau catalogue." -ForegroundColor Cyan
} catch {
    if(Test-Path $tmp){Remove-Item -Force $tmp}
    throw
}
