# Persistent PTT host lifecycle tests

Run `PTT_TOOLCHAIN_HOME=/path/to/android-toolchain tests/ptt/run.sh`.

Compiles and executes production `PttService`, `PttReadiness`, `PttStore`, and
`VolumeKeyService`. Android stubs model queued foreground starts and Handler
callbacks separately from service delivery, foreground permission/launch failure,
and named preference stores shared across component recreation. The notification
stop PendingIntent is inspected and delivered to a recreated production service.
The production diagnostic clear is executed, not reimplemented.

`MainActivity` resume/stop bindings and absence of a BootReceiver PTT path are
supplemental source assertions, not activity instrumentation. Audio, media-session
bridge, and Android system scheduling are stubbed; recorder starts are counted and
DB/transcription access fails the test. This suite deliberately does not validate
WAV contents or screen-off physical-key dispatch, process/OS disk persistence,
Android FGS eligibility, notification visibility, OEM reclaim, reboot, or Doze.
Those require device evidence. No always-on guarantee is implied.
