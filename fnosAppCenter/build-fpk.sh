#!/usr/bin/env bash
# 把 fly-narwhal-server 的 native 构建产物打进飞牛 fnOS 的 .fpk 安装包。
#
# 用法：
#   fnosAppCenter/build-fpk.sh [--package DIR] [--arch amd64|arm64] [--platform x86|arm]
#                              [--bin-dir DIR] [--authx FILE] [--out-dir DIR]
#                              [--fnpack FILE] [--skip-check]
#
# 产物：<out-dir>/<appname>.<arch>.<version>.fpk（版本号取自 manifest）
# --package 指定其它应用目录（例如从生产包复制出的 -dev 包），默认 App.Native.flyNarwhalServer
# 开发版（应用名 FlyNarwhalDev，可与正式版同时安装）请直接用 build-fpk-dev.sh。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PKG_DIR="${SCRIPT_DIR}/App.Native.flyNarwhalServer"
TOOLS_DIR="${SCRIPT_DIR}/.tools"

FNPACK_VERSION="1.2.3"
FNPACK_BASE_URL="https://static2.fnnas.com/fnpack"

case "$(uname -s)" in
  Darwin) OS=darwin ;;
  Linux) OS=linux ;;
  *) echo "unsupported host OS: $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  arm64|aarch64) HOST_ARCH=arm64 ;;
  x86_64|amd64) HOST_ARCH=amd64 ;;
  *) echo "unsupported host arch: $(uname -m)" >&2; exit 1 ;;
esac

PACKAGE_DIR=""
ARCH="${HOST_ARCH}"
PLATFORM=""
BIN_DIR="${REPO_ROOT}/fly-narwhal-web/build/native/nativeCompile"
AUTHX=""
OUT_DIR="${REPO_ROOT}/dist/fpk"
FNPACK_BIN=""
SKIP_CHECK=false

while [ $# -gt 0 ]; do
  case "$1" in
    --package) PACKAGE_DIR="$2"; shift 2 ;;
    --arch) ARCH="$2"; shift 2 ;;
    --platform) PLATFORM="$2"; shift 2 ;;
    --bin-dir) BIN_DIR="$2"; shift 2 ;;
    --authx) AUTHX="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --fnpack) FNPACK_BIN="$2"; shift 2 ;;
    --skip-check) SKIP_CHECK=true; shift ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 1 ;;
  esac
done

case "${ARCH}" in
  amd64) [ -n "${PLATFORM}" ] || PLATFORM=x86 ;;
  arm64) [ -n "${PLATFORM}" ] || PLATFORM=arm ;;
  *) echo "--arch must be amd64 or arm64" >&2; exit 1 ;;
esac
case "${PLATFORM}" in
  x86) case "${ARCH}" in amd64) ;; *) echo "--platform x86 requires --arch amd64" >&2; exit 1 ;; esac ;;
  arm) case "${ARCH}" in arm64) ;; *) echo "--platform arm requires --arch arm64" >&2; exit 1 ;; esac ;;
  *) echo "--platform must be x86 or arm" >&2; exit 1 ;;
esac

if [ -n "${PACKAGE_DIR}" ]; then
  PKG_DIR="$(cd "${PACKAGE_DIR}" && pwd)"
fi
[ -f "${PKG_DIR}/manifest" ] || { echo "manifest not found in ${PKG_DIR}" >&2; exit 1; }
APPNAME="$(sed -n 's/^appname[[:space:]]*=[[:space:]]*//p' "${PKG_DIR}/manifest" | head -n 1 | tr -d '[:space:]')"
[ -n "${APPNAME}" ] || { echo "cannot read appname from manifest" >&2; exit 1; }

VERSION="$(sed -n 's/^version[[:space:]]*=[[:space:]]*//p' "${PKG_DIR}/manifest" | head -n 1 | tr -d '[:space:]')"
[ -n "${VERSION}" ] || { echo "cannot read version from manifest" >&2; exit 1; }

BIN="${BIN_DIR}/fly-narwhal-server"
if [ -z "${AUTHX}" ]; then
  AUTHX="${REPO_ROOT}/fly-narwhal-web/build/generated-resources/authx-verifier/native/authx/linux-${ARCH}/flynarwhal-authx"
fi

