#!/usr/bin/env bash
set -euo pipefail

: "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN is required}"
: "${TELEGRAM_CHAT_ID:?TELEGRAM_CHAT_ID is required}"

APK_DIR="${APK_DIR:-MikuRay/app/build/outputs/apk/${BUILD_TYPE:-debug}}"
REQUESTED_COMMIT_COUNT="${TELEGRAM_COMMIT_COUNT:-1}"
COMMIT_SHAS="${TELEGRAM_COMMIT_SHAS:-}"
COMMIT_RANGE="${TELEGRAM_COMMIT_RANGE:-}"
REPOSITORY_URL="${TELEGRAM_REPOSITORY_URL:-}"
MAX_FILE_BYTES="${MAX_FILE_BYTES:-50000000}"
MAX_CAPTION_CHARS="${TELEGRAM_MAX_CAPTION_CHARS:-900}"
MAX_MESSAGE_CHARS="${TELEGRAM_MAX_MESSAGE_CHARS:-3800}"

escape_html() {
  local value="$1"
  value="${value//&/&amp;}"
  value="${value//</\&lt;}"
  value="${value//>/\&gt;}"
  value="${value//\"/\&quot;}"
  printf '%s' "${value}"
}

html_length() {
  local value="$1"
  printf '%s' "${#value}"
}

COMMIT_LINES_HTML=""
COMMIT_LINES_PLAIN=""
COMMIT_COUNT=0
COMMIT_SHA_MODE=false

declare -A SEEN_COMMIT_SHAS=()

append_commit_line() {
  local commit_sha="$1"
  local subject="$2"
  local short_sha
  local escaped_subject
  local commit_url

  short_sha="$(git rev-parse --short=7 "${commit_sha}")"
  escaped_subject="$(escape_html "${subject}")"
  commit_url="${REPOSITORY_URL%/}/commit/${commit_sha}"

  if [[ -n "${REPOSITORY_URL}" ]]; then
    COMMIT_LINES_HTML+="• <a href=\"${commit_url}\">${escaped_subject}</a> (${short_sha})"$'\n'
    COMMIT_LINES_PLAIN+="- ${subject} (${short_sha}) ${commit_url}"$'\n'
  else
    COMMIT_LINES_HTML+="• ${escaped_subject} (${short_sha})"$'\n'
    COMMIT_LINES_PLAIN+="- ${subject} (${short_sha})"$'\n'
  fi
  ((COMMIT_COUNT += 1))
}

