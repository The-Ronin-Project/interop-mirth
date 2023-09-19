package com.projectronin.interop.mirth.channels

import com.projectronin.interop.mirth.channels.client.AidboxTestData
import com.projectronin.interop.mirth.channels.client.MockEHRTestData
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher

class TestListener : TestWatcher, AfterTestExecutionCallback {
    override fun testFailed(context: ExtensionContext, cause: Throwable) {
        MockEHRTestData.purge()
        AidboxTestData.purge()
    }

    override fun afterTestExecution(context: ExtensionContext) {
        if (context.executionException.isPresent) {
            testFailed(context, context.executionException.get())
        }
    }
}
