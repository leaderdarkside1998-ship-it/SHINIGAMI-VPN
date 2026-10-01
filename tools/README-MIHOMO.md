# Mihomo / Clash backend

This fork can use Mihomo as an alternative backend to Xray for Gaming mode. It does **not** run two TUN interfaces at once: Android keeps the existing VPN/TUN + HEV bridge, while Mihomo provides the local SOCKS5/UDP upstream.

Run `./tools/fetch-mihomo.sh` before building the APK. The script downloads the Android binaries from the official MetaCubeX release and places them in `V2rayNG/app/src/main/assets/mihomo/` for all four common Android ABIs.

The app's Gaming screen now has a `Gaming Core` selector. `Xray` remains the default. `Clash / Mihomo` is an alternative backend for supported VLESS, VMess, Trojan, Shadowsocks, and HTTP profiles.
