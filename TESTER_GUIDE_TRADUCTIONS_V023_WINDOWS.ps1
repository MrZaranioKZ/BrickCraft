$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$root = Join-Path $PSScriptRoot 'src\main\resources\assets\brickcraft'
$items = Join-Path $PSScriptRoot 'src\main\java\fr\brickcraft'
$expected = @('fr_fr','en_us','es_es','de_de','pt_br','ru_ru','zh_cn','zh_tw','ja_jp','ar_sa','hi_in')
$must = @('guide.brickcraft.title','guide.brickcraft.page1.title','guide.brickcraft.page1.body','guide.brickcraft.page6.title','guide.brickcraft.page6.body','guide.brickcraft.next','guide.brickcraft.close','item.brickcraft.guide_book','command.brickcraft.guide.given','command.brickcraft.guide.hint')
foreach ($code in $expected) {
    $file = Join-Path $root "lang\$code.json"
    if (!(Test-Path $file)) { throw "Langue manquante : $code" }
    $json = Get-Content $file -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($key in $must) {
        if ($json.PSObject.Properties.Name -notcontains $key) { throw "Clé absente : $code / $key" }
        if ([string]::IsNullOrWhiteSpace([string]$json.$key)) { throw "Traduction vide : $code / $key" }
    }
    Write-Host "OK : $code / guide + commandes + objet"
}
foreach ($relative in @('models\item\guide_book.json','textures\item\guide_book.png')) {
    if (!(Test-Path (Join-Path $root $relative))) { throw "Asset manquant : $relative" }
}
$register = Get-Content (Join-Path $items 'BrickCraft.java') -Raw -Encoding UTF8
$creative = Get-Content (Join-Path $items 'BrickCreativeCatalog.java') -Raw -Encoding UTF8
$commands = Get-Content (Join-Path $items 'BrickCraftCommands.java') -Raw -Encoding UTF8
$guide = Get-Content (Join-Path $items 'BrickGuideBookItem.java') -Raw -Encoding UTF8
if ($register -notmatch 'GUIDE_BOOK\s*=') { throw 'GUIDE_BOOK absent du registre' }
if ($creative -notmatch 'entries\.add\(BrickCraft\.GUIDE_BOOK\)') { throw 'Guide absent du créatif' }
if ($commands -notmatch 'literal\("guide"\)') { throw 'Commande /brickcraft guide absente' }
if ($guide -notmatch 'BrickGuideBookScreen\.open') { throw 'Clic droit non relié au lecteur' }
Write-Host 'OK : registre, creatif, commande, clic droit et textures'
Write-Host 'Attention : ce test ne remplace pas une compilation Fabric ni un lancement dans Minecraft.'
