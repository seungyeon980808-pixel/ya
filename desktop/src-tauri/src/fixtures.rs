use crate::models::{CacheEnvelope, Item, CACHE_SCHEMA_VERSION};

fn item(
    item_id: &str,
    status: &str,
    area: &str,
    kind: &str,
    title: &str,
    start_at: &str,
    end_at: &str,
    reminder_at: &str,
    transcript: &str,
    notes: &str,
    created_at: &str,
    approved_at: &str,
    version: u32,
) -> Item {
    Item {
        item_id: item_id.into(),
        status: status.into(),
        source_type: "LOCAL_FIXTURE".into(),
        area: area.into(),
        kind: kind.into(),
        title: title.into(),
        start_at: start_at.into(),
        end_at: end_at.into(),
        reminder_at: reminder_at.into(),
        transcript: transcript.into(),
        notes: notes.into(),
        created_at: created_at.into(),
        updated_at: created_at.into(),
        approved_at: approved_at.into(),
        created_by: "local-fixture".into(),
        approved_by: if approved_at.is_empty() { "" } else { "local-fixture" }.into(),
        version,
        calendar_enabled: false,
        calendar_id: String::new(),
        calendar_event_id: String::new(),
        deleted_at: String::new(),
        idempotency_key: format!("fixture-key-{item_id}"),
        sync_state: "LOCAL_FIXTURE".into(),
    }
}

pub fn fixture_cache() -> CacheEnvelope {
    CacheEnvelope {
        schema_version: CACHE_SCHEMA_VERSION,
        updated_at: "2030-04-11T08:30:00+09:00".into(),
        items: vec![
            item(
                "fixture-review-001", "PENDING", "SCHOOL", "SCHEDULE",
                "[가상] 연구실 안전 교육", "2030-04-18T14:00:00+09:00",
                "2030-04-18T15:00:00+09:00", "2030-04-18T13:50:00+09:00",
                "가상 음성 예시: 목요일 오후 두 시에 안전 교육 검토하기.",
                "제품 화면 확인용 인공 데이터입니다.", "2030-04-11T08:30:00+09:00", "", 1,
            ),
            item(
                "fixture-task-002", "PENDING", "PERSONAL", "TODO",
                "[가상] 우산 수선 맡기기", "2030-04-20T11:00:00+09:00", "",
                "2030-04-20T10:30:00+09:00",
                "가상 음성 예시: 토요일 오전에 우산 수선을 맡기기.",
                "실제 인물, 장소 또는 일정과 무관합니다.", "2030-04-10T18:05:00+09:00", "", 1,
            ),
            item(
                "fixture-approved-003", "APPROVED", "PERSONAL", "SCHEDULE",
                "[가상] 도서 반납", "2030-04-22T17:00:00+09:00",
                "2030-04-22T17:30:00+09:00", "2030-04-22T16:50:00+09:00",
                "가상 음성 예시: 월요일 오후 다섯 시에 도서 반납하기.",
                "실제 계정과 무관한 승인 상태 예시입니다.", "2030-04-09T09:15:00+09:00",
                "2030-04-09T09:16:00+09:00", 2,
            ),
        ],
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fixture_is_explicitly_local_and_has_three_items() {
        let cache = fixture_cache();
        assert_eq!(cache.items.len(), 3);
        assert!(cache.items.iter().all(|item| {
            item.source_type == "LOCAL_FIXTURE"
                && item.sync_state == "LOCAL_FIXTURE"
                && item.calendar_id.is_empty()
                && item.calendar_event_id.is_empty()
        }));
    }
}
