package android.view;
import java.util.ArrayList;
import java.util.List;
public class View {
    public int left, top, right, bottom, background, requests;
    public boolean attached;
    public OnApplyWindowInsetsListener insetListener;
    public final List<OnAttachStateChangeListener> attachListeners = new ArrayList<>();
    public interface OnApplyWindowInsetsListener { WindowInsets onApplyWindowInsets(View v, WindowInsets i); }
    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View v);
        void onViewDetachedFromWindow(View v);
    }
    public void setPadding(int l,int t,int r,int b) { left=l; top=t; right=r; bottom=b; }
    public void setBackgroundColor(int color) { background=color; }
    public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener l) { insetListener=l; }
    public void addOnAttachStateChangeListener(OnAttachStateChangeListener l) { attachListeners.add(l); }
    public void requestApplyInsets() { requests++; }
    public boolean isAttachedToWindow() { return attached; }
    public void attach() { attached=true; for (OnAttachStateChangeListener l:attachListeners) l.onViewAttachedToWindow(this); }
}
