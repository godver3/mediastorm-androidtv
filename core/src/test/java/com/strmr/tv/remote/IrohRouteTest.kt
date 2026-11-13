package com.strmr.tv.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class IrohRouteTest {
    @Test fun direct() = assertEquals(IrohRoute.Direct, IrohRoute.parse("""{"connectionGeneration":1,"selectedPath":"direct","remoteAddress":"192.0.2.10:443"}"""))
    @Test fun relay() = assertEquals(IrohRoute.Relay, IrohRoute.parse("""{"selectedPath":"relay"}"""))
    @Test fun unknown() = assertEquals(IrohRoute.Detecting, IrohRoute.parse("""{"selectedPath":"unknown"}"""))
    @Test fun noEnvelopeNeeded() = assertEquals(IrohRoute.Direct, IrohRoute.parse("""{"selectedPath":"direct","reconnects":0}"""))
    @Test fun bridgeError() = assertEquals(IrohRoute.Detecting, IrohRoute.parse("""{"ok":false,"error":"panicked"}"""))
    @Test fun garbage() = assertEquals(IrohRoute.Detecting, IrohRoute.parse("not json"))
}
