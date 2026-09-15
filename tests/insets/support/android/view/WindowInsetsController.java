package android.view;
public class WindowInsetsController {
    public static final int APPEARANCE_LIGHT_STATUS_BARS=8, APPEARANCE_LIGHT_NAVIGATION_BARS=16;
    public int appearance,mask;
    public void setSystemBarsAppearance(int a,int m) { appearance=a; mask=m; }
}
