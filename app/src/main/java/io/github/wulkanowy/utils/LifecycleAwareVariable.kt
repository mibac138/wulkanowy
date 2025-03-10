package io.github.wulkanowy.utils

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class LifecycleAwareVariable<T : Any> : ReadWriteProperty<Fragment, T>, DefaultLifecycleObserver {

    private var _value: T? = null

    override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
        thisRef.viewLifecycleOwner.lifecycle.removeObserver(this)
        _value = value
        thisRef.viewLifecycleOwner.lifecycle.addObserver(this)
    }

    override fun getValue(thisRef: Fragment, property: KProperty<*>) = _value
        ?: throw IllegalStateException("Trying to call an lifecycle-aware value outside of the view lifecycle, or the value has not been initialized")

    override fun onDestroy(owner: LifecycleOwner) {
        Handler(Looper.getMainLooper()).post {
            _value = null
        }
    }
}

class LifecycleAwareVariableActivity<T : Any> : ReadWriteProperty<AppCompatActivity, T>,
    DefaultLifecycleObserver {

    private var _value: T? = null

    override fun setValue(thisRef: AppCompatActivity, property: KProperty<*>, value: T) {
        thisRef.lifecycle.removeObserver(this)
        _value = value
        thisRef.lifecycle.addObserver(this)
    }

    override fun getValue(thisRef: AppCompatActivity, property: KProperty<*>) = _value
        ?: throw IllegalStateException("Trying to call an lifecycle-aware value outside of the view lifecycle, or the value has not been initialized")

    override fun onDestroy(owner: LifecycleOwner) {
        Handler(Looper.getMainLooper()).post {
            _value = null
        }
    }
}

@Suppress("unused")
fun <T : Any> Fragment.lifecycleAwareVariable() = LifecycleAwareVariable<T>()

fun <T : Any> lifecycleAwareVariable() = LifecycleAwareVariableActivity<T>()
