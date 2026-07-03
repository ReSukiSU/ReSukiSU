use num_enum::{TryFromPrimitive};
use serde::{Deserialize, Serialize};

#[derive(Debug, Eq, PartialEq, TryFromPrimitive, Copy, Clone, Serialize, Deserialize)]
#[repr(i32)]
pub enum UidScheme {
    NonApp = 0,
    RootExceptSu = 1,
    NonSu = 2,
    UnmountedApp = 3,
    Unmounted = 4,
}

#[derive(Debug, Serialize, Deserialize)]
pub enum SusKstatType {
    Normal,
    FullClone,
    Statically,
}