if [ "${SKIP_CHECK}" = false ]; then
  [ -x "${BIN}" ] || { echo "native binary missing or not executable: ${BIN} (run ./gradlew :fly-narwhal-web:linkNativeBinary first)" >&2; exit 1; }
  if [ "${OS}" = linux ]; then
    shopt -s nullglob
    sos=("${BIN_DIR}"/lib*.so)
    shopt -u nullglob
    [ ${#sos[@]} -gt 0 ] || { echo "no lib*.so next to ${BIN}; the native binary cannot run without them" >&2; exit 1; }
  fi
  [ -x "${AUTHX}" ] || { echo "authx verifier missing or not executable: ${AUTHX}" >&2; exit 1; }
fi

resolve_fnpack() {
  if [ -n "${FNPACK_BIN}" ]; then
    echo "${FNPACK_BIN}"
    return
  fi
  # 各平台二进制随仓库一起提供，构建机不需要联网下载。
  local vendored="${SCRIPT_DIR}/fnpack-${FNPACK_VERSION}-${OS}-${HOST_ARCH}"
  if [ -x "${vendored}" ]; then
    echo "${vendored}"
    return
  fi
  local cached="${TOOLS_DIR}/fnpack-${FNPACK_VERSION}-${OS}-${HOST_ARCH}"
  if [ ! -x "${cached}" ]; then
    local url="${FNPACK_BASE_URL}/fnpack-${FNPACK_VERSION}-${OS}-${HOST_ARCH}"
    if [ "${OS}" = linux ] && [ "${HOST_ARCH}" = arm64 ]; then
      # 文档列了这个文件名，但 static2 上一直没有真正的 arm64 二进制。
      # 仓库也不再放一份改名的旧版本顶替，需要时用 --fnpack 指定。
      echo "fnpack ${FNPACK_VERSION} for ${OS}-${HOST_ARCH} is not available (${url} returns 404)" >&2
      echo "pass --fnpack <file> to use another fnpack binary" >&2
      exit 1
    fi
    mkdir -p "${TOOLS_DIR}"
    echo "downloading fnpack ${FNPACK_VERSION}: ${url}" >&2
    curl -fsSL -o "${cached}" "${url}"
    chmod +x "${cached}"
  fi
  echo "${cached}"
}
FNPACK="$(resolve_fnpack)"

# 1. 同步构建产物到 app/server/（该目录不入库）。
# 注意：在临时副本里操作，打包失败也不会在仓库里留下半截产物。
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT
cp -a "${PKG_DIR}/." "${WORK_DIR}/"

SERVER_DIR="${WORK_DIR}/app/server"
rm -rf "${SERVER_DIR}"
mkdir -p "${SERVER_DIR}"
if [ "${SKIP_CHECK}" = false ]; then
  cp -p "${BIN}" "${SERVER_DIR}/fly-narwhal-server"
  cp -p "${AUTHX}" "${SERVER_DIR}/flynarwhal-authx"
  if compgen -G "${BIN_DIR}/fly-narwhal-server-*" >/dev/null; then
    cp -p "${BIN_DIR}"/fly-narwhal-server-* "${SERVER_DIR}/"
  fi
  if [ "${OS}" = linux ]; then
    cp -p "${BIN_DIR}"/lib*.so "${SERVER_DIR}/"
  fi
  chmod +x "${SERVER_DIR}/fly-narwhal-server" "${SERVER_DIR}/flynarwhal-authx"
else
  echo "WARN: --skip-check, packing without server binaries (package will not start)" >&2
fi

# 2. platform 只对本次打包生效，不改动仓库里的 manifest
sed -i.bak "s/^platform[[:space:]]*=.*/platform              = ${PLATFORM}/" "${WORK_DIR}/manifest"
rm -f "${WORK_DIR}/manifest.bak"

# 生命周期脚本和 CGI 必须可执行：fnOS 直接 fork/exec 它们，缺执行位会以
# "fork/exec .../cmd/install_init: permission denied" 回滚安装。
# 打包前统一补上，避免检出时丢了文件模式就打出装不上的包。
chmod +x "${WORK_DIR}"/cmd/* "${WORK_DIR}/app/ui/index.cgi"
for f in "${WORK_DIR}"/cmd/*; do
  [ -x "$f" ] || { echo "lifecycle script not executable: $f" >&2; exit 1; }
done

# 3. fnpack build
echo "fnpack: ${FNPACK}"
( cd "${WORK_DIR}" && "${FNPACK}" build )

FPK_IN="${WORK_DIR}/${APPNAME}.fpk"
[ -f "${FPK_IN}" ] || FPK_IN="$(find "${WORK_DIR}" -maxdepth 1 -name '*.fpk' | head -n 1)"
[ -f "${FPK_IN}" ] || { echo "fnpack did not produce an .fpk in ${WORK_DIR}" >&2; ls -la "${WORK_DIR}" >&2; exit 1; }

# 4. 按旧 CI 的命名规则落盘：App.Native.flyNarwhalServer.<arch>.<version dots->underscores>.fpk
mkdir -p "${OUT_DIR}"
OUT_FPK="${OUT_DIR}/${APPNAME}.${ARCH}.$(echo "${VERSION}" | tr '.' '_').fpk"
mv "${FPK_IN}" "${OUT_FPK}"

echo "built: ${OUT_FPK}"
ls -lh "${OUT_FPK}"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "${OUT_FPK}"
else
  shasum -a 256 "${OUT_FPK}"
fi
