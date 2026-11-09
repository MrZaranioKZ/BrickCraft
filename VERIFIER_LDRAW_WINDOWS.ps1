param([string]$MinecraftFolder = "$env:APPDATA\.minecraft")
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$path = Join-Path $MinecraftFolder 'config\brickcraft\complete.zip'
Write-Host "Instance : $MinecraftFolder" -ForegroundColor Cyan
Write-Host "Archive  : $path"
if(-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    Write-Host "ABSENT : complete.zip n'est pas dans cette instance." -ForegroundColor Red
    Write-Host "Le Studio sera vide. Installe la bibliotheque ici, puis relance Minecraft."
    exit 2
}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[System.IO.Compression.ZipFile]::OpenRead($path)
try {
    $n=0; $design3004=$false
    foreach($entry in $zip.Entries) {
        $name=$entry.FullName.Replace('\','/').ToLowerInvariant()
        if($name -match '^(ldraw/)?parts/[^/]+\.dat$'){$n++}
        if($name -match '^(ldraw/)?parts/3004\.dat$'){$design3004=$true}
    }
    if($n -lt 1000 -or -not $design3004) {
        Write-Host "ARCHIVE NON CONFORME : $n pieces ; reference 3004 presente : $design3004" -ForegroundColor Red
        exit 3
    }
    Write-Host "OK : $n fichiers de pieces indexables, reference 3004 presente." -ForegroundColor Green
    Write-Host "Relance Minecraft ou utilise 'Recharger LDraw' dans le Studio."
} finally {$zip.Dispose()}
