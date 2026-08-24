import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.Random

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
}

fun loadKeystoreProperties(file: java.io.File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val index = line.indexOf('=')
            line.substring(0, index).trim() to line.substring(index + 1).trim()
        }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = loadKeystoreProperties(keystorePropertiesFile)

// ═════════════════════════════════════════════════════════════════════════════
//  GRAY PART — per-project fingerprint
//
//  Everything below turns one line of gray.properties (gray.seed) into the
//  complete set of identifiers, constants and cipher parameters this app is
//  built with. See .cursor/rules/kotlin_fingerprint.mdc.
// ═════════════════════════════════════════════════════════════════════════════

val grayFile = rootProject.file("gray.properties")
val gray = Properties().apply {
    if (grayFile.exists()) grayFile.inputStream().use { load(it) }
}
fun grayProp(key: String, fallback: String = ""): String =
    (gray.getProperty(key) ?: fallback).trim()

val graySeed        = grayProp("gray.seed", "CHANGE-ME-EVERY-PROJECT")
val grayBundleId    = grayProp("gray.bundleId", "com.crystalolympus.crystalolympusgame")
val grayAppLabel    = grayProp("gray.appLabel", "Crystal Olympus")
val grayVersionCode = grayProp("gray.versionCode", "1").toInt()
val grayVersionName = grayProp("gray.versionName", "1.0.0")

if (graySeed == "CHANGE-ME-EVERY-PROJECT") {
    logger.warn(
        "[gray] gray.properties is missing or gray.seed is the default. " +
        "The build will succeed, but every derived identifier is the template " +
        "default and MUST NOT be shipped. Copy gray.properties.example → " +
        "gray.properties and run `gradlew graySeed` for a fresh seed."
    )
}

// ─── Deterministic per-project RNG ──────────────────────────────────────────
fun grayEscape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")
fun bcStr(value: String): String = "\"" + grayEscape(value) + "\""

val seedHash: ByteArray = run {
    val md = MessageDigest.getInstance("SHA-256")
    md.update("gray-fingerprint".toByteArray(Charsets.UTF_8))
    md.update(0)
    md.update(graySeed.toByteArray(Charsets.UTF_8))
    var h = md.digest()
    repeat(4) { h = MessageDigest.getInstance("SHA-256").digest(h) }
    h
}
val seedLongA = (0..7).fold(0L)  { acc, i -> (acc shl 8) or (seedHash[i].toLong() and 0xFF) }
val seedLongB = (8..15).fold(0L) { acc, i -> (acc shl 8) or (seedHash[i].toLong() and 0xFF) }
val grayRng = Random(seedLongA xor seedLongB)

fun pick(range: IntRange): Int = grayRng.nextInt(range.last - range.first + 1) + range.first
fun pick(range: LongRange): Long =
    (grayRng.nextLong() and Long.MAX_VALUE) % (range.last - range.first + 1) + range.first
fun <T> pickOne(items: List<T>): T = items[grayRng.nextInt(items.size)]
fun pickToken(minLen: Int, maxLen: Int): String {
    val len = pick(minLen..maxLen)
    val chars = ('a'..'z') + ('0'..'9')
    return (1..len).joinToString("") { chars[grayRng.nextInt(chars.size)].toString() }
}

// ─── Derived: cipher parameters ────────────────────────────────────────────
val cipherSeedBytes: IntArray = IntArray(pick(24..40)) { grayRng.nextInt(256) }
val cipherMult: Int = pick(3..255) or 1
val cipherAdd:  Int = pick(0..255)

// A constant default is a shared default: every project that never set this
// shipped variant 1, so the decoder had the same shape portfolio-wide while
// only its parameters moved. The fallback is taken from seedHash rather than
// grayRng on purpose — drawing from grayRng here would shift every value
// picked after it, and those name the preference files an installed build is
// already reading.
val codecVariant: Int = grayProp("gray.codecVariant")
    .ifBlank { ((seedHash[16].toInt() and 0xFF) % 3 + 1).toString() }
    .toInt()
    .coerceIn(1, 3)

