package com.strmr.tv.app

import com.strmr.tv.api.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** Main-thread PIN prompt state, scoped to the identity that requested it. */
internal class ScopedProfileUnlock(
    private val epoch: () -> Long,
    private val verifyRemote: suspend (String,String,Long,() -> Boolean) -> Boolean,
) {
    private class Pending(val id:String,val context:Long,val ui:AppSession.PinChallenge,val onCancelled:suspend()->Unit) {
        val reply=CompletableDeferred<Boolean>()
        var waiters=0
        var cancelHandled=false
    }
    val pinChallenge=MutableStateFlow<AppSession.PinChallenge?>(null)
    private var pending:Pending?=null
    private fun requireContext(expected:Long) {if(epoch()!=expected)throw CancellationException("PIN request context changed")}

    fun expire() {
        pending?.takeIf {it.context!=epoch()}?.let {close(it,false)}
    }
    private fun close(active:Pending,unlocked:Boolean) {
        if(pending===active){pending=null;pinChallenge.value=null}
        active.reply.complete(unlocked)
    }
    suspend fun unlock(id:String,locked:Profile?,expected:Long,onCancelled:suspend()->Unit):Boolean {
        requireContext(expected);expire()
        val active=pending?.let {if(it.id!=id||it.context!=expected)return false else it}
            ?: locked?.let {Pending(id,expected,AppSession.PinChallenge(it),onCancelled).also {pending=it;pinChallenge.value=it.ui}}
            ?: return false
        active.waiters++
        return try {
            val unlocked=active.reply.await();requireContext(active.context)
            if(!unlocked&&!active.cancelHandled){active.cancelHandled=true;active.onCancelled()}
            unlocked
        } finally {
            active.waiters--
            if(active.waiters==0&&pending===active&&!active.reply.isCompleted)close(active,false)
        }
    }
    suspend fun verify(pin:String,expected:AppSession.PinChallenge?):PinVerification {
        expire()
        val active=pending?:return PinVerification.Wrong
        if(active.ui!==expected)throw CancellationException("PIN prompt changed")
        val valid=try {
            verifyRemote(pin,active.id,active.context) { pending===active && active.context==epoch() }
        }catch(error:CancellationException){expire();throw error
        }catch(error:Exception){
            requireContext(active.context)
            if(pending!==active)throw CancellationException("PIN prompt changed")
            return PinVerification.Failed(error.message?:"Couldn't check the PIN.")
        }
        requireContext(active.context)
        if(pending!==active)throw CancellationException("PIN prompt changed")
        if(!valid)return PinVerification.Wrong
        close(active,true)
        return PinVerification.Unlocked
    }
    fun cancel(expected:AppSession.PinChallenge?) {
        pending?.takeIf {it.ui===expected}?.let {close(it,false)}
    }
}
