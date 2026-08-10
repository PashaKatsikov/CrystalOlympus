<#
Works out where the objects on a sprite sheet actually sit.

The shipped sheets place several objects on one canvas, but never at even intervals, so cutting the
canvas into equal cells clips artwork. This script measures the empty gutters between the objects and
reports a cut line through the middle of each one.

Windows' WebP codec throws the alpha channel away and hands back pure black wherever the image is
transparent, so "black" is used as the background key here. That is only good enough to find the
gutters; the exact silhouette is still trimmed on device where the real alpha is available.
#>
param(
    [Parameter(Mandatory = $true)][string]$SourceDir,
    [string]$OutFile = 'tools\sheet_layout.txt',
    [string]$CropDir = 'tools\slices'
)

Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName WindowsBase

# file -> rows, cols expected on that sheet
$layouts = [ordered]@{
    'Mythical_Creatures_Set_1_asset.webp'   = @(1, 4)
    'Mythical_Creatures_Set_2_asset.webp'   = @(1, 4)
    'Lightning_Titan_Boss_asset.webp'       = @(1, 4)
    'Olympus_Magic_Fruits_Set_asset.webp'   = @(1, 4)
    'Divine_Energy_Coin_asset.webp'         = @(1, 4)
    'Olympus_Columns_Set_asset.webp'        = @(1, 4)
    'Olympus_Magical_Plants_Set_asset.webp' = @(1, 4)
    'Olympus_Clouds_Set_asset.webp'         = @(2, 2)
    'Olympus_Stone_Elements_Set_asset.webp' = @(2, 2)
    'Olympus_Traps_Set_asset.webp'          = @(2, 2)
}

$LUMA_THRESHOLD = 12

function Get-Pixels([string]$path) {
    $uri = New-Object System.Uri((Resolve-Path $path).Path)
    $decoder = [System.Windows.Media.Imaging.BitmapDecoder]::Create($uri, 'None', 'OnLoad')
    $frame = $decoder.Frames[0]
    $converted = New-Object System.Windows.Media.Imaging.FormatConvertedBitmap($frame, [System.Windows.Media.PixelFormats]::Bgra32, $null, 0)
    $w = $converted.PixelWidth
    $h = $converted.PixelHeight
    $stride = $w * 4
    $buffer = New-Object byte[] ($stride * $h)
    $converted.CopyPixels($buffer, $stride, 0)
    return @{ Bytes = $buffer; Width = $w; Height = $h; Stride = $stride; Frame = $frame }
}

# How many non-background pixels each column (or row) of the window holds.
function Get-Profile($img, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [bool]$byColumn) {
    $bytes = $img.Bytes
    $stride = $img.Stride
    $length = if ($byColumn) { $x1 - $x0 } else { $y1 - $y0 }
    $profile = New-Object int[] $length

    for ($y = $y0; $y -lt $y1; $y++) {
        $rowStart = $y * $stride
        for ($x = $x0; $x -lt $x1; $x++) {
            $i = $rowStart + $x * 4
            if ($bytes[$i] -gt $LUMA_THRESHOLD -or $bytes[$i + 1] -gt $LUMA_THRESHOLD -or $bytes[$i + 2] -gt $LUMA_THRESHOLD) {
                if ($byColumn) { $profile[$x - $x0]++ } else { $profile[$y - $y0]++ }
            }
        }
    }
    return , $profile
}

