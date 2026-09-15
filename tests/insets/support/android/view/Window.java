package android.view;
public class Window {
    public boolean decorFits=true;
    public boolean decorCreated;
    public int statusColor=-1, navigationColor=-1;
    public WindowInsetsController controller=new WindowInsetsController();
    public void setDecorFitsSystemWindows(boolean fits) { decorFits=fits; }
    public void setStatusBarColor(int color) { statusColor=color; }
    public void setNavigationBarColor(int color) { navigationColor=color; }
    public WindowInsetsController getInsetsController() {
        if (!decorCreated) throw new NullPointerException("PhoneWindow decor not created");
        return controller;
    }
}
