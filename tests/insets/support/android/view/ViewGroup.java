package android.view;
public class ViewGroup extends View {
    public boolean clipChildren, clipToPadding;
    public View child;
    public LayoutParams childParams;
    public void setClipChildren(boolean value) { clipChildren=value; }
    public void setClipToPadding(boolean value) { clipToPadding=value; }
    public void addView(View view, LayoutParams params) { child=view; childParams=params; }
    public static class LayoutParams {
        public static final int MATCH_PARENT=-1;
        public final int width,height;
        public LayoutParams(int w,int h) { width=w; height=h; }
    }
}