# Splits a strip into exactly $count pieces.
#
# Empty gutters between neighbouring objects are the natural cut lines, so the widest ones win. When
# two objects are close enough that their glows touch, no gutter exists and the widest remaining
# piece is cut at its thinnest point instead.
function Get-Cuts([int[]]$profile, [int]$count, [int]$noiseFloor) {
    if ($count -le 1) { return @() }

    $gaps = New-Object System.Collections.ArrayList
    $start = -1
    for ($i = 0; $i -lt $profile.Length; $i++) {
        $empty = $profile[$i] -le $noiseFloor
        if ($empty -and $start -lt 0) { $start = $i }
        if (-not $empty -and $start -ge 0) {
            if ($start -gt 0) { [void]$gaps.Add(@{ From = $start; To = $i }) }
            $start = -1
        }
    }

    $cuts = New-Object System.Collections.ArrayList
    foreach ($gap in ($gaps | Sort-Object { $_.To - $_.From } -Descending | Select-Object -First ($count - 1))) {
        [void]$cuts.Add([int](($gap.From + $gap.To) / 2))
    }

    # No gutter for one of the splits: carve the widest piece at its quietest column instead.
    while ($cuts.Count -lt $count - 1) {
        $edges = @(0) + ($cuts | Sort-Object) + @($profile.Length)
        $bestFrom = 0; $bestTo = 0
        for ($i = 0; $i -lt $edges.Count - 1; $i++) {
            if ($edges[$i + 1] - $edges[$i] -gt $bestTo - $bestFrom) { $bestFrom = $edges[$i]; $bestTo = $edges[$i + 1] }
        }
        $margin = [int](($bestTo - $bestFrom) * 0.2)
        $searchFrom = $bestFrom + $margin
        $searchTo = $bestTo - $margin
        $bestX = $searchFrom
        for ($x = $searchFrom; $x -lt $searchTo; $x++) {
            if ($profile[$x] -lt $profile[$bestX]) { $bestX = $x }
        }
        [void]$cuts.Add($bestX)
    }

    return @($cuts | Sort-Object)
}

function Save-Crop($img, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [string]$target) {
    $rect = New-Object System.Windows.Int32Rect $x0, $y0, ($x1 - $x0), ($y1 - $y0)
    $cropped = New-Object System.Windows.Media.Imaging.CroppedBitmap($img.Frame, $rect)
    $encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
    $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($cropped, $null, $null, $null))
    $stream = [System.IO.File]::Create($target)
    $encoder.Save($stream)
    $stream.Close()
}

if (-not (Test-Path $CropDir)) { New-Item -ItemType Directory -Path $CropDir -Force | Out-Null }

$lines = @()
foreach ($file in $layouts.Keys) {
    $rows = $layouts[$file][0]
    $cols = $layouts[$file][1]
    $img = Get-Pixels (Join-Path $SourceDir $file)
    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($file)

    $columnNoise = [int]($img.Height * 0.004)
    $rowNoise = [int]($img.Width * 0.004)

    $columnCuts = @(0) + (Get-Cuts (Get-Profile $img 0 0 $img.Width $img.Height $true) $cols $columnNoise) + @($img.Width)

    $lines += "$file  ${rows}x${cols}  $($img.Width)x$($img.Height)"
    for ($c = 0; $c -lt $cols; $c++) {
        $left = $columnCuts[$c]
        $right = $columnCuts[$c + 1]
        $rowCuts = @(0) + (Get-Cuts (Get-Profile $img $left 0 $right $img.Height $false) $rows $rowNoise) + @($img.Height)

        for ($r = 0; $r -lt $rows; $r++) {
            $top = $rowCuts[$r]
            $bottom = $rowCuts[$r + 1]
            $cell = $r * $cols + $c
            $lines += ("  cell {0}  px {1},{2},{3},{4}  frac {5},{6},{7},{8}" -f `
                    $cell, $left, $top, $right, $bottom, `
                ($left / $img.Width).ToString('F4', [cultureinfo]::InvariantCulture), `
                ($top / $img.Height).ToString('F4', [cultureinfo]::InvariantCulture), `
                ($right / $img.Width).ToString('F4', [cultureinfo]::InvariantCulture), `
                ($bottom / $img.Height).ToString('F4', [cultureinfo]::InvariantCulture))
            Save-Crop $img $left $top $right $bottom (Join-Path $CropDir "${baseName}_$cell.png")
        }
    }
}

$lines | Set-Content -Path $OutFile -Encoding UTF8
$lines | Write-Output
