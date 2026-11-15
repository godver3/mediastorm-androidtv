package com.strmr.tv.live

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Review data from the production grouping model; no provider URLs are exported. */
internal object LiveStationReference {
    fun export() {
        val directory=System.getenv("MEDIASTORM_NATIVE_MODEL_ARTIFACTS")?.let(Path::of)?:return
        fun station(n:Int)="station:"+n.toString(16).padStart(24,'0')
        fun channel(id:String,name:String,source:String,group:Int)=LiveChannel(id,name,"http://127.0.0.1/synthetic/$id",sourceId=source,sourceName="Synthetic list ${source.last().uppercaseChar()}",stationId=station(group),stationName=if(group==1)"RTL" else "RTLZWEI",stationVariants=9,tvgCountry="DE",tvgLanguage="de")
        val first=listOf(channel("rtl-hd-a","RTL HD","source-a",1),channel("rtl-fhd-b","RTL FHD","source-b",1),channel("rtlzwei-a","RTLZWEI HD","source-a",2))
        val second=first+listOf(channel("rtl-sd-c","RTL SD","source-c",1),channel("rtlzwei-b","RTLZWEI FHD","source-b",2))
        val snapshots=buildJsonArray {
            for((page,entries)in listOf(first,second).withIndex())for(grouped in listOf(true,false))for(mask in 0..3){
                var collapsed=emptySet<String>();if(mask and 1 != 0)collapsed=LiveStationGroups.toggle(collapsed,station(1));if(mask and 2 != 0)collapsed=LiveStationGroups.toggle(collapsed,station(2))
                val rows=LiveStationGroups.rows(entries,grouped,collapsed)
                add(buildJsonObject {
                    put("page",page+1);put("grouped",grouped);put("mask",mask)
                    put("rows",buildJsonArray{for(row in rows)add(buildJsonObject{
                        put("key",row.key)
                        when(row){
                            is LiveStationGroups.Header->{put("kind","header");put("station",row.stationId);put("name",row.name);put("loadedSources",row.loadedSources);put("expanded",row.expanded);put("scopeHint",row.scopeHint)}
                            is LiveStationGroups.Channel->{put("kind","channel");put("id",row.channel.id);put("name",row.channel.name);put("sourceId",row.channel.sourceId.orEmpty());put("sourceName",row.channel.sourceName.orEmpty())}
                        }
                    })})
                })
            }
        }
        val result=buildJsonObject{put("production_kotlin_model",true);put("synthetic_only",true);put("native_ui_rendered",false);put("snapshots",snapshots)}
        Files.createDirectories(directory);Files.write(directory.resolve("station-group-model.json"),(result.toString()+"\n").toByteArray())
    }
}