fun grayEncode(text: String): List<Int> {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val n = cipherSeedBytes.size
    return bytes.mapIndexed { i, raw ->
        val s = cipherSeedBytes[i % n] and 0xFF
        val mix = when (codecVariant) {
            1 -> (i * cipherMult + cipherAdd) and 0xFF
            2 -> ((i + 1) * cipherMult xor cipherAdd) and 0xFF
            else -> (((i * cipherMult) and 0xFF) + cipherAdd + (i shr 3)) and 0xFF
        }
        ((raw.toInt() and 0xFF) xor s xor mix) and 0xFF
    }
}

fun encodedArrayLiteral(text: String): String {
    if (text.isBlank()) return "new int[0]"
    val hex = grayEncode(text).joinToString(",") { "0x%02X".format(it) }
    return "new int[]{$hex}"
}

// ─── Derived: identifiers, constants, sentinels ────────────────────────────
val prefsFileName    = "s_" + pickToken(6, 10)
val securePrefsName  = "e_" + pickToken(6, 10)
val keyRunChannel    = pickToken(4, 8)
val keyDestUrl       = pickToken(4, 8)
val keyExpires       = pickToken(4, 8)
val keyPushCold      = pickToken(4, 8)
val keyNotifSkip     = pickToken(4, 8)
val keyNotifClosed   = pickToken(4, 8)
val keyFcm           = pickToken(4, 8)
val keyKbPortrait    = pickToken(4, 8)
val keyKbLandscape   = pickToken(4, 8)

val jsSafeAreaSentinel = "__" + pickToken(4, 8)
val jsKeyboardSentinel = "__" + pickToken(4, 8)
// A leading digit here is a valid window[...] key but an illegal JS identifier,
// so any dotted access to the bridge would be a parse error. Force a letter first.
val jsBridgeName       = pickToken(5, 9).let { t ->
    val head = if (t.first().isDigit()) ('a' + (t.first() - '0')) else t.first()
    (head + t.substring(1)).replaceFirstChar { it.uppercase() }
}

val fcmChannelId    = "ch_" + pickToken(6, 10)
val fcmChannelTitle = pickOne(listOf(
    "Promotions", "Bonuses", "Updates", "Offers",
    "Announcements", "Rewards", "Deals", "News"
))

// The one timing that is specified rather than drawn from a range: Skip has to
// bring the promo back after exactly three days. Written as the multiplication
// it is, because the seconds count spelled out is a round number no project
// could plausibly have arrived at independently, and there is nothing to gain
// from having it sitting in the source as well as in the compiled constant.
val pushSnoozeDays      = 3L
val pushSnoozeSeconds   = pushSnoozeDays * 24L * 60L * 60L
val organicGcdDelayMs   = pick(3_500L..7_500L)
val configTimeoutMs     = pick(11_000L..22_000L)
val attributionFirstMs  = pick(22_000L..38_000L)
val attributionReturnMs = pick(7_000L..14_000L)
val deepLinkWaitMs      = pick(3_500L..7_000L)
val gcdTimeoutMs        = pick(7_500L..14_000L)
val connectGraceMs      = pick(2_500L..5_000L)
val safeAreaDelayMs     = pick(500L..1_400L)
val heartbeatMs         = pick(3_000L..6_500L)
val redirectRetryMax    = pick(4..8)

val chromeMajor = pickOne(listOf(146, 147, 148, 149, 150))
val chromeBuild = pick(6900..7900)
val chromePatch = pick(40..250)

// ─── Derived: injected-script parameters ───────────────────────────────────
//
// New draws belong at the end of this block. Every `pick` advances one shared
// RNG, so inserting a call higher up rewrites the preference filenames and
// keys below it, and an installed build would no longer find its own state.
//
// The two injected scripts contain several intervals that nothing depends on:
// how soon after a history change the safe-area style is re-applied, how often
// it is re-checked, how long after focus the field is measured a second time.
// As literals they made the injected JS byte-for-byte identical across
// projects — and that JS is the one part of this app a page can read back.
val jsReapplyFastMs  = pick(60..120)
val jsReapplySlowMs  = pick(320..520)
val jsReapplyPollMs  = pick(2_000..3_200)
val jsFocusRecheckMs = pick(160..260)

// Which wrappers add a status-bar offset of their own is a property of the
// sites a given project actually sends users to, so it belongs in
// gray.properties. The default is what the previous hard-coded list was.
val jsSafeAreaSelectors = grayProp(
    "gray.safeAreaSelectors",
    ".gameview-mobile-header,.app-header"
)

