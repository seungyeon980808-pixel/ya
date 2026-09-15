package android.app;
import android.view.View;
import android.view.Window;
public class Activity {
    public final Window window=new Window();
    public View content;
    public boolean attachOnSet;
    public Window getWindow() { return window; }
    public void setContentView(View view) { window.decorCreated=true; content=view; if (attachOnSet) view.attach(); }
}
