package com.scrmoods.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Updates straight from GitHub: every push builds a new APK into the "latest" release.
 * The app checks which build that is, downloads the APK and hands it to Android's installer.
 * The APK is signed with the same key every time, so it installs over this one and keeps all data.
 */
object Updater {
    private const val RELEASE_API = "https://api.github.com/repos/Scroup/scrmoods/releases/tags/latest"
    private const val APK_URL = "https://github.com/Scroup/scrmoods/releases/download/latest/scrmoods.apk"
    const val RELEASE_PAGE = "https://github.com/Scroup/scrmoods/releases/tag/latest"

    enum class Phase { IDLE, CHECKING, CHECKED, DOWNLOADING, READY, FAILED }

    var phase by mutableStateOf(Phase.IDLE)
        private set
    var latestBuild by mutableStateOf<Int?>(null)
        private set
    var progress by mutableStateOf(0) // percent while downloading
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())

    fun installedBuild(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (_: Exception) { 0L }

    private fun apkFile(context: Context) = File(File(context.cacheDir, "updates").apply { mkdirs() }, "scrmoods.apk")

    /** Asks GitHub which build the latest release is ("scrmoods build 45" → 45). */
    fun check() {
        if (phase == Phase.CHECKING || phase == Phase.DOWNLOADING) return // one request at a time
        phase = Phase.CHECKING
        error = null
        thread(name = "update-check") {
            try {
                val c = URL(RELEASE_API).openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.connectTimeout = 15000
                c.readTimeout = 15000
                val body = try {
                    if (c.responseCode != HttpURLConnection.HTTP_OK) throw IllegalStateException("HTTP ${c.responseCode}")
                    c.inputStream.bufferedReader().use { it.readText() }
                } finally { c.disconnect() }
                val n = Regex("\"name\"\\s*:\\s*\"scrmoods build (\\d+)\"").find(body)?.groupValues?.get(1)?.toIntOrNull()
                main.post { latestBuild = n; phase = Phase.CHECKED }
            } catch (e: Exception) {
                main.post { latestBuild = null; error = e.message; phase = Phase.FAILED }
            }
        }
    }

    /** Downloads the latest APK into the app's cache, then opens the installer. */
    fun download(context: Context) {
        // a second tap while downloading would start another thread writing the same file
        if (phase == Phase.DOWNLOADING) return
        val app = context.applicationContext
        phase = Phase.DOWNLOADING
        progress = 0
        error = null
        thread(name = "update-download") {
            try {
                val target = apkFile(app)
                val part = File(target.parentFile, "scrmoods.apk.part")
                val c = URL(APK_URL).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = true
                c.connectTimeout = 20000
                c.readTimeout = 30000
                // an error page (404, rate limit…) must not be saved as the APK
                if (c.responseCode != HttpURLConnection.HTTP_OK) throw IllegalStateException(tr("Download failed (HTTP {0}).", c.responseCode))
                val total = c.contentLengthLong
                try { c.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) { lastPct = pct; main.post { progress = pct } }
                            }
                        }
                    }
                } } finally { c.disconnect() }
                if (part.length() < 100_000 || (total > 0 && part.length() != total)) throw IllegalStateException(tr("The download looks incomplete."))
                target.delete()
                part.renameTo(target)
                // checked under its .apk name (older Android versions only parse files that end in .apk)
                if (!isOurUpdate(app, target)) { target.delete(); throw IllegalStateException(tr("The downloaded file isn't a scrmoods update.")) }
                main.post { phase = Phase.READY; install(app) }
            } catch (e: Exception) {
                main.post { error = e.message; phase = Phase.FAILED }
            }
        }
    }

    /**
     * Checks the downloaded APK before handing it to the installer: same package name and, on Android 9+, signed with
     * the same certificate as the installed app. Android's installer refuses a differently signed update anyway; this
     * catches a broken or wrong file early with a clear message instead of a confusing installer error.
     */
    private fun isOurUpdate(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val flags = android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
                if (archive.packageName != context.packageName) return false
                val installed = pm.getPackageInfo(context.packageName, flags)
                val mine = installed.signingInfo?.apkContentsSigners?.map { it.toByteArray().contentHashCode() }?.toSet().orEmpty()
                val theirs = archive.signingInfo?.apkContentsSigners?.map { it.toByteArray().contentHashCode() }?.toSet().orEmpty()
                // some Android versions don't fill signingInfo for archives; then the installer's own check decides
                theirs.isEmpty() || mine.isEmpty() || mine == theirs
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Opens Android's installer for the downloaded APK (first asking to allow installs from scrmoods, if needed). */
    fun install(context: Context) {
        val file = apkFile(context)
        if (!file.exists()) { download(context); return }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, tr("Allow scrmoods to install updates, then tap Install again."), Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Settings → App updates. */
@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val installed = Updater.installedBuild(context)
    SectionHeader(tr("App updates"))
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Text(tr("Installed: build {0}", installed), fontSize = 16.sp, color = Ink)
        val status = when (Updater.phase) {
            Updater.Phase.IDLE -> tr("New versions come from the app's GitHub page.")
            Updater.Phase.CHECKING -> tr("Checking…")
            Updater.Phase.CHECKED -> when (val latest = Updater.latestBuild) {
                null -> tr("Couldn't read the latest build number. You can still download it.")
                else -> if (latest > installed) tr("A new version is ready: build {0}.", latest) else tr("You have the latest version.")
            }
            Updater.Phase.DOWNLOADING -> tr("Downloading… {0}%", Updater.progress)
            Updater.Phase.READY -> tr("Downloaded. The installer should open; tap Update there.")
            Updater.Phase.FAILED -> tr("Something went wrong: {0}", Updater.error ?: "?")
        }
        Text(status, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSoft, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))

        val newer = Updater.phase == Updater.Phase.CHECKED && (Updater.latestBuild ?: Int.MAX_VALUE) > installed
        when {
            Updater.phase == Updater.Phase.DOWNLOADING || Updater.phase == Updater.Phase.CHECKING -> {}
            Updater.phase == Updater.Phase.READY -> Button(
                onClick = { Haptics.tap(); Updater.install(context) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("Install")) }
            newer || Updater.phase == Updater.Phase.FAILED -> Button(
                onClick = { Haptics.tap(); Updater.download(context) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("Download and install")) }
            else -> OutlinedButton(
                onClick = { Haptics.tap(); Updater.check() },
                border = BorderStroke(1.dp, Rule),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("Check for updates"), color = Accent) }
        }
        if (Updater.phase == Updater.Phase.CHECKED && !newer) {
            LinkButton(tr("Download it anyway"), Modifier.padding(top = 2.dp)) { Updater.download(context) }
        }
        LinkButton(tr("Open the release page"), Modifier.padding(top = 2.dp)) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.RELEASE_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
