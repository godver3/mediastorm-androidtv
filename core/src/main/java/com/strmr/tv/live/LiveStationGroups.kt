package com.strmr.tv.live

/** Presentation uses server-issued station identities; names and stream URLs never establish a group. */
object LiveStationGroups {
    private val validStation=Regex("station:[0-9a-f]{24}")
    private const val MAX_COLLAPSED=128

    sealed interface Row {val key:String}
    data class Header(val stationId:String,val name:String,val loadedSources:Int,val expanded:Boolean,val scopeHint:String=""):Row {
        override val key get()="station-group:$stationId"
    }
    data class Channel(val channel:LiveChannel,val originalIndex:Int,val stationId:String?):Row {
        override val key get()=entryKey(channel)
    }
    private data class Member(val channel:LiveChannel,val index:Int,val station:String?)

    fun stationId(channel:LiveChannel):String? = channel.stationId?.trim()?.takeIf{validStation.matches(it)}
    fun entryKey(channel:LiveChannel):String {
        val source=channel.sourceId.orEmpty()
        return "live-channel:${source.length}:$source${channel.id.length}:${channel.id}"
    }
    fun rows(channels:List<LiveChannel>,enabled:Boolean,collapsed:Set<String> = emptySet()):List<Row> {
        val seen=mutableSetOf<String>()
        val members=channels.mapIndexedNotNull {index,channel->
            if(!seen.add(entryKey(channel)))null else Member(channel,index,stationId(channel))
        }
        if(!enabled)return members.map{Channel(it.channel,it.index,null)}
        val groups=linkedMapOf<String,MutableList<Member>>()
        for(member in members)groups.getOrPut(member.station?:entryKey(member.channel)){mutableListOf()}.add(member)
        return buildList {
            for(group in groups.values){
                val first=group.first();val id=first.station
                if(id==null||group.size<2){add(Channel(first.channel,first.index,id));continue}
                val expanded=id !in collapsed
                val name=group.firstNotNullOfOrNull{it.channel.stationName?.trim()?.takeIf(String::isNotEmpty)}?:first.channel.name
                val hints=group.flatMap{listOfNotNull(it.channel.tvgCountry,it.channel.tvgLanguage)}
                    .map{it.trim().take(48)}.filter{it.isNotEmpty()}.distinct().take(4).joinToString(" · ")
                add(Header(id,name,group.size,expanded,hints))
                for(member in if(expanded)group else group.take(1))add(Channel(member.channel,member.index,id))
            }
        }
    }
    fun toggle(collapsed:Set<String>,stationId:String):Set<String> {
        val result=LinkedHashSet(collapsed.filter{validStation.matches(it)})
        if(!validStation.matches(stationId))return result
        if(!result.remove(stationId)){
            while(result.size>=MAX_COLLAPSED)result.remove(result.first())
            result.add(stationId)
        }
        return result
    }
}
