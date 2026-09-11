import type { CacheEnvelope, ConnectionStatus, Item } from "./domain";

const base: Pick<Item, "sourceType" | "createdBy" | "approvedBy" | "calendarId" | "calendarEventId" | "deletedAt" | "syncState"> = {
  sourceType: "LOCAL_FIXTURE",
  createdBy: "local-fixture",
  approvedBy: "",
  calendarId: "",
  calendarEventId: "",
  deletedAt: "",
  syncState: "LOCAL_FIXTURE"
};

/** Artificial records only. Dates and wording are intentionally fictional. */
export const fixtureItems: Item[] = [
  {
    ...base,
    itemId: "fixture-review-001",
    status: "PENDING",
    area: "SCHOOL",
    kind: "SCHEDULE",
    title: "[가상] 연구실 안전 교육",
    startAt: "2030-04-18T14:00:00+09:00",
    endAt: "2030-04-18T15:00:00+09:00",
    reminderAt: "2030-04-18T13:50:00+09:00",
    transcript: "가상 음성 예시: 목요일 오후 두 시에 안전 교육 검토하기.",
    notes: "제품 화면 확인용 인공 데이터입니다.",
    createdAt: "2030-04-11T08:30:00+09:00",
    updatedAt: "2030-04-11T08:30:00+09:00",
    approvedAt: "",
    version: 1,
    calendarEnabled: false,
    idempotencyKey: "fixture-key-001"
  },
  {
    ...base,
    itemId: "fixture-task-002",
    status: "PENDING",
    area: "PERSONAL",
    kind: "TODO",
    title: "[가상] 우산 수선 맡기기",
    startAt: "2030-04-20T11:00:00+09:00",
    endAt: "",
    reminderAt: "2030-04-20T10:30:00+09:00",
    transcript: "가상 음성 예시: 토요일 오전에 우산 수선을 맡기기.",
    notes: "실제 인물, 장소 또는 일정과 무관합니다.",
    createdAt: "2030-04-10T18:05:00+09:00",
    updatedAt: "2030-04-10T18:05:00+09:00",
    approvedAt: "",
    version: 1,
    calendarEnabled: false,
    idempotencyKey: "fixture-key-002"
  },
  {
    ...base,
    itemId: "fixture-approved-003",
    status: "APPROVED",
    area: "PERSONAL",
    kind: "SCHEDULE",
    title: "[가상] 식물 물주기",
    startAt: "2030-04-21T09:00:00+09:00",
    endAt: "2030-04-21T09:15:00+09:00",
    reminderAt: "2030-04-21T09:00:00+09:00",
    transcript: "가상 음성 예시: 일요일 아침에 화분 물주기.",
    notes: "로컬 UI 상태 표현을 위한 승인 예시입니다.",
    createdAt: "2030-04-09T09:10:00+09:00",
    updatedAt: "2030-04-09T09:12:00+09:00",
    approvedAt: "2030-04-09T09:12:00+09:00",
    approvedBy: "local-fixture",
    version: 2,
    calendarEnabled: false,
    idempotencyKey: "fixture-key-003"
  }
];

export const fixtureCache: CacheEnvelope = {
  schemaVersion: 1,
  items: fixtureItems,
  updatedAt: "2030-04-11T08:30:00+09:00"
};

export const unconfiguredConnection: ConnectionStatus = {
  state: "unconfigured",
  label: "Google 미연결",
  detail: "OAuth 및 운영 Google 설정이 포함되지 않은 안전한 로컬 미리보기입니다.",
  configured: false,
  canWrite: false,
  lastSyncAt: null
};
