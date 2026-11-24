# Keep VPN service, boot receiver, and AndroidViewModel constructor
-keep class com.example.vpn.SwiftTunnelVpnService { *; }
-keep class com.example.vpn.BootCompletedReceiver { *; }
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}
