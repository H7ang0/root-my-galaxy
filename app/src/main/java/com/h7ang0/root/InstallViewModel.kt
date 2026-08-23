package com.h7ang0.root

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import kotlin.time.Duration.Companion.milliseconds

enum class InstallPhase {
    Checking,
    Ready,
    Downloading,
    Exploiting,
    LoadingKernelSu,
    Installed,
    Failed,
}

data class InstallUiState(
    val phase: InstallPhase = InstallPhase.Checking,
    val message: String = "",
    val probeOutput: String = "",
    val log: String = "",
) {
    val busy: Boolean
        get() = phase in setOf(
            InstallPhase.Checking,
            InstallPhase.Downloading,
            InstallPhase.Exploiting,
            InstallPhase.LoadingKernelSu,
        )

}

data class TargetCatalogUiState(
    val loading: Boolean = false,
    val profiles: List<TargetProfile> = emptyList(),
    val error: String? = null,
)

private data class CommandResult(val code: Int, val output: String)

class InstallViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repository = PayloadRepository(application)
    private val historyStore = InstallHistoryStore(application)
    private val donationManager = DonationManager(application)
    private val mutableState = MutableStateFlow(InstallUiState())
    private val mutableHistory = MutableStateFlow(historyStore.closeInterruptedRuns())
    private val mutableTargetCatalog = MutableStateFlow(TargetCatalogUiState())
    private var discoveryJob: Job? = null
    private var installJob: Job? = null
    private var activeHistoryEntry: InstallHistoryEntry? = null
    val state: StateFlow<InstallUiState> = mutableState.asStateFlow()
    val history: StateFlow<List<InstallHistoryEntry>> = mutableHistory.asStateFlow()
    val targetCatalog: StateFlow<TargetCatalogUiState> = mutableTargetCatalog.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (installJob?.isActive == true) return
        mutableHistory.value = historyStore.load()
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            val probe = NativeProbe.run()
            if (detectInstalled()) {
                mutableState.value = InstallUiState(
                    phase = InstallPhase.Installed,
                    message = app.getString(R.string.status_ksu_active),
                    probeOutput = probe,
                    log = probe,
                )
                return@launch
            }
            try {
                val profile = repository.resolveTarget(DeviceSnapshot.current(app))
                mutableState.value = InstallUiState(
                    phase = InstallPhase.Ready,
                    message = app.getString(R.string.status_not_installed),
                    probeOutput = probe,
                    log = "$probe\n${app.getString(R.string.log_profile, profile.profileId)}",
                )
            } catch (error: Throwable) {
                mutableState.value = InstallUiState(
                    phase = InstallPhase.Failed,
                    message = app.getString(R.string.status_support_failed),
                    probeOutput = probe,
                    log = "$probe\n[-] ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    fun loadTargetCatalog() {
        if (mutableTargetCatalog.value.loading) return
        viewModelScope.launch(Dispatchers.IO) {
            mutableTargetCatalog.value = TargetCatalogUiState(loading = true)
            mutableTargetCatalog.value = try {
                TargetCatalogUiState(
                    profiles = repository.loadTargets().sortedWith(
                        compareBy(
                            TargetProfile::displayName,
                            TargetProfile::profileId,
                        ),
                    ),
                )
            } catch (error: Throwable) {
                TargetCatalogUiState(error = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    fun deleteHistory(entry: InstallHistoryEntry) {
        if (entry.result == InstallRunResult.Running || activeHistoryEntry?.id == entry.id) return
        viewModelScope.launch(Dispatchers.IO) {
            if (historyStore.delete(entry.id)) {
                mutableHistory.value = mutableHistory.value.filterNot { it.id == entry.id }
            }
        }
    }

    fun install(profileId: String? = null) {
        if (installJob?.isActive == true || mutableState.value.phase == InstallPhase.Installed) return
        discoveryJob?.cancel()
        installJob = viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = InstallUiState(
                phase = InstallPhase.Checking,
                probeOutput = mutableState.value.probeOutput,
            )
            startHistory()
            try {
                if (shizukuEnabled()) {
                    appendLog(app.getString(R.string.log_shizuku_prepare))
                    if (!ShizukuController.isRunning() && !ShizukuController.pingUntilRunning()) {
                        error(app.getString(R.string.error_shizuku_unavailable))
                    }
                    if (!ShizukuController.isGranted() && !ShizukuController.requestPermission()) {
                        error(app.getString(R.string.error_shizuku_permission))
                    }
                    appendLog(app.getString(R.string.log_shizuku_permission))
                }
                setPhase(InstallPhase.Checking, app.getString(R.string.status_checking_profile))
                val profile = if (profileId == null) {
                    repository.resolveTarget(DeviceSnapshot.current(app))
                } else {
                    repository.resolveTarget(profileId)
                }
                appendLog(app.getString(R.string.log_profile, profile.profileId))
                appendLog("[+] bundle          : offline assets (SM-S9280 / S9280ZCS6DZF2)")
                updateHistoryProfile(profile.profileId)

                setPhase(InstallPhase.Downloading, app.getString(R.string.status_downloading_payload))
                val payloads = repository.download(profile) { appendLog("[*] $it") }
                appendLog(app.getString(R.string.log_download_verified))

                setPhase(InstallPhase.Exploiting, app.getString(R.string.status_exploit_running))
                executeExploit(payloads)

                setPhase(InstallPhase.LoadingKernelSu, app.getString(R.string.status_ksu_loading))
                installKernelSu(payloads)

                setPhase(InstallPhase.Installed, app.getString(R.string.status_ksu_active))
                appendLog(app.getString(R.string.log_install_complete))
                if (donationManager.recordSuccess()) {
                    appendLog(app.getString(R.string.log_success_milestone, donationManager.successCount))
                }
                finishHistory(InstallRunResult.Succeeded)
            } catch (error: Throwable) {
                appendLog("[-] ${error.message ?: error.javaClass.simpleName}")
                setPhase(InstallPhase.Failed, app.getString(R.string.status_install_failed))
                finishHistory(InstallRunResult.Failed)
            }
        }
    }

    private suspend fun executeExploit(payloads: VerifiedPayloads) {
        waitForPostBootQuietWindow()
        val stagedPayload = shizukuStage(payloads.exploit, SHIZUKU_PAYLOAD_PATH, "755")
        shizukuStage(payloads.rootHelper, SHIZUKU_ROOT_HELPER_PATH, "755")
        shizukuStage(payloads.kernelSu, SHIZUKU_KSUD_SELECTED_PATH, "755")
        publishEnvironmentDiagnostics()
        appendLog(app.getString(R.string.log_cleanup))
        val cleanup = ShizukuController.shell(
            "pkill -9 -f 'cve-2026-43499' 2>/dev/null; " +
            "pkill -9 -f 'cve43499' 2>/dev/null; " +
                "rm -f /data/local/tmp/temp_su.sock /data/local/tmp/ksud-s25u-kdp " +
                "/data/local/tmp/.ksud-stage; echo ok",
        )
        appendLog(app.getString(R.string.log_cleanup_done, cleanup.first))
        if (AppPreferences.autoScreenOff(app)) {
            ShizukuController.shell("input keyevent 26")
            appendLog(app.getString(R.string.log_screen_off))
        }
        val logPrefix = mutableState.value.log
        val process = ShizukuController.exec(
            arrayOf("/system/bin/sh", "-c", "true"),
            shizukuEnvironment(stagedPayload.absolutePath),
        )
        val captured = StringBuilder()
        val readLog: () -> String = { drainProcessOutput(process, captured) }

        try {
            val startedAt = SystemClock.elapsedRealtime()
            var lastRawLog = ""
            while (process.isAlive) {
                val rawLog = readLog()
                if (rawLog != lastRawLog) {
                    publishExploitLog(logPrefix, rawLog)
                    lastRawLog = rawLog
                }
                val now = SystemClock.elapsedRealtime()
                require(now - startedAt < EXPLOIT_TOTAL_MILLIS) {
                    app.getString(R.string.error_exploit_timeout)
                }
                delay(LOG_POLL_INTERVAL)
            }

            val exitCode = process.waitFor()
            val rawLog = readLog()
            publishExploitLog(logPrefix, rawLog)
            val earlyOutput = readProcessOutput(process).trim()
            require(exitCode == 0) {
                app.getString(
                    R.string.error_payload_exit,
                    exitCode,
                    earlyOutput.takeIf(String::isNotBlank)?.let { " ($it)" } ?: "",
                )
            }
            require(
                rawLog.contains("exploit completed") &&
                    (rawLog.contains("retval=0 socket=1") || rawLog.contains("done=1 root=1")),
            ) {
                app.getString(R.string.error_success_marker)
            }
        } finally {
            if (process.isAlive) {
                process.destroy()
                delay(500.milliseconds)
                if (process.isAlive) process.destroyForcibly()
            }
        }
        appendLog(app.getString(R.string.log_bootstrap_root))
    }

    private suspend fun waitForPostBootQuietWindow() {
        val uptimeSeconds = SystemClock.elapsedRealtime() / 1_000L
        val waitSeconds = POST_BOOT_QUIET_SECONDS - uptimeSeconds
        if (waitSeconds <= 0) return
        appendLog(app.getString(R.string.log_post_boot_wait, waitSeconds, uptimeSeconds))
        delay(waitSeconds * 1_000L)
    }

    private fun drainProcessOutput(process: Process, buffer: StringBuilder): String {
        return try {
            drainStream(process.inputStream, buffer)
            drainStream(process.errorStream, buffer)
            buffer.toString()
        } catch (_: Throwable) {
            buffer.toString()
        }
    }

    private fun drainStream(stream: InputStream, buffer: StringBuilder) {
        val data = ByteArray(4096)
        while (stream.available() > 0) {
            val count = stream.read(data)
            if (count <= 0) break
            buffer.append(String(data, 0, count, Charsets.UTF_8))
        }
    }

    private fun publishExploitLog(prefix: String, rawLog: String) {
        mutableState.value = mutableState.value.copy(
            log = listOf(prefix, stripAnsi(rawLog))
                .filter(String::isNotBlank)
                .joinToString("\n"),
        )
        updateHistoryLog()
    }

    private fun installKernelSu(payloads: VerifiedPayloads) {
        shizukuStage(payloads.rootHelper, SHIZUKU_ROOT_HELPER_PATH, "755")
        shizukuStage(payloads.kernelSu, SHIZUKU_KSUD_SELECTED_PATH, "755")
        val stageCommand =
            "/system/bin/cp $SHIZUKU_KSUD_SELECTED_PATH $SHIZUKU_KSUD_PATH && " +
                "/system/bin/cp $SHIZUKU_KSUD_SELECTED_PATH $SHIZUKU_KSUD_STAGE_PATH && " +
                "/system/bin/chmod 755 $SHIZUKU_KSUD_PATH $SHIZUKU_KSUD_STAGE_PATH"
        val stage = ShizukuController.shell(stageCommand)
        require(stage.first == 0) { app.getString(R.string.error_ksu_stage, stage.second) }
        appendLog(app.getString(R.string.log_ksu_staged))

        val lateLoad = runRootHelper("--late-load")
        require(lateLoad.code == 0) {
            app.getString(R.string.error_ksu_verify, lateLoad.code, lateLoad.output)
        }
        if (lateLoad.output.isNotBlank()) appendLog(lateLoad.output)
        storeInstallReceipt()
        appendLog(app.getString(R.string.log_ksu_control_verified))
    }

    private fun detectInstalled(): Boolean {
        if (NativeProbe.isKernelSuActive()) return true
        val bootToken = currentBootToken() ?: return false
        val receipt = app.getSharedPreferences(INSTALL_RECEIPT, Application.MODE_PRIVATE)
        return receipt.getString(RECEIPT_BOOT_TOKEN, null) == bootToken &&
            receipt.getBoolean(RECEIPT_VERIFIED, false)
    }

    private fun storeInstallReceipt() {
        val bootToken = currentBootToken() ?: error(app.getString(R.string.error_boot_id))
        val stored = app.getSharedPreferences(INSTALL_RECEIPT, Application.MODE_PRIVATE)
            .edit()
            .putString(RECEIPT_BOOT_TOKEN, bootToken)
            .putBoolean(RECEIPT_VERIFIED, true)
            .commit()
        require(stored) { app.getString(R.string.error_receipt) }
    }

    private fun currentBootToken(): String? = runCatching {
        File("/proc/sys/kernel/random/boot_id")
            .readText(Charsets.US_ASCII)
            .trim()
            .takeIf(String::isNotBlank)
    }.getOrNull()

    private fun shizukuEnabled(): Boolean = true

    private fun shizukuStage(source: File, target: String, mode: String): File {
        val staged = File(target)
        if (staged.exists() && staged.length() == source.length()) return staged
        try {
            ShizukuController.writeFile(target, mode, source.inputStream())
        } catch (error: Throwable) {
            throw IllegalStateException(
                app.getString(R.string.error_shizuku_stage, target, error.message.orEmpty()),
                error,
            )
        }
        return staged
    }

    private fun shizukuEnvironment(payloadPath: String): Array<String> = buildList {
        add("EXPLOIT_ATTEMPTS=$EXPLOIT_ATTEMPTS")
        add("P0_ATTEMPT_TIMEOUT_SEC=45")
        add("EXPLOIT_ATTEMPT_TIMEOUT_SEC=120")
        add("CVE43499_ROOT_HELPER=$SHIZUKU_ROOT_HELPER_PATH")
        add("LD_PRELOAD=$payloadPath")
    }.toTypedArray()

    private fun readProcessOutput(process: Process): String {
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        return stdout + stderr
    }

    private fun runRootHelper(vararg arguments: String): CommandResult {
        val process = ShizukuController.exec(arrayOf(SHIZUKU_ROOT_HELPER_PATH) + arguments)
        val output = readProcessOutput(process)
        return CommandResult(process.waitFor(), stripAnsi(output.trim()))
    }

    private fun publishEnvironmentDiagnostics() {
        val pipeMax = ShizukuController.capture(
            arrayOf("/system/bin/sh", "-c", "cat /proc/sys/fs/pipe-max-size 2>&1"),
        ).trim()
        val pipeUser = ShizukuController.capture(
            arrayOf(
                "/system/bin/sh",
                "-c",
                "cat /proc/sys/fs/pipe-user-pages-soft 2>&1 || " +
                    "cat /proc/sys/fs/pipe-user-pages-hard 2>&1 || echo n/a",
            ),
        ).trim()
        val uname = ShizukuController.capture(
            arrayOf("/system/bin/sh", "-c", "cat /proc/version 2>&1 | head -c 200"),
        ).trim()
        appendLog(app.getString(R.string.log_diag, pipeMax.ifBlank { "?" }, pipeUser.ifBlank { "?" }))
        appendLog(app.getString(R.string.log_diag_version, uname.ifBlank { "?" }))
        val pipeMaxValue = pipeMax.toLongOrNull() ?: 0
        if (pipeMaxValue > 0 && pipeMaxValue < 131072) {
            appendLog(app.getString(R.string.log_pipe_warn, pipeMaxValue))
        }
    }

    private fun setPhase(phase: InstallPhase, message: String) {
        mutableState.value = mutableState.value.copy(phase = phase, message = message)
        appendLog("[*] $message")
    }

    private fun appendLog(line: String) {
        val cleanLine = stripAnsi(line).trim()
        if (cleanLine.isBlank()) return
        mutableState.value = mutableState.value.copy(
            log = (mutableState.value.log + "\n" + cleanLine).trim(),
        )
        updateHistoryLog()
    }

    private fun startHistory() {
        val entry = historyStore.create()
        activeHistoryEntry = entry
        publishHistory(entry)
    }

    private fun updateHistoryLog() {
        val entry = activeHistoryEntry ?: return
        val updated = entry.copy(log = mutableState.value.log)
        activeHistoryEntry = updated
        historyStore.save(updated)
        publishHistory(updated)
    }

    private fun updateHistoryProfile(profileId: String) {
        val entry = activeHistoryEntry ?: return
        val updated = entry.copy(profileId = profileId)
        activeHistoryEntry = updated
        historyStore.save(updated)
        publishHistory(updated)
    }

    private fun finishHistory(result: InstallRunResult) {
        val entry = activeHistoryEntry ?: return
        val completed = entry.copy(
            completedAtMillis = System.currentTimeMillis(),
            result = result,
            log = mutableState.value.log,
        )
        activeHistoryEntry = null
        historyStore.save(completed)
        publishHistory(completed)
    }

    private fun publishHistory(entry: InstallHistoryEntry) {
        mutableHistory.value = (mutableHistory.value.filterNot { it.id == entry.id } + entry)
            .sortedByDescending(InstallHistoryEntry::startedAtMillis)
    }

    companion object {
        private const val EXPLOIT_ATTEMPTS = "30"
        private const val EXPLOIT_TOTAL_MILLIS = 2_700_000L
        private const val POST_BOOT_QUIET_SECONDS = 300L
        private const val INSTALL_RECEIPT = "install_receipt"
        private const val RECEIPT_BOOT_TOKEN = "kernel_boot_id"
        private const val RECEIPT_VERIFIED = "verified"
        private const val SHIZUKU_PAYLOAD_PATH = "/data/local/tmp/cve-2026-43499"
        private const val SHIZUKU_ROOT_HELPER_PATH = "/data/local/tmp/cve-2026-43499-root"
        private const val SHIZUKU_KSUD_SELECTED_PATH = "/data/local/tmp/ksud-selected"
        private const val SHIZUKU_KSUD_PATH = "/data/local/tmp/ksud-s25u-kdp"
        private const val SHIZUKU_KSUD_STAGE_PATH = "/data/local/tmp/.ksud-stage"
        private val LOG_POLL_INTERVAL = 500.milliseconds
        private val ANSI_ESCAPE = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")

        private fun stripAnsi(value: String): String = ANSI_ESCAPE.replace(value, "").replace("\r", "")
    }
}
