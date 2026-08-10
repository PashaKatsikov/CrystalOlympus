param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$OutFile,
    [int]$X, [int]$Y, [int]$W, [int]$H
)

Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName WindowsBase

Add-Type -ReferencedAssemblies PresentationCore, WindowsBase, System.Xaml -TypeDefinition @'
using System;
using System.IO;
using System.Windows;
using System.Windows.Media.Imaging;

public static class ImageCropper
{
    public static void Crop(string source, string outFile, int x, int y, int w, int h)
    {
        var decoder = BitmapDecoder.Create(new Uri(source), BitmapCreateOptions.None, BitmapCacheOption.OnLoad);
        BitmapSource frame = decoder.Frames[0];
        BitmapSource cropped = new CroppedBitmap(frame, new Int32Rect(x, y, w, h));

        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(cropped));
        using (var stream = File.Create(outFile))
        {
            encoder.Save(stream);
        }
    }
}
'@

[ImageCropper]::Crop($Source, $OutFile, $X, $Y, $W, $H)
Write-Output "Saved $OutFile"