android {
    namespace = "com.crystalolympus.crystalolympusgame"
    compileSdk = 35

    defaultConfig {
        applicationId = grayBundleId
        minSdk = 24
        targetSdk = 35
        versionCode = grayVersionCode
        versionName = grayVersionName
        resourceConfigurations += listOf("en")

        manifestPlaceholders["grayAppLabel"]      = grayAppLabel
        manifestPlaceholders["grayFcmChannelId"]  = fcmChannelId

        buildConfigField("String", "GRAY_BUNDLE_ID",    bcStr(grayBundleId))
        buildConfigField("String", "GRAY_APP_LABEL",    bcStr(grayAppLabel))
        buildConfigField("String", "GRAY_UA_TOKEN",     bcStr(grayProp("gray.uaAppToken", "App")))
        buildConfigField("boolean","GRAY_UA_APP_SUFFIX", (grayProp("gray.uaAppSuffix", "false") == "true").toString())

        buildConfigField("int[]",  "SEC_CFG_ENDPOINT",  encodedArrayLiteral(grayProp("gray.configEndpoint")))
        buildConfigField("int[]",  "SEC_AF_KEY",        encodedArrayLiteral(grayProp("gray.appsFlyerKey")))
        buildConfigField("int[]",  "SEC_FB_PROJECT",    encodedArrayLiteral(grayProp("gray.firebaseProject")))
        buildConfigField("int[]",  "SEC_GCD_BASE",      encodedArrayLiteral(grayProp("gray.gcdBase")))

        buildConfigField("int[]",  "CIPHER_SEED",       "new int[]{${cipherSeedBytes.joinToString(",") { "0x%02X".format(it) }}}")
        buildConfigField("int",    "CIPHER_MULT",       cipherMult.toString())
        buildConfigField("int",    "CIPHER_ADD",        cipherAdd.toString())
        buildConfigField("int",    "CIPHER_VARIANT",    codecVariant.toString())

        buildConfigField("String", "PREFS_PLAIN",       bcStr(prefsFileName))
        buildConfigField("String", "PREFS_SECURE",      bcStr(securePrefsName))
        buildConfigField("String", "K_RUN_CHANNEL",     bcStr(keyRunChannel))
        buildConfigField("String", "K_DEST_URL",        bcStr(keyDestUrl))
        buildConfigField("String", "K_EXPIRES",         bcStr(keyExpires))
        buildConfigField("String", "K_PUSH_COLD",       bcStr(keyPushCold))
        buildConfigField("String", "K_NOTIF_SKIP",      bcStr(keyNotifSkip))
        buildConfigField("String", "K_NOTIF_CLOSED",    bcStr(keyNotifClosed))
        buildConfigField("String", "K_FCM",             bcStr(keyFcm))
        buildConfigField("String", "K_KB_PORTRAIT",     bcStr(keyKbPortrait))
        buildConfigField("String", "K_KB_LANDSCAPE",    bcStr(keyKbLandscape))

        buildConfigField("String", "JS_SAFE_AREA_SENTINEL", bcStr(jsSafeAreaSentinel))
        buildConfigField("String", "JS_KEYBOARD_SENTINEL",  bcStr(jsKeyboardSentinel))
        buildConfigField("String", "JS_BRIDGE_NAME",        bcStr(jsBridgeName))
        buildConfigField("String", "JS_SAFE_AREA_SELECTORS", bcStr(jsSafeAreaSelectors))
        buildConfigField("int",    "JS_REAPPLY_FAST_MS",   jsReapplyFastMs.toString())
        buildConfigField("int",    "JS_REAPPLY_SLOW_MS",   jsReapplySlowMs.toString())
        buildConfigField("int",    "JS_REAPPLY_POLL_MS",   jsReapplyPollMs.toString())
        buildConfigField("int",    "JS_FOCUS_RECHECK_MS",  jsFocusRecheckMs.toString())

        buildConfigField("String", "FCM_CHANNEL_ID",    bcStr(fcmChannelId))
        buildConfigField("String", "FCM_CHANNEL_TITLE", bcStr(fcmChannelTitle))

        buildConfigField("long",   "PUSH_SNOOZE_SEC",        "${pushSnoozeSeconds}L")
        buildConfigField("long",   "ORGANIC_GCD_DELAY_MS",   "${organicGcdDelayMs}L")
        buildConfigField("long",   "CONFIG_TIMEOUT_MS",      "${configTimeoutMs}L")
        buildConfigField("long",   "ATTRIBUTION_FIRST_MS",   "${attributionFirstMs}L")
        buildConfigField("long",   "ATTRIBUTION_RETURN_MS",  "${attributionReturnMs}L")
        buildConfigField("long",   "DEEP_LINK_WAIT_MS",      "${deepLinkWaitMs}L")
        buildConfigField("long",   "GCD_TIMEOUT_MS",         "${gcdTimeoutMs}L")
        buildConfigField("long",   "CONNECT_GRACE_MS",       "${connectGraceMs}L")
        buildConfigField("long",   "SAFE_AREA_DELAY_MS",     "${safeAreaDelayMs}L")
        buildConfigField("long",   "HEARTBEAT_MS",           "${heartbeatMs}L")
        buildConfigField("int",    "REDIRECT_RETRY_MAX",     redirectRetryMax.toString())

        buildConfigField("int",    "UA_CHROME_MAJOR",  chromeMajor.toString())
        buildConfigField("int",    "UA_CHROME_BUILD",  chromeBuild.toString())
        buildConfigField("int",    "UA_CHROME_PATCH",  chromePatch.toString())

        buildConfigField("String", "ALLOWED_HOSTS",   bcStr(grayProp("gray.allowedHosts")))
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProperties["storeFile"]
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = keystoreProperties["storePassword"]
                keyAlias = keystoreProperties["keyAlias"]
                keyPassword = keystoreProperties["keyPassword"]
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "DEBUG_FORCE_URL", "\"\"")
        }
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
            buildConfigField("String", "DEBUG_FORCE_URL", bcStr(grayProp("gray.debugForceUrl")))
        }
    }

    androidResources {
        // Sprite atlases are decoded at runtime, they must stay byte-identical in the APK.
        noCompress += listOf("webp", "mp3")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)

    // ── Launch flow ──────────────────────────────────────────────────────────
    // Versioned through the catalogue like everything else. Coordinates pinned
    // inline here were how two projects ended up declaring one identical block,
    // and an exactly matching version set is a join on its own.
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)

    implementation(libs.appsflyer)
    implementation(libs.install.referrer)
}

