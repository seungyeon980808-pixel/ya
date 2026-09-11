use crate::{
    cache::{load_cache_at, store_cache_at},
    fixtures::fixture_cache,
    models::{CacheEnvelope, CacheLoadResult, ConnectionStatus},
};
use tauri::{AppHandle, Manager};

fn app_data_dir(app: &AppHandle) -> Result<std::path::PathBuf, String> {
    app.path()
        .app_data_dir()
        .map_err(|error| format!("앱 데이터 경로를 열 수 없습니다: {error}"))
}

#[tauri::command]
pub fn get_connection_status() -> ConnectionStatus {
    ConnectionStatus {
        state: "unconfigured",
        label: "Google 미연결",
        detail: "OAuth와 운영 Google 설정이 포함되지 않은 로컬 미리보기입니다.",
        configured: false,
        can_write: false,
        last_sync_at: None,
    }
}

#[tauri::command]
pub fn bootstrap_fixture() -> CacheEnvelope {
    fixture_cache()
}

#[tauri::command]
pub fn load_local_cache(app: AppHandle) -> Result<CacheLoadResult, String> {
    load_cache_at(&app_data_dir(&app)?)
        .map_err(|error| format!("로컬 캐시를 불러올 수 없습니다: {error}"))
}

#[tauri::command]
pub fn store_local_cache(app: AppHandle, cache: CacheEnvelope) -> Result<(), String> {
    store_cache_at(&app_data_dir(&app)?, &cache)
        .map_err(|error| format!("로컬 캐시를 저장할 수 없습니다: {error}"))
}
