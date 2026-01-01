use daemonize::User;

pub fn start_sulog() {
    daemonize::Daemonize::new().execute();
    signal_hook::flag::register(signal_hook::consts::SIGUSR2, flag)
}
