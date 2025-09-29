<# 
  build-msi.ps1  —  Empaqueta la app JavaFX en app-image y MSI

  Uso:
    .\build-msi.ps1                  # Toma versión del pom.xml (sanea -SNAPSHOT)
    .\build-msi.ps1 -Version 1.2.3   # Fuerza versión MSI concreta
    .\build-msi.ps1 -WinConsole       # App-image con consola (útil para depurar)

  Notas:
    - MSI requiere versión Major.Minor.Build (X.Y.Z). Si el pom tiene -SNAPSHOT, se elimina.
    - PATH_JAVAFX_JMODS debe apuntar a la carpeta de jmods (p.ej. C:\Program Files\Java\javafx-jmods-21.0.8)
#>

[CmdletBinding()]
param(
  [string]$Version = $null,
  [switch]$WinConsole
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Write-Info($msg)  { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Warn($msg)  { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Err ($msg)  { Write-Host "[ERROR] $msg" -ForegroundColor Red }

# --- 1) Validación de entorno ---
if (-not $env:JAVA_HOME) { throw "JAVA_HOME no está definido." }
$Jmods = Join-Path $env:JAVA_HOME 'jmods'
if (-not (Test-Path $Jmods)) { throw "No existe $Jmods (JAVA_HOME incorrecto?)." }

if (-not $env:PATH_JAVAFX_JMODS) { 
  throw "PATH_JAVAFX_JMODS no está definido. Debe apuntar a la carpeta de jmods de JavaFX (p.ej. C:\Program Files\Java\javafx-jmods-21.0.8)." 
}
if (-not (Test-Path (Join-Path $env:PATH_JAVAFX_JMODS 'javafx.web.jmod'))) {
  throw "No se encontró javafx.web.jmod en $($env:PATH_JAVAFX_JMODS). Revisa PATH_JAVAFX_JMODS."
}

# --- 2) Versionado: leer del pom si no se pasa ---
[xml]$pom = Get-Content -LiteralPath .\pom.xml
$pomVersion = $pom.project.version
if (-not $pomVersion) { throw "No se encontró <version> en pom.xml" }

if (-not $Version) {
  $Version = ($pomVersion -replace '-SNAPSHOT','')
  Write-Info "Versión MSI derivada del pom: $Version (pom: $pomVersion)"
} else {
  Write-Info "Versión MSI forzada por parámetro: $Version (pom: $pomVersion)"
}

# MSI exige X.Y.Z
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
  throw "La versión MSI debe ser Major.Minor.Build (X.Y.Z). Recibido: '$Version'."
}

# --- 3) Paths de salida ---
$TargetDir     = Resolve-Path -LiteralPath .\target
$JpkgDir       = Join-Path $TargetDir 'jpkg'
$ImageDir      = Join-Path $TargetDir 'image'
$InstallerDir  = Join-Path $TargetDir 'installer'

# --- 4) Build limpio + shaded JAR ---
Write-Info "Limpieza y compilación (mvn clean package -DskipTests)"
& mvn -q clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Falló 'mvn clean package'." }

# Crea jpkg y copia el shaded
New-Item -ItemType Directory -Force -Path $JpkgDir | Out-Null

# Busca el shaded jar en target (classifier 'shaded')
$shaded = Get-ChildItem -LiteralPath $TargetDir -Filter '*-shaded.jar' | Select-Object -First 1
if (-not $shaded) { throw "No se encontró el shaded JAR en target (*-shaded.jar). Revisa el maven-shade-plugin." }

Copy-Item -LiteralPath $shaded.FullName -Destination (Join-Path $JpkgDir 'app.jar') -Force
Write-Info "Shaded copiado: $($shaded.Name) -> $JpkgDir\app.jar"

# --- 5) jpackage: app-image ---
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $ImageDir
New-Item -ItemType Directory -Force -Path $ImageDir | Out-Null

# Ruta del icono .ico
$IconPath = Resolve-Path -LiteralPath .\src\main\resources\icons\gdg.ico

# Módulos a incluir (JDK + JavaFX)
$Modules = @(
  'java.sql','java.xml','java.logging','java.desktop',
  'jdk.crypto.ec','jdk.localedata',
  'javafx.controls','javafx.fxml','javafx.web','javafx.graphics'
) -join ','

# Construir argumentos comunes
$appImageArgs = @(
  '--type','app-image',
  '--name','Pildoras',
  '--input', $JpkgDir,
  '--main-jar','app.jar',
  '--main-class','io.github.guillermo_david.MainApp',
  '--icon', $IconPath,
  '--module-path', "$Jmods;$($env:PATH_JAVAFX_JMODS)",
  '--add-modules', $Modules,
  '--java-options','-Dprism.order=sw',
  '--dest', $ImageDir,
  '--verbose'
)

if ($WinConsole) {
  $appImageArgs += '--win-console'
  Write-Warn "Generando app-image con consola (modo depuración)."
}

Write-Info "Creando app-image…"
& jpackage @appImageArgs
if ($LASTEXITCODE -ne 0) { throw "Falló jpackage app-image." }

# --- 6) jpackage: MSI ---
New-Item -ItemType Directory -Force -Path $InstallerDir | Out-Null

# UUID estable para upgrades (no lo cambies entre versiones)
$UpgradeUUID = '6498aa45-98d5-3fb7-8e59-af25e1dfc8c2'

$msiArgs = @(
  '--type','msi',
  '--name','Pildoras',
  '--app-image', (Join-Path $ImageDir 'Pildoras'),
  '--vendor','Guillermo David García',
  '--app-version', $Version,
  '--win-menu',
  '--win-shortcut',
  '--win-per-user-install',
  '--win-dir-chooser',
  '--win-upgrade-uuid', $UpgradeUUID,
  '--dest', $InstallerDir,
  '--verbose'
  # Nota: NO usar --win-console en MSI
)

Write-Info "Creando MSI (versión $Version)…"
& jpackage @msiArgs
if ($LASTEXITCODE -ne 0) { throw "Falló jpackage MSI." }

Write-Host ""
Write-Host "✅ Listo. Salidas:" -ForegroundColor Green
Write-Host "   App-image : $ImageDir\Pildoras"
Write-Host "   MSI       : $InstallerDir" 
