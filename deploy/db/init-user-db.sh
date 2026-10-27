#!/bin/bash
# 按 db-accounts.yml 清单创建多个独立数据库及其专属业务账号（最小权限）。
# - 每个库对应一个业务账号，密码从 password_env 指定的【环境变量】读取，不硬编码。
# - 幂等：已存在的库/角色不会重复创建。
# - 建表由应用启动时 Spring sql.init 执行 classpath:schema.sql 完成，本脚本只做账号与授权。
# 由 postgres 镜像初始化时以超级管理员（POSTGRES_USER）身份执行。
set -euo pipefail

ACCOUNTS="/docker-entrypoint-initdb.d/db-accounts.yml"
ADMIN_DB="${POSTGRES_DB:-postgres}"

# 去除字符串首尾空白
trim() {
    local s="$1"
    s="${s#"${s%%[![:space:]]*}"}"
    s="${s%"${s##*[![:space:]]}"}"
    printf '%s' "$s"
}

create_account() {
    local db="$1" user="$2" pwd_env="$3" readonly="${4:-false}"
    local pwd="${!pwd_env:-}"
    if [[ -z "$pwd" ]]; then
        echo "[skip] 环境变量 ${pwd_env} 未设置，跳过账号 ${user}@${db}" >&2
        return 0
    fi

    local exists
    # CREATE DATABASE / ROLE 不能在 PL/pgSQL 函数内执行，因此单独用存在性判断
    exists=$(psql -tAc "SELECT 1 FROM pg_database WHERE datname = '${db}'" --username "$POSTGRES_USER" --dbname "$ADMIN_DB")
    if [[ "$exists" != "1" ]]; then
        echo "[db] 创建数据库 ${db}"
        psql --username "$POSTGRES_USER" --dbname "$ADMIN_DB" -c "CREATE DATABASE \"${db}\""
    else
        echo "[db] 数据库已存在，跳过：${db}"
    fi

    exists=$(psql -tAc "SELECT 1 FROM pg_roles WHERE rolname = '${user}'" --username "$POSTGRES_USER" --dbname "$ADMIN_DB")
    if [[ "$exists" != "1" ]]; then
        echo "[role] 创建账号 ${user}"
        psql --username "$POSTGRES_USER" --dbname "$ADMIN_DB" -c "CREATE ROLE \"${user}\" LOGIN PASSWORD '${pwd}'"
    else
        echo "[role] 账号已存在，跳过：${user}"
    fi

    psql --username "$POSTGRES_USER" --dbname "$ADMIN_DB" -c "GRANT CONNECT ON DATABASE \"${db}\" TO \"${user}\""

    if [[ "${readonly}" == "true" ]]; then
        # 只读账号：仅连接与 use schema；表级 SELECT 需由迁移/应用对已建表另行授权
        psql --username "$POSTGRES_USER" --dbname "$db" -c "GRANT USAGE ON SCHEMA public TO \"${user}\""
    else
        # 读写账号：允许在 public schema 建表（Spring schema.sql 需要）与读写
        psql --username "$POSTGRES_USER" --dbname "$db" -c "GRANT USAGE, CREATE ON SCHEMA public TO \"${user}\""
    fi
    echo "[ok] ${user}@${db} 就绪 (readonly=${readonly})"
}

db=""; user=""; pwd_env=""; ro="false"
flush() {
    if [[ -n "$db" && -n "$user" && -n "$pwd_env" ]]; then
        create_account "$db" "$user" "$pwd_env" "$ro"
    fi
    db=""; user=""; pwd_env=""; ro="false"
}

while IFS= read -r line || [[ -n "$line" ]]; do
    trimmed="${line#"${line%%[![:space:]]*}"}"
    # 跳过空行与整行注释（顶格或缩进的 # 开头）
    [[ -z "$trimmed" || "$trimmed" == \#* ]] && continue

    if [[ "$trimmed" == "- name:"* ]]; then
        flush
        db="$(trim "${trimmed#- name:}")"
    else
        key="$(trim "${trimmed%%:*}")"
        val="${trimmed#*: }"
        val="${val%%\#*}"                 # 剥离行尾注释
        val="$(trim "$val")"
        case "$key" in
            app_user)     user="$val" ;;
            password_env) pwd_env="$val" ;;
            readonly)     ro="$val" ;;
        esac
    fi
done < "$ACCOUNTS"
flush

echo "[done] 数据库账号初始化完成"
