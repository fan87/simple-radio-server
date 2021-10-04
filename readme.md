# 簡單網路電台
本Repository主要供學習用途，不建議企業使用
## License
WTFPL，你他媽的想幹麻就幹麻公開許可證<br>
簡單來說就是你想對這個Repository幹麻就幹麻

## 使用
可以至config.json查看所有可更改項目<br>
電台會重複播放指定歌曲清單，而歌曲應該存在tracks/<電台Namespace>/ 裡面<br>
電台Namespace可在config.json修改<br>
預設有example電台<br>

## 歌曲格式
格式必須為FFmpeg支援的格式<br>
值得一提的是檔名:<br>
```
001_audio.mp3
002_audio.mp3
```
依照這個格式(  %index%_%random%.%ext%  )會依照index排好，否則依照作業系統給的回應排列