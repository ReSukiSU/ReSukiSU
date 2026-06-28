pub macro impl_hashset_indexkey($struct_name:ident, $field:ident) {
    impl std::hash::Hash for $struct_name {
        fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
            self.$field.hash(state);
        }
    }
    impl PartialEq for $struct_name {
        fn eq(&self, other: &Self) -> bool {
            self.$field == other.$field
        }
    }
    impl Eq for $struct_name {}
    impl std::borrow::Borrow<String> for $struct_name {
        fn borrow(&self) -> &String {
            &self.$field
        }
    }
    impl std::borrow::Borrow<str> for $struct_name {
        fn borrow(&self) -> &str {
            &self.$field.as_str()
        }
    }
}

pub macro ensure_path_exists {
    ($path:expr $(,)?) => {{
        let p = std::path::Path::new($path);
        if !p.exists() {
            anyhow::bail!("Path does not exist: {}", p.display());
        }
    }},
    ($path:expr, $msg:expr $(,)?) => {{
        let p = std::path::Path::new($path);
        if !p.exists() {
            anyhow::bail!("{}: {}", $msg, p.display());
        }
    }},
}
