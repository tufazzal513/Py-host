package com.localhost.py.pythonruntime

import java.util.concurrent.LinkedBlockingQueue

class InputProvider {
    private val queue = LinkedBlockingQueue<String>()

    fun submitInput(text: String) {
        queue.put(text)
    }

    fun requestInput(): String {
        return queue.take()
    }
}