// ─── graySeed task ──────────────────────────────────────────────────────────
tasks.register("graySeed") {
    group = "gray part"
    description = "Print a fresh cryptographic seed for gray.properties."
    notCompatibleWithConfigurationCache("prints a random seed; nothing to cache")
    doLast {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        println("gray.seed = $encoded")
    }
}

// ─── grayReport task ────────────────────────────────────────────────────────
tasks.register("grayReport") {
    group = "gray part"
    description = "Print the derived per-project fingerprint (do not commit output)."
    notCompatibleWithConfigurationCache("reads derived script values for reporting only")
    doLast {
        println("═══ gray fingerprint (seed=${graySeed.take(6)}…) ═══")
        println("bundleId       = $grayBundleId")
        println("prefs plain    = $prefsFileName")
        println("prefs secure   = $securePrefsName")
        println("run channel k  = $keyRunChannel")
        println("dest url k     = $keyDestUrl")
        println("fcm channel    = $fcmChannelId ($fcmChannelTitle)")
        println("js sentinels   = $jsSafeAreaSentinel / $jsKeyboardSentinel")
        println("js bridge      = $jsBridgeName")
        println("codec variant  = $codecVariant  mult=$cipherMult  add=$cipherAdd  seed bytes=${cipherSeedBytes.size}")
        println("timings ms     = cfg $configTimeoutMs / att1 $attributionFirstMs / attR $attributionReturnMs")
        println("js timings ms  = $jsReapplyFastMs / $jsReapplySlowMs / $jsReapplyPollMs / $jsFocusRecheckMs")
        println("push snooze s  = $pushSnoozeSeconds (${pushSnoozeDays}d)")
        println("redirect max   = $redirectRetryMax")
        println("chrome UA      = $chromeMajor.0.$chromeBuild.$chromePatch")
    }
}
