# Mihomo / Clash backend

This fork can use Mihomo as an alternative backend to Xray for Gaming mode. It does **not** run two TUN interfaces at once: Android keeps the existing VPN/TUN + HEV bridge, while Mihomo provides the local SOCKS5/UDP upstream.

**Before building the APK run `./tools/fetch-mihomo.sh`.** It places `libmihomo.so` in `V2rayNG/app/libs/<abi>/` (arm64-v8a, armeabi-v7a, x86_64, x86). The binary has to be packaged as a native library so Android extracts it to `nativeLibraryDir`, the only place an app may execute it from on Android 10+.

If the binary is missing, HEV TUN is off, the profile type is unsupported, or Mihomo fails to start, the app logs the reason and automatically falls back to Xray, so servers keep working.

The Gaming screen has a `Gaming Core` selector. `Xray` is the default. `Clash / Mihomo` supports VLESS, VMess, Trojan, Shadowsocks and HTTP profiles.
