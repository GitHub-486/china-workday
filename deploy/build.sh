#!/bin/bash
set -e

# ============================== 颜色定义 ==============================
RED='\033[0;31m';  GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m';  MAGENTA='\033[0;35m'
BOLD='\033[1m';    DIM='\033[2m';     NC='\033[0m'

log_info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[ OK ]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[FAIL]${NC}  $*"; }

# ============================== 配置 ==============================
IMAGE_REGISTRY="${IMAGE_REGISTRY:-}"
TIMESTAMP_SUFFIX="$(date +_%Y%m%d_%H%M%S)"
IMAGE_TAG="$(grep -A1 '<artifactId>china-workday</artifactId>' ../pom.xml | tail -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')${TIMESTAMP_SUFFIX}"
IMAGE_NAME="${IMAGE_REGISTRY}china-workday:${IMAGE_TAG}"

# ============================== 构建函数 ==============================
build_image() {
    local dockerfile="$1" image="$2"
    local start=$(date +%s)

    echo -e "\n${BOLD}${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}${BLUE}  🏗  Building china-workday${NC}"
    echo -e "${BOLD}${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    log_info "镜像:        ${image}"
    log_info "Dockerfile:  ${dockerfile}"

    if docker build --no-cache -f "${dockerfile}" -t "${image}" ..; then
        local cost=$(( $(date +%s) - start ))
        local size
        size=$(docker image inspect "${image}" --format '{{.Size}}' 2>/dev/null \
               | awk '{printf "%.1f MB", $1/1024/1024}')
        log_ok "构建完成 | 耗时 ${cost}s | 大小 ${size:-N/A}"
    else
        log_error "构建失败  →  ${image}"
        return 1
    fi
}

# ============================== Banner ==============================
echo -e "${BOLD}${MAGENTA}"
echo "  ╔══════════════════════════════════════════════╗"
echo "  ║  🚀  china-workday Image Builder              ║"
echo "  ╚══════════════════════════════════════════════╝"
echo -e "${NC}"
echo -e "  ${DIM}Registry:${NC}  ${IMAGE_REGISTRY:-<none>}"
echo -e "  ${DIM}Version:${NC}   ${BOLD}${IMAGE_TAG}${NC}"
echo ""

# ============================== 构建 ==============================
TOTAL_START=$(date +%s)
build_image "Dockerfile" "${IMAGE_NAME}"
TOTAL_COST=$(( $(date +%s) - TOTAL_START ))

# ============================== 收尾 ==============================
echo ""
echo -e "${BOLD}${GREEN}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${GREEN}║              ✅  BUILD SUCCESSFUL                ║${NC}"
echo -e "${BOLD}${GREEN}╚══════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  ${DIM}耗时:${NC}   ${BOLD}${TOTAL_COST}s${NC}"
echo -e "  ${DIM}时间:${NC}   $(date '+%Y-%m-%d %H:%M:%S')"
echo -e "  ${DIM}产物:${NC}"
echo -e "    ${GREEN}✔${NC} ${IMAGE_NAME}"
echo ""
