use num_enum::{IntoPrimitive, TryFromPrimitive};
use serde::{Deserialize, Serialize};

#[derive(PartialEq, TryFromPrimitive, IntoPrimitive)]
#[repr(i32)]
pub enum UidScheme {
    NonApp = 0,
    RootExceptSu = 1,
    NonSu = 2,
    UnmountedApp = 3,
    Unmounted = 4,
}

#[derive(Serialize, Deserialize)]
#[repr(u8)]
pub enum SusKstatType {
    Normal = 0,
    FullClone = 1,
    Statically = 2,
}
