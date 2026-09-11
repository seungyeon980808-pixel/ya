import type { ConnectionStatus, Item, ItemStatus } from "./domain";
import { WRITE_DISABLED_REASON } from "./domain";

type Filter = "ALL" | "PENDING" | "APPROVED";

const statusLabel: Record<ItemStatus, string> = {
  PENDING: "승인 대기",
  APPROVED: "승인됨",
  DONE: "완료",
  DELETED: "삭제됨"
};

function escapeHtml(value: unknown): string {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function displayDate(value: string): string {
  if (!value) return "시각 없음";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "시각 확인 필요";
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function shortDate(value: string): string {
  if (!value) return "기한 없음";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "확인 필요";
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function itemButton(item: Item, selectedId: string): string {
  const active = item.itemId === selectedId;
  return `<button class="item-row${active ? " is-selected" : ""}" type="button"
    data-item-id="${escapeHtml(item.itemId)}" aria-pressed="${active}">
    <span class="item-row__meta">
      <span class="state state--${item.status.toLowerCase()}">${statusLabel[item.status]}</span>
      <time>${shortDate(item.createdAt)}</time>
    </span>
    <strong>${escapeHtml(item.title || "제목 없음")}</strong>
    <span class="item-row__sub">${item.area === "SCHOOL" ? "학교" : "개인"} · ${item.kind === "TODO" ? "할 일" : "일정"} · ${shortDate(item.startAt)}</span>
  </button>`;
}

function detailPane(item: Item | undefined): string {
  if (!item) {
    return `<section class="detail empty-detail" aria-live="polite">
      <p>현재 조건에 맞는 항목이 없습니다.</p>
    </section>`;
  }
  const reason = escapeHtml(WRITE_DISABLED_REASON);
  return `<section class="detail" aria-labelledby="detail-title" data-testid="detail-pane">
    <header class="detail__head">
      <div>
        <p class="kicker">${escapeHtml(item.sourceType)} · ${shortDate(item.createdAt)}</p>
        <h2 id="detail-title">${escapeHtml(item.title)}</h2>
      </div>
      <div class="calendar-state" aria-label="Google Calendar 상태">
        <span>CALENDAR</span>
        <strong>미연결</strong>
      </div>
    </header>

    <div class="transcript">
      <span>가상 원문</span>
      <blockquote>${escapeHtml(item.transcript || "원문 없음")}</blockquote>
    </div>

    <div class="field-grid" aria-label="선택 항목 세부 정보">
      <label class="field field--wide">제목
        <input value="${escapeHtml(item.title)}" disabled title="${reason}" />
      </label>
      <label class="field">종류
        <select disabled title="${reason}"><option>${item.kind === "TODO" ? "할 일" : "일정"}</option></select>
      </label>
      <label class="field">구분
        <select disabled title="${reason}"><option>${item.area === "SCHOOL" ? "학교" : "개인"}</option></select>
      </label>
      <label class="field field--wide">예정 시각
        <input value="${escapeHtml(displayDate(item.startAt))}" disabled title="${reason}" />
      </label>
      <label class="field">알림
        <input value="${escapeHtml(displayDate(item.reminderAt))}" disabled title="${reason}" />
      </label>
      <label class="field">Google Calendar
        <select data-testid="calendar-control" disabled title="${reason}"><option>사용 안 함</option></select>
      </label>
      <label class="field field--full">메모
        <textarea disabled title="${reason}">${escapeHtml(item.notes)}</textarea>
      </label>
    </div>

    <dl class="review-strip">
      <div><dt>검토 상태</dt><dd>${statusLabel[item.status]}</dd></div>
      <div><dt>데이터</dt><dd>인공 픽스처</dd></div>
      <div><dt>동기화</dt><dd>로컬 전용</dd></div>
      <div><dt>버전</dt><dd>${item.version}</dd></div>
    </dl>

    <div class="write-notice" id="write-disabled-reason" role="status" data-testid="write-disabled-status">
      <strong>읽기 전용</strong> ${escapeHtml(WRITE_DISABLED_REASON)}
    </div>
    <footer class="actions">
      <button type="button" disabled aria-describedby="write-disabled-reason" title="${reason}">삭제</button>
      <button type="button" disabled aria-describedby="write-disabled-reason" title="${reason}">수정 저장</button>
      <button class="primary" type="button" disabled aria-describedby="write-disabled-reason" title="${reason}">확인 후 승인</button>
    </footer>
  </section>`;
}

export function createApp(root: HTMLElement, items: Item[], connection: ConnectionStatus): void {
  let filter: Filter = "ALL";
  let selectedId = items.find((item) => item.status !== "DELETED")?.itemId ?? "";

  const filtered = (): Item[] => items.filter((item) =>
    item.status !== "DELETED" && (filter === "ALL" || item.status === filter)
  );

  function render(): void {
    const visible = filtered();
    if (!visible.some((item) => item.itemId === selectedId)) selectedId = visible[0]?.itemId ?? "";
    const selected = items.find((item) => item.itemId === selectedId);
    const pending = items.filter((item) => item.status === "PENDING").length;
    const approved = items.filter((item) => item.status === "APPROVED").length;

    root.innerHTML = `<div class="app-shell">
      <aside class="sidebar">
        <div class="brand"><span>야</span><small>YA DESKTOP 0.1.0</small></div>
        <nav aria-label="주 메뉴">
          <button class="nav-button is-active" type="button" aria-current="page">승인 데스크</button>
          <button class="nav-button" type="button" disabled title="후속 버전에서 제공됩니다.">등록된 일정</button>
          <button class="nav-button" type="button" disabled title="후속 버전에서 제공됩니다.">알림 기록</button>
          <button class="nav-button" type="button" disabled title="후속 버전에서 제공됩니다.">설정</button>
        </nav>
        <div class="sidebar__status"><span class="status-dot" aria-hidden="true"></span><strong>${escapeHtml(connection.label)}</strong><small>네트워크 요청 없음</small></div>
      </aside>

      <main>
        <header class="topbar">
          <div><p class="kicker">VOICE APPROVAL DESK</p><h1>검토할 기록</h1></div>
          <dl class="summary" aria-label="항목 상태 요약">
            <div><dt>승인 대기</dt><dd data-testid="pending-count">${pending}</dd></div>
            <div><dt>승인됨</dt><dd>${approved}</dd></div>
            <div><dt>연결</dt><dd class="summary__connection">미구성</dd></div>
          </dl>
        </header>

        <section class="preview-banner" role="status" data-testid="preview-banner">
          <div><strong>로컬 미리보기</strong><span>Google 미연결</span></div>
          <p>${escapeHtml(connection.detail)}</p>
        </section>

        <div class="workspace">
          <section class="inbox" aria-label="항목 목록">
            <div class="inbox__head">
              <strong>항목 ${visible.length}</strong>
              <div class="filters" role="group" aria-label="상태 필터">
                <button type="button" data-filter="ALL" aria-pressed="${filter === "ALL"}">전체</button>
                <button type="button" data-filter="PENDING" aria-pressed="${filter === "PENDING"}">대기</button>
                <button type="button" data-filter="APPROVED" aria-pressed="${filter === "APPROVED"}">승인</button>
              </div>
            </div>
            <div class="item-list" data-testid="item-list">
              ${visible.length ? visible.map((item) => itemButton(item, selectedId)).join("") : '<p class="empty-list">표시할 항목이 없습니다.</p>'}
            </div>
          </section>
          ${detailPane(selected)}
        </div>
      </main>
    </div>`;

    root.querySelectorAll<HTMLButtonElement>("[data-filter]").forEach((button) => {
      button.addEventListener("click", () => {
        filter = button.dataset.filter as Filter;
        render();
        root.querySelector<HTMLButtonElement>(`[data-filter="${filter}"]`)?.focus();
      });
    });
    root.querySelectorAll<HTMLButtonElement>("[data-item-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedId = button.dataset.itemId ?? "";
        render();
        Array.from(root.querySelectorAll<HTMLButtonElement>("[data-item-id]"))
          .find((candidate) => candidate.dataset.itemId === selectedId)?.focus();
      });
    });
  }

  render();
}
