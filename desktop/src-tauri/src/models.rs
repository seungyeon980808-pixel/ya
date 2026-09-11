use serde::{Deserialize, Serialize};

pub const CACHE_SCHEMA_VERSION: u32 = 1;

/// Mirrors the public web Items sheet contract exactly (23 columns).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Item {
    pub item_id: String,
    pub status: String,
    pub source_type: String,
    pub area: String,
    pub kind: String,
    pub title: String,
    pub start_at: String,
    pub end_at: String,
    pub reminder_at: String,
    pub transcript: String,
    pub notes: String,
    pub created_at: String,
    pub updated_at: String,
    pub approved_at: String,
    pub created_by: String,
    pub approved_by: String,
    pub version: u32,
    pub calendar_enabled: bool,
    pub calendar_id: String,
    pub calendar_event_id: String,
    pub deleted_at: String,
    pub idempotency_key: String,
    pub sync_state: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CacheEnvelope {
    pub schema_version: u32,
    pub items: Vec<Item>,
    pub updated_at: String,
}

impl CacheEnvelope {
    pub fn empty() -> Self {
        Self {
            schema_version: CACHE_SCHEMA_VERSION,
            items: Vec::new(),
            updated_at: "1970-01-01T00:00:00Z".to_string(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CacheLoadResult {
    pub cache: CacheEnvelope,
    pub recovered_corrupt: bool,
    pub warning: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ConnectionStatus {
    pub state: &'static str,
    pub label: &'static str,
    pub detail: &'static str,
    pub configured: bool,
    pub can_write: bool,
    pub last_sync_at: Option<String>,
}
