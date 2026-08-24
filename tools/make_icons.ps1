param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$ResDir
)

Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName WindowsBase

Add-Type -ReferencedAssemblies PresentationCore, WindowsBase, System.Xaml -TypeDefinition @'
using System;
using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;

public static class IconMaker
{
    // Renders the source into a canvasSize square, the artwork covering contentFraction of it.
    public static void Render(string source, string outFile, int canvasSize, double contentFraction)
    {
        string fullPath = Path.GetFullPath(source);
        var decoder = BitmapDecoder.Create(new Uri(fullPath), BitmapCreateOptions.None, BitmapCacheOption.OnLoad);
        BitmapSource frame = decoder.Frames[0];

        double content = canvasSize * contentFraction;
        double offset = (canvasSize - content) / 2.0;

        var visual = new DrawingVisual();
        using (var ctx = visual.RenderOpen())
        {
            ctx.DrawImage(frame, new Rect(offset, offset, content, content));
        }

        var target = new RenderTargetBitmap(canvasSize, canvasSize, 96, 96, PixelFormats.Pbgra32);
        target.Render(visual);

        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(target));
        Directory.CreateDirectory(Path.GetDirectoryName(outFile));
        using (var stream = File.Create(outFile))
        {
            encoder.Save(stream);
        }
    }
}
'@

$densities = @{
    'mdpi'    = 48
    'hdpi'    = 72
    'xhdpi'   = 96
    'xxhdpi'  = 144
    'xxxhdpi' = 192
}

foreach ($density in $densities.Keys) {
    $legacySize = $densities[$density]
    $adaptiveSize = [int]([math]::Round($legacySize * 108.0 / 48.0))

    [IconMaker]::Render($Source, "$ResDir\mipmap-$density\ic_launcher.png", $legacySize, 1.0)
    [IconMaker]::Render($Source, "$ResDir\mipmap-$density\ic_launcher_round.png", $legacySize, 1.0)
    [IconMaker]::Render($Source, "$ResDir\mipmap-$density\ic_launcher_foreground.png", $adaptiveSize, (72.0 / 108.0))
    Write-Output "$density -> legacy ${legacySize}px, adaptive ${adaptiveSize}px"
}
