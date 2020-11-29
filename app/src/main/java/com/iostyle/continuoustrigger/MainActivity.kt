package com.iostyle.continuoustrigger

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.iostyle.trigger.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private var trigger: ContinuousTrigger? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init()
    }

    private fun init() {
        /**
         * 链式调用写法
         */
//        trigger = ContinuousTrigger.Builder()
//            .with(
//                Trigger().apply {
//                    id = "test1"
//                    timeout = 2000
//                }
//            )
//            .with(
//                Trigger().apply {
//                    id = "test2"
//                    // 应用于dialog的阻塞模式
//                    chokeMode = true
//                }
//            )
//            .with(
//                Trigger().apply {
//                    id = "test3"
//                    timeout = 2000
//                }
//            )
//            .create()

        /**
         * DSL写法
         */
        val t0 = Trigger().apply {
            id = "test1"
            timeout = 7000
            chokeMode = true
        }
        val t1 = Trigger().apply {
            id = "test2"
            timeout = 7000
            // 应用于dialog的阻塞模式
            chokeMode = true
        }
        val t2 = Trigger().apply {
            id = "test3"
            timeout = 7000
            chokeMode = true
        }
        //name为可选参数 设置name后通过getTriggerInstance(name)获取实例
        trigger = (ContinuousTrigger.Builder("myTrigger") with t0 with t1 with t2).create()

        GlobalScope.launch {
            delay(8000)
            withContext(Dispatchers.Main) {
                trigger?.attach("test1", object : Trigger.Strike {
                    override fun strike() {
                        Log.e("llc_trigger", "test1")
                        AlertDialog.Builder(this@MainActivity).setMessage("test1")
                            .setOnDismissListener {
                                trigger?.next()
                            }.show()
                    }
                })
            }
        }

        //在任何位置可以根据名字获取实例
        getTriggerInstance("myTrigger")?.attach("test2", object : Trigger.Strike {
            override fun strike() {
                Log.e("llc_trigger", "test2")
                AlertDialog.Builder(this@MainActivity).setMessage("test2")
                    .setOnDismissListener {
                        trigger?.next()
                    }.show()
            }
        })

        GlobalScope.launch {
            delay(6000)
            withContext(Dispatchers.Main) {
                trigger?.attach("test3", object : Trigger.Strike {
                    override fun strike() {
                        Log.e("llc_trigger", "test3")
                        AlertDialog.Builder(this@MainActivity).setMessage("test3")
                            .setOnDismissListener {
                                removeTriggerInstance("myTrigger")
                            }.show()
                    }
                })
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        trigger?.clear()
        clearTriggers()
    }
}
