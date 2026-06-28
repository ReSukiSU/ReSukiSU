use crate::{
    android::susfs::config::model::{CURRENT_VERSION, Config, VersionProbe},
    defs::SUSFS_CONFIG,
};
use anyhow::{Result, bail};
use std::{
    fs::{File, Permissions, create_dir_all, set_permissions},
    io::{BufReader, Seek, SeekFrom, Write},
    os::unix::fs::PermissionsExt,
    path::Path,
};
use tempfile::NamedTempFile;

impl Config {
    pub fn read() -> Result<Self> {
        let file = File::open(SUSFS_CONFIG)?;
        let mut reader = BufReader::new(file);

        // 1. Parse the version
        let probe: VersionProbe = serde_json::from_reader(&mut reader)?;

        // 2. Check the version
        if probe.version != CURRENT_VERSION {
            bail!("Incompatible SUSFS config version: {}", probe.version);
        }

        // 3. Extract and rewind the file
        let mut file = reader.into_inner();
        file.seek(SeekFrom::Start(0))?;

        // 4. Rewrap BufReader and parse entire structure
        let reader = BufReader::new(file);
        let config: Self = serde_json::from_reader(reader)?;

        Ok(config)
    }

    pub fn read_or_default() -> Self {
        Self::read().unwrap_or_default()
    }

    pub fn save(&self) -> Result<()> {
        let target_path = Path::new(SUSFS_CONFIG);

        // 1. Ensure the directory exists
        if let Some(parent) = target_path.parent() {
            create_dir_all(parent)?;
        }

        // 2. Create a NamedTempFile in the same directory as the target file
        let target_dir = target_path.parent().unwrap_or_else(|| Path::new("."));
        let mut temp_file = NamedTempFile::new_in(target_dir)?;

        // 3. Serialize the config into the temp file
        serde_json::to_writer_pretty(&mut temp_file, self)?;
        temp_file.flush()?;

        // 4. Set permissions
        set_permissions(temp_file.path(), Permissions::from_mode(0o600))?;

        // 5. Sync to disk
        temp_file.as_file().sync_all()?;

        // 6. Persist & replace original file
        temp_file.persist(target_path)?;

        Ok(())
    }
}

impl Drop for Config {
    fn drop(&mut self) {
        log::info!("Saving SUSFS config...");
        self.save()
            .unwrap_or_else(|e| log::error!("Failed to save SUSFS config: {e}"));
    }
}
