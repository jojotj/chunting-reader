# 安装包

此目录用于本地存放构建好的 APK，**不纳入版本库**（`.gitignore` 已排除 `*.apk`）。

正式对外发布时，把 APK 上传到 GitHub 仓库的 Releases 页面，而不是提交到仓库里：

- 仓库页 → Releases → Draft a new release
- Tag 与版本号一致，例如 `v2.0.5`
- 附件名直接用版本号，例如 `2.0.5.apk`（本地文件名带改动说明，上传时改掉）
