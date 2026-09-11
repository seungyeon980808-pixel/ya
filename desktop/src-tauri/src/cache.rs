use crate::models::{CacheEnvelope, CacheLoadResult, CACHE_SCHEMA_VERSION};
use atomicwrites::{AllowOverwrite, AtomicFile};
use std::{
    fs,
    io::{self, Write},
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};

const CACHE_FILE_NAME: &str = "cache-v1.json";

fn cache_path(app_data_dir: &Path) -> PathBuf {
    app_data_dir.join(CACHE_FILE_NAME)
}

pub fn store_cache_at(app_data_dir: &Path, cache: &CacheEnvelope) -> io::Result<()> {
    if cache.schema_version != CACHE_SCHEMA_VERSION {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            format!("unsupported cache schema version: {}", cache.schema_version),
        ));
    }

    fs::create_dir_all(app_data_dir)?;
    let bytes = serde_json::to_vec_pretty(cache)
        .map_err(|error| io::Error::new(io::ErrorKind::InvalidData, error))?;
    AtomicFile::new(cache_path(app_data_dir), AllowOverwrite)
        .write(|file| {
            file.write_all(&bytes)?;
            file.sync_all()
        })
        .map_err(|error| io::Error::other(error.to_string()))
}

pub fn load_cache_at(app_data_dir: &Path) -> io::Result<CacheLoadResult> {
    let path = cache_path(app_data_dir);
    if !path.exists() {
        return Ok(CacheLoadResult {
            cache: CacheEnvelope::empty(),
            recovered_corrupt: false,
            warning: None,
        });
    }

    let bytes = fs::read(&path)?;
    match serde_json::from_slice::<CacheEnvelope>(&bytes) {
        Ok(cache) if cache.schema_version == CACHE_SCHEMA_VERSION => Ok(CacheLoadResult {
            cache,
            recovered_corrupt: false,
            warning: None,
        }),
        Ok(cache) => recover_corrupt(
            &path,
            format!("지원하지 않는 캐시 스키마 {}을 격리했습니다.", cache.schema_version),
        ),
        Err(_) => recover_corrupt(&path, "손상된 로컬 캐시를 격리하고 빈 캐시로 복구했습니다.".into()),
    }
}

fn recover_corrupt(path: &Path, warning: String) -> io::Result<CacheLoadResult> {
    let stamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis();
    let corrupt_path = path.with_file_name(format!("{CACHE_FILE_NAME}.corrupt-{stamp}"));
    fs::rename(path, corrupt_path)?;
    Ok(CacheLoadResult {
        cache: CacheEnvelope::empty(),
        recovered_corrupt: true,
        warning: Some(warning),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::fixtures::fixture_cache;

    #[test]
    fn cache_roundtrip_preserves_envelope() {
        let directory = tempfile::tempdir().expect("temporary directory");
        let expected = fixture_cache();
        store_cache_at(directory.path(), &expected).expect("store cache");
        let loaded = load_cache_at(directory.path()).expect("load cache");
        assert_eq!(loaded.cache, expected);
        assert!(!loaded.recovered_corrupt);
        assert!(loaded.warning.is_none());
    }

    #[test]
    fn corrupt_cache_is_quarantined_and_recovers_empty() {
        let directory = tempfile::tempdir().expect("temporary directory");
        fs::write(cache_path(directory.path()), b"not-json").expect("write corrupt cache");
        let loaded = load_cache_at(directory.path()).expect("recover cache");
        assert!(loaded.recovered_corrupt);
        assert!(loaded.cache.items.is_empty());
        assert!(loaded.warning.is_some());
        assert!(!cache_path(directory.path()).exists());
        assert_eq!(fs::read_dir(directory.path()).expect("list directory").count(), 1);
    }

    #[test]
    fn store_rejects_unknown_schema() {
        let directory = tempfile::tempdir().expect("temporary directory");
        let mut cache = fixture_cache();
        cache.schema_version = 99;
        assert_eq!(
            store_cache_at(directory.path(), &cache).expect_err("reject schema").kind(),
            io::ErrorKind::InvalidInput
        );
    }
}
