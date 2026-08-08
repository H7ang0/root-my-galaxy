package com.h7ang0.root

import android.content.Context
import android.system.Os
import java.io.File
import java.io.FileOutputStream

data class VerifiedPayloads(
    val profile: TargetProfile,
    val exploit: File,
    val kernelSu: File,
)

/**
 * Offline payload repository.
 *
 * Nothing Galaxy Root ships a single verified payload for
 * SM-S9280 / S9280ZCS6DZF2. The support manifest and both artifacts are
 * bundled under assets/, so no network is required and no other device or
 * firmware profile can ever be selected.
 */
class PayloadRepository(private val context: Context) {
    fun loadTargets(): List<TargetProfile> {
        val manifestBytes = context.assets
            .open(MANIFEST_ASSET)
            .use { it.readBytes() }
        return SupportManifest.parse(manifestBytes).targets
    }

    fun resolveTarget(snapshot: DeviceSnapshot): TargetProfile = loadTargets()
        .firstOrNull { it.matches(snapshot) }
        ?: error(context.getString(R.string.repo_no_profile))

    fun resolveTarget(profileId: String): TargetProfile = loadTargets()
        .firstOrNull { it.profileId == profileId }
        ?: error(context.getString(R.string.repo_profile_missing, profileId))

    fun download(profile: TargetProfile, onProgress: (String) -> Unit): VerifiedPayloads {
        val directory = File(context.filesDir, "payloads/${profile.profileId}").apply { mkdirs() }
        val exploit = copyArtifact(
            profile.exploit,
            File(directory, "cve-2026-43499-app.so"),
            context.getString(R.string.artifact_exploit),
            onProgress,
        )
        val kernelSu = copyArtifact(
            profile.kernelSu,
            File(directory, "ksud-s9280-kdp"),
            context.getString(R.string.artifact_kernelsu),
            onProgress,
        )
        Os.chmod(exploit.absolutePath, 0b100100100)
        Os.chmod(kernelSu.absolutePath, 0b100100100)
        return VerifiedPayloads(profile, exploit, kernelSu)
    }

    private fun copyArtifact(
        artifact: RemoteArtifact,
        destination: File,
        label: String,
        onProgress: (String) -> Unit,
    ): File {
        onProgress(context.getString(R.string.repo_downloading, label))
        val assetPath = requireAssetPath(artifact.url)
        val temporary = File(destination.parentFile, "${destination.name}.part")
        context.assets.open(assetPath).use { input ->
            require(input.available().toLong() == artifact.size) {
                context.getString(R.string.repo_size_mismatch, label)
            }
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= artifact.size) {
                        context.getString(R.string.repo_size_exceeded, label)
                    }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        require(temporary.length() == artifact.size) {
            context.getString(R.string.repo_incomplete, label)
        }
        if (destination.exists()) destination.delete()
        require(temporary.renameTo(destination)) {
            context.getString(R.string.repo_finalize_failed, label)
        }
        onProgress(context.getString(R.string.repo_verified, label))
        return destination
    }

    private fun requireAssetPath(url: String): String {
        require(url.startsWith(ASSET_PREFIX)) { context.getString(R.string.repo_url_invalid) }
        val path = url.removePrefix(ASSET_PREFIX)
        require(!path.contains("..")) { context.getString(R.string.repo_url_invalid) }
        return "payloads/$path"
    }

    companion object {
        private const val MANIFEST_ASSET = "support/targets-v3.json"
        private const val ASSET_PREFIX = "asset://"
    }
}
