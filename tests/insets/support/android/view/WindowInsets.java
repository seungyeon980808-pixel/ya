package android.view;
import android.graphics.Insets;
import java.util.HashMap;
import java.util.Map;
/** Minimal type-mask and builder semantics, not a renderer or a replacement helper. */
public final class WindowInsets {
    private final Map<Integer,Insets> visible = new HashMap<>(), ignoring = new HashMap<>();
    public Object cutout;
    public static final class Type {
        public static int systemBars() { return 7; }
        public static int displayCutout() { return 8; }
        public static int ime() { return 16; }
    }
    public Insets getInsets(int mask) { return combined(visible, mask); }
    public Insets getInsetsIgnoringVisibility(int mask) { return combined(ignoring, mask); }
    private static Insets combined(Map<Integer,Insets> map, int mask) {
        int l=0,t=0,r=0,b=0;
        for (Map.Entry<Integer,Insets> e:map.entrySet()) if ((e.getKey() & mask)!=0) {
            Insets i=e.getValue(); l=Math.max(l,i.left); t=Math.max(t,i.top);
            r=Math.max(r,i.right); b=Math.max(b,i.bottom);
        }
        return Insets.of(l,t,r,b);
    }
    public static final class Builder {
        private final WindowInsets value=new WindowInsets();
        public Builder() {}
        public Builder(WindowInsets source) {
            value.visible.putAll(source.visible); value.ignoring.putAll(source.ignoring); value.cutout=source.cutout;
        }
        private void put(Map<Integer,Insets> map,int mask,Insets insets) {
            for(int bit=1;bit<=16;bit<<=1) if ((mask&bit)!=0) map.put(bit,insets);
        }
        public Builder setInsets(int mask,Insets i) { put(value.visible,mask,i); return this; }
        public Builder setInsetsIgnoringVisibility(int mask,Insets i) { put(value.ignoring,mask,i); return this; }
        public Builder setDisplayCutout(Object cutout) { value.cutout=cutout; return this; }
        public WindowInsets build() { return value; }
    }
}
