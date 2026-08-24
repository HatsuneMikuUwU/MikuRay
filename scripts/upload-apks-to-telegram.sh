#!/usr/bin/env bash
set -euo pipefail

: "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN is required}"
: "${TELEGRAM_CHAT_ID:?TELEGRAM_CHAT_ID is required}"

APK_DIR="${APK_DIR:-MikuRay/app/build/outputs/apk/${BUILD_TYPE:-debug}}"
COMMIT_MESSAGE="${TELEGRAM_COMMIT_MESSAGE:-${TELEGRAM_CAPTION:-MikuRay APK build}}"
COMMIT_SHA="${TELEGRAM_COMMIT_SHA:-}"
COMMIT_URL="${TELEGRAM_COMMIT_URL:-}"
MAX_FILE_BYTES="${MAX_FILE_BYTES:-50000000}"

escape_html() {
  local value="$1"
  value="${value//&/&amp;}"
  value="${value//</\&lt;}"
  value="${value//>/\&gt;}"
  value="${value//\"/\&quot;}"
  printf '%s' "${value}"
}

escaped_commit_message="$(escape_html "${COMMIT_MESSAGE}")"
if [[ -n "${COMMIT_SHA}" ]]; then
  commit_label="${escaped_commit_message} (${COMMIT_SHA})"
  plain_commit_label="${COMMIT_MESSAGE} (${COMMIT_SHA})"
else
  commit_label="${escaped_commit_message}"
  plain_commit_label="${COMMIT_MESSAGE}"
fi
if [[ -n "${COMMIT_URL}" ]]; then
  CAPTION="<a href=\"${COMMIT_URL}\">${commit_label}</a>"
  PLAIN_CAPTION="${plain_commit_label}"$'\n'"${COMMIT_URL}"
else
  CAPTION="${commit_label}"
  PLAIN_CAPTION="${plain_commit_label}"
fi

shopt -s nullglob
apk_files=(
  "${APK_DIR}"/*arm64-v8a*.apk
  "${APK_DIR}"/*armeabi-v7a*.apk
)

if (( ${#apk_files[@]} == 0 )); then
  echo "No ARM APK files found in ${APK_DIR}."
  exit 1
fi

LAST_RESPONSE=""
LAST_CURL_EXIT=0

send_document() {
  local apk_file="$1"
  local caption="$2"
  local use_html="$3"
  local response
  local curl_exit
  local -a curl_args

  curl_args=(
    --fail-with-body
    --silent
    --show-error
    --retry 3
    --retry-delay 5
    --request POST
    "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendDocument"
    --form "chat_id=${TELEGRAM_CHAT_ID}"
    --form "document=@${apk_file}"
    --form-string "caption=${caption}"
    --form-string "disable_notification=false"
  )
  if [[ "${use_html}" == "html" ]]; then
    curl_args+=(--form-string "parse_mode=HTML")
  fi

  if response="$(curl "${curl_args[@]}" 2>&1)"; then
    curl_exit=0
  else
    curl_exit=$?
  fi
  LAST_RESPONSE="${response}"
  LAST_CURL_EXIT="${curl_exit}"

  if [[ "${curl_exit}" -eq 0 ]] && grep -Eq '"ok"[[:space:]]*:[[:space:]]*true' <<<"${response}"; then
    return 0
  fi
  return 1
}

is_html_error() {
  grep -Eiq "can't parse entities|unsupported start tag|unclosed tag" <<<"${LAST_RESPONSE}"
}

failure_exit_code() {
  if [[ "${LAST_CURL_EXIT}" -ne 0 ]]; then
    printf '%s' "${LAST_CURL_EXIT}"
  else
    printf '1'
  fi
}

for apk_file in "${apk_files[@]}"; do
  file_name="$(basename "${apk_file}")"
  file_size="$(stat -c '%s' "${apk_file}")"
  if (( file_size > MAX_FILE_BYTES )); then
    echo "Telegram upload skipped for ${file_name}: file size ${file_size} bytes exceeds ${MAX_FILE_BYTES}-byte limit." >&2
    exit 1
  fi

  echo "Uploading ${file_name} to Telegram as an individual document..."
  if ! send_document "${apk_file}" "${CAPTION}" html; then
    if is_html_error; then
      echo "HTML caption was rejected; retrying ${file_name} with a plain-text caption." >&2
      if ! send_document "${apk_file}" "${PLAIN_CAPTION}" plain; then
        echo "Telegram upload failed for ${file_name} (curl exit ${LAST_CURL_EXIT})." >&2
        echo "Telegram response: ${LAST_RESPONSE}" >&2
        exit "$(failure_exit_code)"
      fi
    else
      echo "Telegram upload failed for ${file_name} (curl exit ${LAST_CURL_EXIT})." >&2
      echo "Telegram response: ${LAST_RESPONSE}" >&2
      exit "$(failure_exit_code)"
    fi
  fi
done

echo "Uploaded ${#apk_files[@]} ARM APK file(s) to Telegram in separate messages."
