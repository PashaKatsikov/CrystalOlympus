<#
    af_simulate.ps1 — AppsFlyer non-organic attribution simulation / test helper.

    AppsFlyer never returns "non-organic" from a single server call: attribution
    is device-ID matching. The flow is:

        1) record a CLICK for the device advertising_id (GAID)  <- this script, -Click
        2) the device INSTALLS with the same GAID (SDK register, real device)
        3) read back attribution by AppsFlyer UID via GCD        <- this script, -Read

    Docs:
      - Attribution link (click):  https://support.appsflyer.com/hc/en-us/articles/4412938154513
      - GCD attribution testing:   https://gcdsdk.appsflyer.com/install_data/v4.0/{app_id}?devkey=..&device_id={AF_UID}

    Examples:
      # 1. Fire the non-organic click for a device GAID (opens 7d lookback window):
      pwsh tools/af_simulate.ps1 -Click -Gaid 5d8a8ccd-82b4-4821-a0eb-c9beace79dc0

      # 2. After reinstalling the app on that device, read attribution by its AppsFlyer UID:
      pwsh tools/af_simulate.ps1 -Read -Uid 1700000000000-1234567

      # 2b. Poll GCD until it reports non-organic (handy right after a reinstall):
      pwsh tools/af_simulate.ps1 -Read -Uid 1700000000000-1234567 -PollSeconds 90
#>

[CmdletBinding()]
param(
    [switch]$Click,
    [switch]$Read,

    # Device advertising id (GAID). Only used by -Click.
    [string]$Gaid = "5d8a8ccd-82b4-4821-a0eb-c9beace79dc0",

    # AppsFlyer UID of the install. Only used by -Read. Get it from the device
    # (logcat, or AttrHub.getAppsFlyerId()). NOT the GAID.
    [string]$Uid = "",

    # Kept aligned with gray.properties.
    [string]$AppId       = "com.crystalolympus.crystalolympusgame",
    [string]$DevKey      = "LQS26ZiQCQAPhi88WzpdP7",
    [string]$GcdBase     = "https://gcdsdk.appsflyer.com/install_data/v4.0/",

    # Click campaign shape (shows up verbatim in the attribution result).
    [string]$MediaSource = "crystal_sim_int",
    [string]$Campaign    = "nonorg_sim_campaign",
    [string]$CampaignId  = "sim0001",
    [string]$Lookback    = "7d",

    # If > 0, -Read keeps polling GCD until non-organic or the budget runs out.
    [int]$PollSeconds = 0
)

$ErrorActionPreference = "Stop"

function Invoke-Click {
    $clickId = "sim-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")
    $url =
        "https://app.appsflyer.com/$AppId" +
        "?pid=$MediaSource" +
        "&c=$Campaign" +
        "&af_campaign_id=$CampaignId" +
        "&advertising_id=$Gaid" +
        "&af_click_lookback=$Lookback" +
        "&clickid=$clickId"

    Write-Host "[click] recording non-organic engagement for GAID $Gaid" -ForegroundColor Cyan
    Write-Host "[click] $url"
    $code = & curl.exe -s -o NUL -w "%{http_code}" $url
    Write-Host "[click] HTTP $code (200 = accepted; landing page is cosmetic while the app is not on Play)" -ForegroundColor Green
    Write-Host "[click] lookback window open for $Lookback. Now (re)install the app on the device with this GAID."
}

function Invoke-Read {
    if ([string]::IsNullOrWhiteSpace($Uid)) {
        Write-Host "[read] -Uid is required (AppsFlyer UID, NOT the GAID)." -ForegroundColor Red
        Write-Host "[read] Grab it on the device from logcat or AttrHub.getAppsFlyerId()."
        return
    }

    $deadline = (Get-Date).AddSeconds([Math]::Max($PollSeconds, 0))
    do {
        $url = "$GcdBase$AppId`?devkey=$DevKey&device_id=$Uid"
        $body = & curl.exe -s $url
        Write-Host "[gcd] $body"

        if ($body -match '"af_status"\s*:\s*"Non-organic"') {
            Write-Host "[gcd] NON-ORGANIC confirmed." -ForegroundColor Green
            return
        }
        if ($body -match '"af_status"\s*:\s*"Organic"') {
            Write-Host "[gcd] organic (no matching click within lookback, or click not yet processed)." -ForegroundColor Yellow
        }

        if ((Get-Date) -lt $deadline) { Start-Sleep -Seconds 5 }
    } while ((Get-Date) -lt $deadline)
}

if ($Click) { Invoke-Click }
if ($Read)  { Invoke-Read }
if (-not $Click -and -not $Read) {
    Write-Host "Usage: -Click [-Gaid <gaid>]  |  -Read -Uid <appsflyer_uid> [-PollSeconds <n>]" -ForegroundColor Yellow
}