if [[ -n "${COMMIT_SHAS}" ]]; then
  # Manual SHA input has priority over the automatic commit count.
  COMMIT_SHA_MODE=true
  while IFS= read -r selected_sha; do
    selected_sha="${selected_sha#"${selected_sha%%[![:space:]]*}"}"
    selected_sha="${selected_sha%"${selected_sha##*[![:space:]]}"}"
    [[ -z "${selected_sha}" ]] && continue

    if ! resolved_sha="$(git rev-parse --verify "${selected_sha}^{commit}" 2>/dev/null)"; then
      echo "Invalid commit SHA: ${selected_sha}" >&2
      exit 1
    fi
    if [[ -n "${SEEN_COMMIT_SHAS[${resolved_sha}]+x}" ]]; then
      continue
    fi
    SEEN_COMMIT_SHAS["${resolved_sha}"]=1
    append_commit_line "${resolved_sha}" "$(git log -1 --format='%s' "${resolved_sha}")"
  done < <(printf '%s\n' "${COMMIT_SHAS//,/$'\n'}")

  if (( COMMIT_COUNT == 0 )); then
    echo "No valid commit SHA was provided." >&2
    exit 1
  fi
else
  if ! [[ "${REQUESTED_COMMIT_COUNT}" =~ ^[1-9][0-9]*$ ]]; then
    echo "TELEGRAM_COMMIT_COUNT must be a positive integer; received '${REQUESTED_COMMIT_COUNT}'." >&2
    exit 1
  fi

  if [[ -n "${COMMIT_RANGE}" ]]; then
    if ! git rev-list --count "${COMMIT_RANGE}" >/dev/null 2>&1; then
      echo "Invalid commit range or commit '${COMMIT_RANGE}'." >&2
      exit 1
    fi
    COMMIT_REFS=("${COMMIT_RANGE}")
  else
    COMMIT_REFS=(HEAD)
  fi

  # Select only the requested number of newest commits, newest first.
  while IFS= read -r -d '' commit_sha && IFS= read -r -d '' subject; do
    append_commit_line "${commit_sha}" "${subject}"
  done < <(git log -z -n "${REQUESTED_COMMIT_COUNT}" --format='%H%x00%s' "${COMMIT_REFS[@]}")

  if (( COMMIT_COUNT == 0 )); then
    if [[ -n "${COMMIT_RANGE}" ]]; then
      echo "No new commits found in '${COMMIT_RANGE}'; Telegram upload skipped."
      exit 0
    fi
    echo "No commits found in the repository." >&2
    exit 1
  fi
fi

trim_trailing_newline() {
  local value="$1"
  printf '%s' "${value%$'\n'}"
}

COMMIT_LINES_HTML="$(trim_trailing_newline "${COMMIT_LINES_HTML}")"
COMMIT_LINES_PLAIN="$(trim_trailing_newline "${COMMIT_LINES_PLAIN}")"

HTML_CAPTION="${COMMIT_LINES_HTML}"
PLAIN_CAPTION="${COMMIT_LINES_PLAIN}"

SEND_COMMIT_SUMMARY=false
if (( $(html_length "${HTML_CAPTION}") > MAX_CAPTION_CHARS )); then
  SEND_COMMIT_SUMMARY=true
  CAPTION="MikuRay APK build"
  PLAIN_CAPTION="${CAPTION}"
else
  CAPTION="${HTML_CAPTION}"
  PLAIN_CAPTION="${PLAIN_CAPTION}"
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

send_message() {
  local text="$1"
  local use_html="$2"
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
    "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage"
    --form "chat_id=${TELEGRAM_CHAT_ID}"
    --form-string "text=${text}"
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

send_with_html_fallback() {
  local kind="$1"
  local html_text="$2"
  local plain_text="$3"

  if [[ "${kind}" == "document" ]]; then
    if send_document "${CURRENT_APK_FILE}" "${html_text}" html; then
      return 0
    fi
  else
    if send_message "${html_text}" html; then
      return 0
    fi
  fi

  if is_html_error; then
    echo "HTML ${kind} was rejected; retrying with a plain-text ${kind}." >&2
    if [[ "${kind}" == "document" ]]; then
      send_document "${CURRENT_APK_FILE}" "${plain_text}" plain
    else
      send_message "${plain_text}" plain
    fi
    return $?
  fi
  return 1
}

send_summary_chunks() {
  local html_text="$1"
  local plain_text="$2"
  local html_chunk=""
  local plain_chunk=""
  local html_candidate
  local plain_candidate
  local i
  local -a html_lines
  local -a plain_lines

  mapfile -t html_lines < <(printf '%s\n' "${html_text}")
  mapfile -t plain_lines < <(printf '%s\n' "${plain_text}")

  for i in "${!html_lines[@]}"; do
    if [[ -z "${html_chunk}" ]]; then
      html_candidate="${html_lines[i]}"
      plain_candidate="${plain_lines[i]}"
    else
      html_candidate="${html_chunk}"$'\n'"${html_lines[i]}"
      plain_candidate="${plain_chunk}"$'\n'"${plain_lines[i]}"
    fi

    if [[ -n "${html_chunk}" ]] && (( ${#html_candidate} > MAX_MESSAGE_CHARS )); then
      if ! send_with_html_fallback "message" "${html_chunk}" "${plain_chunk}"; then
        return 1
      fi
      html_chunk="${html_lines[i]}"
      plain_chunk="${plain_lines[i]}"
    else
      html_chunk="${html_candidate}"
      plain_chunk="${plain_candidate}"
    fi
  done

  if [[ -n "${html_chunk}" ]]; then
    send_with_html_fallback "message" "${html_chunk}" "${plain_chunk}"
  fi
}

shopt -s nullglob
apk_files=(
  "${APK_DIR}"/*arm64-v8a*.apk
  "${APK_DIR}"/*armeabi-v7a*.apk
)

if (( ${#apk_files[@]} == 0 )); then
  echo "No ARM APK files found in ${APK_DIR}."
  exit 1
fi

for apk_file in "${apk_files[@]}"; do
  file_name="$(basename "${apk_file}")"
  file_size="$(stat -c '%s' "${apk_file}")"
  if (( file_size > MAX_FILE_BYTES )); then
    echo "Telegram upload skipped for ${file_name}: file size ${file_size} bytes exceeds ${MAX_FILE_BYTES}-byte limit." >&2
    exit 1
  fi

  echo "Uploading ${file_name} to Telegram as an individual document..."
  CURRENT_APK_FILE="${apk_file}"
  if ! send_with_html_fallback "document" "${CAPTION}" "${PLAIN_CAPTION}"; then
    echo "Telegram upload failed for ${file_name} (curl exit ${LAST_CURL_EXIT})." >&2
    echo "Telegram response: ${LAST_RESPONSE}" >&2
    exit "$(failure_exit_code)"
  fi
done

if [[ "${SEND_COMMIT_SUMMARY}" == "true" ]]; then
  SUMMARY_HTML="${COMMIT_LINES_HTML}"
  SUMMARY_PLAIN="${COMMIT_LINES_PLAIN}"
  echo "Sending the complete commit list in Telegram message(s)..."
  if ! send_summary_chunks "${SUMMARY_HTML}" "${SUMMARY_PLAIN}"; then
    echo "Telegram commit summary failed (curl exit ${LAST_CURL_EXIT})." >&2
    echo "Telegram response: ${LAST_RESPONSE}" >&2
    exit "$(failure_exit_code)"
  fi
fi

echo "Uploaded ${#apk_files[@]} ARM APK file(s) to Telegram with ${COMMIT_COUNT} commit message(s)."
