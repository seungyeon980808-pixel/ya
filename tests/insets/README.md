# System bar viewport regression

Run `bash tests/insets/run.sh` (supports `JAVA_HOME` / `PTT_TOOLCHAIN_HOME`).
The runner compiles the actual production `SystemBarInsets.java`, not a copied
inset algorithm. Small Android API stubs record window configuration, hierarchy,
clipping flags, inset callback results and attach-time requests. Tests cover
portrait, both landscape edges, gesture / three-button bottom values, cutouts,
repeat dispatch, zero-inset reset, independent instances and retained IME insets.
Source guards additionally require both activities to use the tested helper.
The Window stub now throws before decor creation, reproducing the Galaxy S25
PhoneWindow startup failure: controller lookup must follow setContentView.
This regression was observed failing before the ordering fix and passing after it.

These are host contract tests, not Android rendering or instrumentation tests.
They cannot prove actual platform dispatch, pixel clipping, Samsung bar contrast,
rotation lifecycle, keyboard behavior, or touch access. Device checks must cover
both screens at top and scrolled to bottom in portrait and landscape, including
navigation modes. No device connection or installation is performed by this test.
