package me.ipromptnest.cleanlink

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把下载好的 APK 交给系统安装器。
 *
 * 为什么不用 PackageInstaller 的 session：在部分国产 ROM 上，session 提交后
 * 那个「待用户确认」的广播会被拦掉，装到一半没有任何界面，用户以为坏了。
 * 走 ACTION_VIEW 交给系统安装器，界面由系统自己负责，所有 ROM 表现一致。
 *
 * 代价是拿不到安装结果 —— 但装完进程就被替换了，本来也没地方汇报。
 */
object ApkInstall {

    private const val MIME_APK = "application/vnd.android.package-archive"

    /** 用户是否已允许本应用安装未知来源的应用。 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** 拉起系统的「安装未知应用」授权页，返回后由 Activity 自己决定是否继续。 */
    fun openPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun launch(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
