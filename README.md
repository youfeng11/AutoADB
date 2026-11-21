# AutoADBXposed

一个简单的Xposed模块，让手机连接 USB 数据主机时询问是否开启 USB 调试。仅对本次连接有效，拔线后自动关闭。

## 要求

- Android 11+

- LSPosed 版本号 7759+

## 使用

安装 APK，在 LSPosed 中启用模块并勾选 **系统框架（`system`）**，重启后生效。模块启动时会关闭 USB 调试，连接后需在弹窗中允许。

## 许可证

[Apache License 2.0](LICENSE)
