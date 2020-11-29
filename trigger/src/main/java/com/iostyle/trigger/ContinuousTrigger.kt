package com.iostyle.trigger

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 多线程连续触发器
 */
class ContinuousTrigger {

    private var triggerList: ConcurrentLinkedQueue<Trigger>? = null
    private var currentJob: Job? = null
    private var startTime: Long = 0L
    private var blockNode: Trigger? = null
    private var debugMode = false

    constructor()

    constructor(triggerList: ConcurrentLinkedQueue<Trigger>?) {
        startTime = System.currentTimeMillis()
        this.triggerList = triggerList
    }


    class Builder {
        private var name: String? = null
        private var triggerList = ConcurrentLinkedQueue<Trigger>()

        constructor()
        constructor(name: String?) {
            this.name = name
        }

        @TriggerDSL
        infix fun with(trigger: Trigger): Builder {
            triggerList.offer(trigger)
            return this
        }

        fun create(): ContinuousTrigger {

            return ContinuousTrigger(triggerList).also {
                it.next()
                if (!name.isNullOrBlank()) saveTriggerInstance(name!!, it)
            }
        }
    }

    //按序注册
    @TriggerDSL
    infix fun register(trigger: Trigger): ContinuousTrigger {
        if (debugMode) log("ContinuousTrigger register ${triggerList?.size}: ${trigger.id}")
        if (triggerList == null) triggerList = ConcurrentLinkedQueue<Trigger>()
        triggerList?.offer(trigger)
        if (blockNode == null) {
            if (debugMode) log("ContinuousTrigger autoStart")
            next()
        }
        return this
    }

    //绑定执行
    @Synchronized
    fun attach(id: String, strike: Trigger.Strike) {
        if (debugMode) log("ContinuousTrigger attach ${id}")

        //阻塞唤醒
        if (tryWakeUp(id, strike)) {
            if (debugMode) log("ContinuousTrigger blockNode wakeup")
        } else if (triggerList?.isEmpty() == false) {
            triggerList?.forEach {
                if (it.id == id) {
                    it.strike = strike
                    return
                }
            }
        }
    }

    private fun tryWakeUp(id: String, strike: Trigger.Strike): Boolean {
        if (isCurrentNode(id)) {
            currentJob?.cancel()
            strike.strike()
            if (blockNode?.chokeMode == false) {
                blockNode = null
                next()
            }
            return true
        }
        return false
    }

    private fun isCurrentNode(id: String): Boolean {
        return blockNode != null && blockNode!!.id == id
    }

    //下一步
    @Synchronized
    fun next() {
        if (currentJob != null) {
            currentJob!!.cancel()
            blockNode = triggerList?.poll()
        }
        if (blockNode == null) {
            blockNode = triggerList?.poll()
        }
        if (debugMode) {
            log("ContinuousTrigger next ${blockNode?.id}")
        }
        blockNode?.run {
            if (invalid) {
                if (debugMode) {
                    log("ContinuousTrigger invalid $id")
                }
                next()
                return
            }
            strike?.run {
                strike()
                blockNode = null
                if (!chokeMode) {
                    next()
                }
            } ?: (if (timeout > 0) {
                var delTime = System.currentTimeMillis() - startTime
                if (delTime > timeout) {
                    //超时
                    blockNode = null
                    if (!chokeMode) {
                        next()
                    }
                } else {
                    //未超时
                    currentJob = GlobalScope.launch {
                        delay(timeout - delTime)
                        if (debugMode) log("ContinuousTrigger timeout $id")
                        withContext(Dispatchers.Main) {
                            next()
                        }
                        Log.i("llc", "name = $id")
                    }
                }

            })
        } ?: clear()
    }

    fun cancel(id: String) {
        if (debugMode) log("ContinuousTrigger cancel $id")
        if (triggerList?.isEmpty() == false)
            kotlin.run looper@{
                triggerList?.forEach {
                    if (it.id == id) {
                        it.invalid = true
                        return@looper
                    }
                }
            }
        if (isCurrentNode(id)) {
            if (debugMode) log("ContinuousTrigger cancel next $id")
            next()
        }
    }

    //清空
    fun clear() {
        if (debugMode) log("ContinuousTrigger clear")
        blockNode = null
        currentJob?.cancel()
        triggerList?.clear()
    }

    fun log(content: String?) {
        content?.let {
            Log.e("Trigger", it)
        }
    }
}