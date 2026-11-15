package com.strmr.tv.live

import com.strmr.tv.library.ProfileDataContext
import kotlinx.coroutines.CancellationException

/** Filter changes and forced reloads invalidate old pages even after an A/B/A query cycle. */
internal class LivePageGate<Q>(private val epoch:()->Long = {ProfileDataContext.current}) {
    private val context=epoch()
    private var revision=0L
    private var active:Ticket<Q>?=null
    private var loadedQuery:Q?=null
    private var nextOffset=0
    class Ticket<Q> internal constructor(internal val query:Q,internal val revision:Long,internal val offset:Int)

    fun reset(){revision++;active=null;loadedQuery=null;nextOffset=0}
    fun begin(query:Q):Ticket<Q>? {
        if(epoch()!=context)throw CancellationException("Live profile context changed")
        if(active?.query==query)return null
        return Ticket(query,revision,if(loadedQuery==query)nextOffset else 0).also{active=it}
    }
    fun accepts(ticket:Ticket<Q>,query:Q):Boolean =
        epoch()==context&&active===ticket&&ticket.revision==revision&&ticket.query==query
    fun commitPage(ticket:Ticket<Q>,query:Q,returnedEntries:Int):Boolean {
        require(returnedEntries>=0)
        if(!accepts(ticket,query))return false
        loadedQuery=query
        nextOffset=ticket.offset+returnedEntries
        return true
    }
    fun finish(ticket:Ticket<Q>):Boolean {
        if(active!==ticket)return false
        active=null
        return true
    }
}
