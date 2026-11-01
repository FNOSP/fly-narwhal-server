#!/usr/bin/env bash
# 构建「飞鲸影视（开发版）」fnOS 安装包：应用名 FlyNarwhalDev，与正式版
# App.Native.flyNarwhalServer 可同时安装、互不干扰。
#
# 用法：
#   fnosAppCenter/build-fpk-dev.sh [--bin-dir DIR] [--authx FILE] [--out-dir DIR]
#                                  [--arch amd64|arm64] [--platform x86|arm]
#                                  [--fnpack FILE] [--skip-check]
#
# 服务端二进制必须是用 FN_PROFILE=dev 构建的 native 镜像（application-dev.yml 在
# 构建期解析，数据目录会固定到 /var/apps/FlyNarwhalDev/shares/data）。
# 打包本身复用 build-fpk.sh，只是固定 --package App.FlyNarwhalDev。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PACKAGE_DIR="${SCRIPT_DIR}/App.FlyNarwhalDev"

[ -d "${PACKAGE_DIR}" ] || { echo "package dir missing: ${PACKAGE_DIR}" >&2; exit 1; }

APPNAME="$(sed -n 's/^appname[[:space:]]*=[[:space:]]*//p' "${PACKAGE_DIR}/manifest" | head -n 1 | tr -d '[:space:]')"
[ "${APPNAME}" = "FlyNarwhalDev" ] || { echo "unexpected appname in dev package: '${APPNAME}'" >&2; exit 1; }

exec "${SCRIPT_DIR}/build-fpk.sh" --package "${PACKAGE_DIR}" "$@"
