param(
    [Parameter(Mandatory = $true)][string]$SourceDir,
    [Parameter(Mandatory = $true)][string]$OutDir,
    [int]$MaxWidth = 0
)

Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName WindowsBase

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }

Get-ChildItem -Path $SourceDir -Filter *.webp | ForEach-Object {
    $uri = New-Object System.Uri($_.FullName)
    $decoder = [System.Windows.Media.Imaging.BitmapDecoder]::Create($uri, 'None', 'OnLoad')
    $frame = $decoder.Frames[0]

    $source = [System.Windows.Media.Imaging.BitmapSource]$frame
    if ($MaxWidth -gt 0 -and $frame.PixelWidth -gt $MaxWidth) {
        $scale = $MaxWidth / $frame.PixelWidth
        $transform = New-Object System.Windows.Media.ScaleTransform($scale, $scale)
        $source = New-Object System.Windows.Media.Imaging.TransformedBitmap($frame, $transform)
    }

    $encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
    $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($source))

    $target = Join-Path $OutDir ($_.BaseName + '.png')
    $stream = [System.IO.File]::Create($target)
    $encoder.Save($stream)
    $stream.Close()
    Write-Output "$($_.Name) -> $target ($($frame.PixelWidth)x$($frame.PixelHeight))"
}
