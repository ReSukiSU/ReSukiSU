use std::path::Path;

use anyhow::{Context, Result};

use crate::{
    android::{sepolicy, utils::ensure_dir_exists},
    defs,
};

pub fn set_sepolicy(pkg: String, policy: String) -> Result<()> {
    ensure_dir_exists(defs::PROFILE_SELINUX_DIR)?;
    let policy_file = Path::new(defs::PROFILE_SELINUX_DIR).join(pkg);
    std::fs::write(&policy_file, policy)?;
    sepolicy::apply_file(&policy_file)?;
    Ok(())
}

pub fn get_sepolicy(pkg: String) -> Result<()> {
    let policy_file = Path::new(defs::PROFILE_SELINUX_DIR).join(pkg);
    let policy = std::fs::read_to_string(policy_file)?;
    println!("{policy}");
    Ok(())
}

// ksud doesn't guarteen the correctness of template, it just save
pub fn set_template(id: String, template: String) -> Result<()> {
    ensure_dir_exists(defs::PROFILE_TEMPLATE_DIR)?;
    let template_file = Path::new(defs::PROFILE_TEMPLATE_DIR).join(id);
    std::fs::write(template_file, template)?;
    Ok(())
}

pub fn get_template(id: String) -> Result<()> {
    let template_file = Path::new(defs::PROFILE_TEMPLATE_DIR).join(id);
    let template = std::fs::read_to_string(template_file)?;
    println!("{template}");
    Ok(())
}

pub fn delete_template(id: String) -> Result<()> {
    let template_file = Path::new(defs::PROFILE_TEMPLATE_DIR).join(id);
    std::fs::remove_file(template_file)?;
    Ok(())
}

fn parse_template_name(template: &str, locale: Option<&str>) -> Result<String> {
    // Older manager versions accidentally appended a single quote when saving
    // templates. Keep those existing templates readable while new writes are
    // fixed on the manager side.
    let template = template.trim();
    let template = template.strip_suffix('\'').unwrap_or(template);
    let template: serde_json::Value = serde_json::from_str(template)?;
    let fallback = template
        .get("name")
        .and_then(serde_json::Value::as_str)
        .context("template name is missing or is not a string")?;

    let Some(locale) = locale.filter(|locale| !locale.is_empty()) else {
        return Ok(fallback.to_owned());
    };
    let language = locale.split('_').next().unwrap_or(locale);
    let localized_name = template
        .get("locales")
        .and_then(serde_json::Value::as_object)
        .and_then(|locales| locales.get(locale).or_else(|| locales.get(language)))
        .and_then(|localized| localized.get("name"))
        .and_then(serde_json::Value::as_str);

    Ok(localized_name.unwrap_or(fallback).to_owned())
}

#[cfg(test)]
mod tests {
    use super::parse_template_name;

    #[test]
    fn parses_template_name() {
        assert_eq!(
            parse_template_name(r#"{"id":"adb","name":"Adb"}"#, None).unwrap(),
            "Adb"
        );
    }

    #[test]
    fn parses_localized_template_name() {
        let template = r#"{
            "id":"adb",
            "name":"Adb",
            "locales":{
                "bn":{"name":"এডিবি"},
                "zh_CN":{"name":"Adb 模版"}
            }
        }"#;

        assert_eq!(
            parse_template_name(template, Some("zh_CN")).unwrap(),
            "Adb 模版"
        );
        assert_eq!(
            parse_template_name(template, Some("bn_BD")).unwrap(),
            "এডিবি"
        );
        assert_eq!(parse_template_name(template, Some("ja_JP")).unwrap(), "Adb");
    }

    #[test]
    fn parses_template_name_with_legacy_trailing_quote() {
        assert_eq!(
            parse_template_name(r#"{"id":"adb","name":"Adb"}'"#, None).unwrap(),
            "Adb"
        );
    }
}

pub fn list_templates(name: bool, locale: Option<&str>) -> Result<()> {
    let templates = std::fs::read_dir(defs::PROFILE_TEMPLATE_DIR);
    let Ok(templates) = templates else {
        return Ok(());
    };
    for template in templates {
        let template = template?;
        if name {
            let path = template.path();
            let template = match std::fs::read_to_string(&path)
                .with_context(|| format!("failed to read {}", path.display()))
                .and_then(|template| parse_template_name(&template, locale))
            {
                Ok(template) => template,
                Err(error) => {
                    log::warn!(
                        "failed to read template name from {}: {error}",
                        path.display()
                    );
                    continue;
                }
            };
            println!("{template}");
        } else if let Some(template) = template.file_name().to_str() {
            println!("{template}");
        }
    }
    Ok(())
}

pub fn apply_sepolies() -> Result<()> {
    let path = Path::new(defs::PROFILE_SELINUX_DIR);
    if !path.exists() {
        log::info!("profile sepolicy dir not exists.");
        return Ok(());
    }

    let sepolicies =
        std::fs::read_dir(path).with_context(|| "profile sepolicy dir open failed.".to_string())?;
    for sepolicy in sepolicies {
        let Ok(sepolicy) = sepolicy else {
            log::info!("profile sepolicy dir read failed.");
            continue;
        };
        let sepolicy = sepolicy.path();
        if sepolicy::apply_file(&sepolicy).is_ok() {
            log::info!("profile sepolicy applied: {}", sepolicy.display());
        } else {
            log::info!("profile sepolicy apply failed: {}", sepolicy.display());
        }
    }
    Ok(())
}
