package com.localhost.py.data.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class GitManager {
    suspend fun cloneRepo(uri: String, dest: File): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            if (!dest.exists()) dest.mkdirs()
            Git.cloneRepository()
                .setURI(uri)
                .setDirectory(dest)
                .call().use {
                    Pair(true, "Cloned successfully.")
                }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Failed to clone repository.")
        }
    }

    suspend fun commitAndPush(projectDir: File, message: String, token: String): String = withContext(Dispatchers.IO) {
        try {
            val git = try {
                Git.open(projectDir)
            } catch (e: Exception) {
                Git.init().setDirectory(projectDir).call()
            }

            git.use { repo ->
                repo.add().addFilepattern(".").call()
                repo.commit().setMessage(message.ifBlank { "Update via PyMobile IDE" }).call()

                if (token.isNotBlank()) {
                    val creds = UsernamePasswordCredentialsProvider(token, "")
                    repo.push().setCredentialsProvider(creds).call()
                    "Successfully committed and pushed to remote."
                } else {
                    "Committed locally (No GitHub Token provided to push)."
                }
            }
        } catch (e: Exception) {
            e.message ?: "Git operation failed."
        }
    }

    suspend fun getStatus(projectDir: File): String = withContext(Dispatchers.IO) {
        try {
            val git = Git.open(projectDir)
            git.use { repo ->
                val status = repo.status().call()
                val sb = StringBuilder()
                sb.append("Branch: ${repo.repository.branch}\n")
                if (status.isClean) {
                    sb.append("Working tree clean. Nothing to commit.\n")
                } else {
                    if (status.modified.isNotEmpty()) sb.append("Modified: ${status.modified.joinToString()}\n")
                    if (status.untracked.isNotEmpty()) sb.append("Untracked: ${status.untracked.joinToString()}\n")
                    if (status.added.isNotEmpty()) sb.append("Added: ${status.added.joinToString()}\n")
                    if (status.removed.isNotEmpty()) sb.append("Removed: ${status.removed.joinToString()}\n")
                }
                sb.toString()
            }
        } catch (e: Exception) {
            "Not a git repository (or unable to read status)."
        }
    }
}
