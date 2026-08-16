package com.resukisu.resukisu.data.webui

import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.domain.model.WebUiCommandResult
import com.resukisu.resukisu.domain.model.WebUiProcess
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.internal.UiThreadHandler
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.util.concurrent.CompletableFuture

class WebUiRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    fun execute(command: String, globalMnt: Boolean = true): WebUiCommandResult {
        val result = ksuCliRepository.withNewRootShell(globalMnt) {
            newJob().add(command).to(ArrayList(), ArrayList()).exec()
        }
        return WebUiCommandResult(
            code = result.code,
            stdout = result.out.joinToString("\n"),
            stderr = result.err.joinToString("\n"),
        )
    }

    fun spawn(command: String, globalMnt: Boolean = true): WebUiProcess =
        ShellWebUiProcess(ksuCliRepository.createRootShell(globalMnt), command)

    fun listModules(): String = ksuCliRepository.listModules()

    fun openFile(path: String) = runCatching {
        val file = SuFile(path).apply { shell = ksuCliRepository.createRootShell(true) }
        SuFileInputStream.open(file)
    }.getOrNull()

    private class ShellWebUiProcess(
        private val shell: Shell,
        private val command: String,
    ) : WebUiProcess {
        override fun start(
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onComplete: (WebUiCommandResult) -> Unit,
        ) {
            try {
                val stdout = object : CallbackList<String>(UiThreadHandler::runAndWait) {
                    override fun onAddElement(s: String) = onStdout(s)
                }
                val stderr = object : CallbackList<String>(UiThreadHandler::runAndWait) {
                    override fun onAddElement(s: String) = onStderr(s)
                }
                val future = shell.newJob().add(command).to(stdout, stderr).enqueue()
                CompletableFuture.supplyAsync { future.get() }
                    .thenAccept { result ->
                        onComplete(
                            WebUiCommandResult(
                                code = result.code,
                                stdout = result.out.joinToString("\n"),
                                stderr = result.err.joinToString("\n"),
                            )
                        )
                    }
                    .whenComplete { _, _ -> close() }
            } catch (error: Throwable) {
                onComplete(WebUiCommandResult(-1, "", error.message.orEmpty()))
                close()
            }
        }

        override fun close() {
            runCatching { shell.close() }
        }
    }
}
