package expo.modules.irohbridge

/**
 * The iroh-bridge JNI surface, from the React Native app's Rust crate (libiroh_ios_bridge.so,
 * copied in by scripts/sync-vendor.sh). Its JNI symbols are bound to this class name, so the
 * package stays the Expo module's: the same build serves both apps without a Rust rebuild.
 * Every call blocks and returns a JSON string, `{"ok":true,…}` or `{"ok":false,"error":…}`.
 */
object IrohBridgeNative {
    init {
        System.loadLibrary("iroh_ios_bridge")
    }

    /** A connection code looked up on the mainline DHT: `{"invite":…}`. */
    external fun resolveCode(code: String): String

    /** Starts (or reuses) the loopback proxy for an invite: `{"url":"http://127.0.0.1:<port>/api"}`. */
    external fun startProxy(invite: String): String

    /** The network changed: iroh rescans its paths rather than waiting out its idle timeout. */
    external fun notifyNetworkChange(): String

    /** Whether the connection runs direct or through a relay. */
    external fun getPathDiagnostics(): String

    /** The bridge's own log lines since the last call, newline-separated (plain text). */
    external fun drainLogs(): String
}
