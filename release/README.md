# 安装包

此目录用于本地存放构建好的 APK，**不纳入版本库**（`.gitignore` 已排除 `*.apk`）。

正式对外发布时，把 APK 上传到 GitHub 仓库的 Releases 页面，而不是提交到仓库里：

- 仓库页 → Releases → Draft a new release
- Tag 建议与版本号一致，例如 `v1.7.7`
- 附件上传 `chunting-reader_v1.7.7.apk`（本地文件名为 `纯听书_v1.7.7_分课进度.apk`，上传时改为 ASCII 名）

当前最新留存：`纯听书_v2.0.9_片头片尾跳过.apk`（versionCode 24），对应的线上附件名为 `2.0.9.apk`。
历史留存在本目录按版本号排列，2.0.7 及以前的中文名包同样只做本地留档。
