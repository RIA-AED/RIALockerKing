# RIALockerKing

[莉亚锁王](https://wiki.ria.red/wiki/%E5%BC%80%E9%94%81%E7%A0%B4%E7%AE%B1%E7%94%B3%E8%AF%B7) 自动撬锁版。

## 功能简介

1. 当玩家被封禁后，允许检测 BlockLocker 的被封禁玩家的牌子锁，并将其自动拆除。

## 配置文件

```yaml
# Bukkit 侧本地缓存时间，秒
# 不需要调整的过大，Velocity 侧还有一层缓存
cache-interval: 30
sign-break: "&a这个箱子锁的所有者被丢出去喂鳕鱼了，因此箱子锁已被自动拆除。"
```

## 缓存

Bukkit侧：默认缓存30秒，超时重新到 Velocity 更新数据  
Velocity侧：默认缓存1小时，当 LiteBans 更新数据时，该缓存自动更新

## 截图

<img width="314" alt="f13feb5978d0a25e2cf613559c4d6dd2" src="https://github.com/RIA-AED/RIALockerKing/assets/30802565/a11573b3-87e7-4b3c-9d87-1d41cb586c8a">
