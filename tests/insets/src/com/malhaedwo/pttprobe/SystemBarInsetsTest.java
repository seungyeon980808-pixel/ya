package com.malhaedwo.pttprobe;

import android.app.Activity;
import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SystemBarInsetsTest {
    private static int checks;
    private static void check(boolean ok, String label) {
        checks++; if (!ok) throw new AssertionError(label);
    }
    private static void padding(View v,int l,int t,int r,int b,String label) {
        check(v.left==l && v.top==t && v.right==r && v.bottom==b,label);
    }
    private static WindowInsets insets(Insets bars, Insets cutout) {
        int mask=WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout();
        return new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(),bars)
                .setInsets(WindowInsets.Type.displayCutout(),cutout)
                .setInsetsIgnoringVisibility(mask, Insets.of(9,80,11,48))
                .setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,600))
                .setDisplayCutout(new Object()).build();
    }
    public static void main(String[] args) throws Exception {
        Activity a=new Activity(); View scroll=new View(); scroll.setPadding(3,5,7,9);
        SystemBarInsets.setContentView(a,scroll,0xffd8ddda);
        check(!a.window.decorFits,"explicit edge-to-edge");
        check(a.window.statusColor==0 && a.window.navigationColor==0,"transparent bars");
        check(a.window.controller.appearance==24 && a.window.controller.mask==24,"dark bar icons");
        check(a.content instanceof FrameLayout,"non-scrolling wrapper");
        FrameLayout root=(FrameLayout)a.content;
        check(root.child==scroll,"original content preserved");
        check(root.clipChildren && root.clipToPadding,"safe viewport clips scrolled children");
        check(root.background==0xffd8ddda,"system area background");
        check(root.childParams.width==-1 && root.childParams.height==-1,"bounded full viewport");
        check(root.insetListener!=null,"listener installed before attach");
        root.attach(); check(root.requests>0,"insets requested on deferred attach");
        WindowInsets portrait=insets(Insets.of(0,72,0,24),Insets.of(0,80,0,0));
        WindowInsets remainder=root.insetListener.onApplyWindowInsets(root,portrait);
        padding(root,0,80,0,24,"portrait cutout union and gesture bottom");
        root.insetListener.onApplyWindowInsets(root,portrait);
        padding(root,0,80,0,24,"repeat dispatch never accumulates");
        padding(scroll,3,5,7,9,"content padding remains untouched");
        root.insetListener.onApplyWindowInsets(root,insets(Insets.of(0,0,48,0),Insets.of(92,0,0,0)));
        padding(root,92,0,48,0,"landscape left cutout and right navigation");
        root.insetListener.onApplyWindowInsets(root,insets(Insets.of(48,0,0,0),Insets.of(0,0,92,0)));
        padding(root,48,0,92,0,"reverse landscape");
        root.insetListener.onApplyWindowInsets(root,insets(Insets.of(0,72,0,48),Insets.NONE));
        padding(root,0,72,0,48,"three-button bottom and rotation reset");
        root.insetListener.onApplyWindowInsets(root,insets(Insets.NONE,Insets.NONE));
        padding(root,0,0,0,0,"zero insets remove previous padding");
        int handled=WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout();
        Insets rest=remainder.getInsets(handled), hidden=remainder.getInsetsIgnoringVisibility(handled);
        check(rest.left==0 && rest.top==0 && rest.right==0 && rest.bottom==0,"handled visible insets removed");
        check(hidden.left==0 && hidden.top==0 && hidden.right==0 && hidden.bottom==0,"handled stable insets removed");
        check(remainder.cutout==null,"cutout metadata consumed");
        check(remainder.getInsets(WindowInsets.Type.ime()).bottom==600,"IME dispatch preserved");
        check(portrait.getInsets(handled).top==80 && portrait.cutout!=null,"input not mutated");
        Activity b=new Activity(); b.attachOnSet=true; b.window.controller=null;
        SystemBarInsets.setContentView(b,new View(),123);
        check(b.content.requests>0,"already attached root requests insets, null controller safe");
        padding(b.content,0,0,0,0,"new activity has independent inset state");
        // Supplemental wiring guard: behavior above compiles and executes the real helper.
        for(String name:new String[]{"MainActivity","InboxActivity"}) {
            String source=Files.readString(Path.of(args[0],name+".java"));
            check(source.contains("SystemBarInsets.setContentView(this, scroll, BG);"),name+" wired");
            check(!source.contains("setContentView(scroll)"),name+" has no unsafe direct root");
        }
        System.out.println("SystemBarInsetsTest PASS: " + checks + " assertions");
    }
}
